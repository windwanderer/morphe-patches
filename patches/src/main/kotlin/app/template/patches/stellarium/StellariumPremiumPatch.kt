package app.template.patches.stellarium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_Stellarium
import app.template.patches.shared.Elf64
import java.security.MessageDigest
import java.util.logging.Logger

/*
 * Stellarium keeps the entire Plus feature set in
 * lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so, which is shipped
 * unstripped, so the license gate can be found by symbol name instead of a
 * per-release address. Four functions decide what the app believes it is
 * licensed for:
 *
 * - StelAPI::getHasFeaturePLUS() const is the gate every Plus feature (extended
 *   catalogs, deep sky surveys, ...) is checked against. It returns
 *   QVariant(false) once the Play licensing callback reports a definite
 *   failure, so it is rewritten to return QVariant(true) unconditionally.
 * - StelAPI::hasValidLicense() const answers the same question for the parts of
 *   the app that ask separately, and gets the same treatment.
 * - StelAPI::getAppVariant() const reports which variant the install runs as,
 *   "plus" or "free", read from the variant flag of the loaded bundle. The flag
 *   is forced to "plus".
 * - StelAPI::getLicenceStatus() const reports the cached license status, which a
 *   patched install never sets to "licensed", so it is rewritten to report
 *   LICENSED.
 *
 * No bytecode patch is needed: the licensing callback in Java only feeds these
 * native checks, which no longer care what it reports.
 *
 * The unlock also makes the app ask Play Asset Delivery for the "extended" data
 * pack, the deep catalogs that only a Play install can receive. A sideloaded
 * install never gets an answer, and the failure is shown in the app's data pack
 * error dialog ("Cannot get data pack state"). The patch skips that request as
 * well, because such an install can never receive the pack anyway.
 *
 * The patch is written to survive releases it was not built for. Instead of
 * asserting the bytes at each site, it verifies what the functions do:
 *
 * - the two checks that answer with a QVariant have to build one, by branching
 *   into a QVariant constructor. That call is also what proves that replacing
 *   their entry with a QVariant construction is an ABI compatible change,
 * - the variant flag and the license status are found by their shape in the
 *   function, so register allocation and instruction order may change,
 * - the request for the extended data pack is followed from the call
 *   `StelAPI::addExtendedSources()` makes, and recognized by the pack name its
 *   function uses and the Play Asset Delivery calls it reaches.
 *
 * The four license checks are patched together: the app reads all of them to
 * decide what it reports and which features it enables, so leaving one alone
 * would let a patch that reports success still run as the free version. A
 * release that reshapes one of them fails the patch, with a message naming the
 * function that changed. Only the request for the data pack is optional:
 * skipping it leaves the app working, at worst with the data pack dialog back.
 * Nothing is ever patched blindly: a library whose checks no longer match fails
 * instead.
 */

/** A native function the patch rewrites, by mangled name and by plain C++ name. */
private class NativeFunction(val symbol: String, val name: String)

/** `StelAPI::getHasFeaturePLUS() const`, the gate every Plus feature is checked against. */
private val HAS_FEATURE_PLUS = NativeFunction("_ZNK7StelAPI17getHasFeaturePLUSEv", "getHasFeaturePLUS")

/** `StelAPI::hasValidLicense() const`. */
private val HAS_VALID_LICENSE = NativeFunction("_ZNK7StelAPI15hasValidLicenseEv", "hasValidLicense")

/** `StelAPI::getAppVariant() const`. */
private val APP_VARIANT = NativeFunction("_ZNK7StelAPI13getAppVariantEv", "getAppVariant")

/** `StelAPI::getLicenceStatus() const`. */
private val LICENCE_STATUS = NativeFunction("_ZNK7StelAPI16getLicenceStatusEv", "getLicenceStatus")

/** `StelAPI::addExtendedSources()`, which hands the extended data pack to the engine. */
private val ADD_EXTENDED_SOURCES = NativeFunction("_ZN7StelAPI18addExtendedSourcesEv", "addExtendedSources")

/** `QVariant::QVariant(bool)`, how the original code reports a successful check. */
private const val SYMBOL_QVARIANT_BOOL = "_ZN8QVariantC1Eb"

/** Every `QVariant::QVariant(...)` constructor is mangled under this prefix. */
private const val SYMBOL_QVARIANT_CONSTRUCTOR = "_ZN8QVariantC1"

/** The Play asset pack holding the extended catalogs, as the native code names it. */
private const val ASSET_PACK = "asset_pack_extended"

/** Imported functions of the Play Asset Delivery SDK, all under this prefix. */
private const val PLAY_ASSET_DELIVERY = "AssetPackManager_"

/** How far into the function that registers the pack the pack name and the Play calls sit. */
private const val PACK_FUNCTION_SIZE = 0x400

/**
 * The library holding the license gate. It sits in the arm64 split of the XAPK,
 * so it is found by the tail of the entry name it is listed under, which is the
 * same whether the split appears in the name or not.
 */
private val PLUS_LIBRARY = Regex("""lib/arm64-v8a/libstellarium-mobile-plus(_arm64-v8a)?\.so$""")

private val logger = Logger.getLogger("StellariumPremium")

// Sizes of the sequences the patch writes, in bytes.
private const val LICENSE_RESULT_SIZE = 12
private const val VARIANT_FLAG_SIZE = 4
private const val LICENCE_STATUS_SIZE = 8

// AArch64 instructions used to rewrite the license checks. They are written as
// hex words so they read like the disassembly they came from.
private val MOV_X0_X8 = 0xAA0803E0.toInt() // mov x0, x8: the return slot the caller passed.
private val MOV_W1_1 = 0x52800021.toInt() // mov w1, #1: the bool to build a QVariant from.
private val MOV_W0_1 = 0x52800020.toInt() // mov w0, #1: LICENSED in StelAPI's status enum.
private val RET = 0xD65F03C0.toInt() // ret

// Fields of the instructions the shape of the license code is read through.
private val LOAD_STORE_OPCODE_MASK = 0xFFC00000.toInt()
private val LOAD_BYTE = 0x39400000.toInt() // ldrb w?, [x?, #imm]
private val LOAD_WORD = 0xB9400000.toInt() // ldr w?, [x?, #imm]
private val LOAD_DOUBLEWORD = 0xF9400000.toInt() // ldr x?, [x?, #imm]
private const val IMMEDIATE_12_SHIFT = 10
private const val IMMEDIATE_12_MASK = 0xFFF
private const val REGISTER_MASK = 0x1F

// The receiver x0 that C++ member functions are called with, and the register
// holding the return value of a function that returns an `int`.
private const val RECEIVER = 0
private const val RETURN_WORD = 0

/**
 * `[x0, #0x10]` holds the bundle `getAppVariant()` reads its variant from, and
 * the flag is the byte at `0x58` of it. The offset is stored as an immediate of
 * 2, because 64 bit loads are scaled by 8.
 */
private const val BUNDLE_POINTER = 2
private const val VARIANT_FLAG = 0x58

@Suppress("unused")
val premiumPatch = rawResourcePatch(
    name = "Stellarium Premium",
    description = "Unlock Stellarium Plus by patching the native license checks, and skip the " +
        "Play data pack request a sideloaded install cannot satisfy (arm64-v8a)."
) {
    compatibleWith(COMPATIBILITY_Stellarium)
    execute {
        val libraryEntry = listApkEntries("").firstOrNull { PLUS_LIBRARY.containsMatchIn(it) }
            ?: throw PatchException(
                "The APK has no lib/arm64-v8a/libstellarium-mobile-plus*.so. " +
                    "The patch needs the arm64-v8a build that 64 bit devices install, " +
                    "such as the 1.16.8 XAPK, and patches the merged APK."
            )
        val library = get(libraryEntry)
        val bytes = library.readBytes()
        // The entry and the hash of the input go to the log, so a run that did not
        // end up unlocking the app can be told apart from one that never patched
        // the library the install uses.
        logger.info("Patching $libraryEntry (${bytes.size} bytes, sha-256 ${digestOf(bytes)})")

        val elf = Elf64(bytes)

        // Both checks that answer with a QVariant build the QVariant they return
        // by calling its constructor, with the return slot the caller passes in
        // x8. That is what makes replacing their entry with a QVariant
        // construction of the patch's own a change the caller cannot notice, so
        // the patch verifies the call instead of the bytes around it.
        val variantConstructors = elf.pltStubs
            .filterKeys { it.startsWith(SYMBOL_QVARIANT_CONSTRUCTOR) }
            .values.toSet()
        val variantConstructor = elf.pltStubs[SYMBOL_QVARIANT_BOOL]
            ?: throw PatchException(
                "The native library does not import QVariant(bool) (\"$SYMBOL_QVARIANT_BOOL\"), " +
                    "so the license checks cannot be answered with QVariant(true)"
            )

        val applied = ArrayList<String>()
        val skipped = ArrayList<String>()

        fun apply(label: String, required: Boolean, edit: () -> NativeEdit) {
            try {
                val done = edit()
                val where = "$label @0x${done.offset.toString(16)}"
                applied += if (done.applyTo(bytes)) where else "$where (already patched)"
            } catch (error: PatchException) {
                if (required) throw error
                logger.warning("$label was left alone: ${error.message}")
                skipped += label
            }
        }

        // All four checks are patched: the app reads the gate, the answer it
        // reports for the license, and the variant, so one of them left alone
        // would let a "successful" patch still run as the free version. A
        // release that reshapes one of them fails here instead, naming it.
        apply("getHasFeaturePLUS()", required = true) {
            licenseResultEdit("getHasFeaturePLUS()", elf, HAS_FEATURE_PLUS, variantConstructors, variantConstructor)
        }
        apply("hasValidLicense()", required = true) {
            licenseResultEdit("hasValidLicense()", elf, HAS_VALID_LICENSE, variantConstructors, variantConstructor)
        }
        apply("getAppVariant()", required = true) { appVariantEdit(elf) }
        apply("getLicenceStatus()", required = true) { licenceStatusEdit(elf) }

        // The extended data pack only ever reaches a Play install, so asking for
        // it can only fail. Leaving the request out keeps the app from reporting
        // that failure, and leaving the code in place keeps the patch working
        // when a release renames or moves it.
        apply("extended data pack", required = false) { extendedDataPackEdit(elf) }

        library.writeBytes(bytes)

        logger.info("Patched the native library: ${applied.joinToString(", ")}")
        if (skipped.isNotEmpty()) {
            logger.warning("These were not patched: ${skipped.joinToString(", ")}")
        }
    }
}

/**
 * Rewrites a function that answers with a `QVariant(bool)` so that it always
 * answers `true`, by constructing the `QVariant` itself:
 *
 * ```asm
 * mov x0, x8          ; the return slot the caller passed in x8
 * mov w1, #1          ; true
 * b   QVariant(bool)  ; construct and return to the caller
 * ```
 *
 * @throws PatchException If the function no longer builds a `QVariant`, which
 *   means the release moved or changed the license check.
 */
private fun licenseResultEdit(
    label: String,
    elf: Elf64,
    function: NativeFunction,
    variantConstructors: Set<Long>,
    variantConstructor: Long,
): NativeEdit {
    val symbol = elf.function(function)
    requireFunction(symbol, label, LICENSE_RESULT_SIZE)

    if (!elf.branchesTo(symbol, variantConstructors)) {
        throw PatchException(
            "$label does not build a QVariant anymore, so it is no longer the license check " +
                "this patch replaces"
        )
    }

    return NativeEdit(
        label = label,
        offset = elf.codeOffsetOf(symbol.address),
        replacement = instruction(MOV_X0_X8) +
            instruction(MOV_W1_1) +
            branch(symbol.address + 8, variantConstructor),
    )
}

/**
 * Forces the variant flag of `getAppVariant()` to "plus":
 *
 * ```asm
 * ldr  x9, [x0, #0x10]   ; the loaded bundle
 * ldrb w9, [x9, #0x58]   ; its variant flag, replaced by `mov w9, #1`
 * ```
 *
 * The two loads are found by their shape anywhere in the function, so a release
 * may move the prologue around them, allocate other registers, or compile the
 * two paths of the check in the opposite order: the function reads the flag as
 * set and reports "plus" in every case.
 *
 * The only register whose contents change is the one the flag was read into, so
 * the rest of the function keeps working as it is.
 *
 * @throws PatchException If the function no longer reads a variant flag.
 */
private fun appVariantEdit(elf: Elf64): NativeEdit {
    val label = "getAppVariant()"
    val symbol = elf.function(APP_VARIANT)
    requireFunction(symbol, label, VARIANT_FLAG_SIZE)

    val words = elf.instructions(symbol)
    for (index in 0 until words.size - 1) {
        val bundle = loadBundle(words[index]) ?: continue

        val read = loadVariantFlag(words[index + 1], bundle)
        if (read == null && !isForcedFlag(words[index + 1])) {
            // Not the code that reads the variant flag, unless it is the patch's
            // own work from an earlier run.
            continue
        }

        val flag = read ?: (words[index + 1] and REGISTER_MASK)
        return NativeEdit(
            label = label,
            offset = elf.codeOffsetOf(symbol.address + (index + 1) * 4L),
            replacement = instruction(moveOne(flag)),
        )
    }

    throw PatchException(
        "$label no longer reads a variant flag from the loaded bundle, and the app reads that " +
            "flag to decide whether it reports the free or the plus variant. If this APK was " +
            "already patched by an older version of this patch, patch its original copy again."
    )
}

/**
 * Rewrites `getLicenceStatus()` from `ldr w0, [x0, #0x18] ; ret` to
 * `mov w0, #1 ; ret`, so the status reads as LICENSED instead of FAILED.
 *
 * @throws PatchException If the function is not a plain getter of a 32 bit
 *   status anymore, because a computed status may hide a failure the app
 *   handles, which the patch should not force away.
 */
private fun licenceStatusEdit(elf: Elf64): NativeEdit {
    val label = "getLicenceStatus()"
    val symbol = elf.function(LICENCE_STATUS)
    requireFunction(symbol, label, LICENCE_STATUS_SIZE)

    val words = elf.instructions(symbol, maxWords = 4)
    val loadsStatus = words.size >= 2 && words[1] == RET &&
        // The patch's own work from an earlier run reads as the same getter.
        (words[0] == MOV_W0_1 || loadStatusWord(words[0]) == RETURN_WORD)

    if (!loadsStatus) {
        throw PatchException("$label is not a plain getter of a 32 bit status anymore")
    }

    return NativeEdit(
        label = label,
        offset = elf.codeOffsetOf(symbol.address),
        replacement = instruction(MOV_W0_1) + instruction(RET),
    )
}

/**
 * Stops the app from asking Play Asset Delivery for the extended data pack.
 *
 * With the Plus unlock in place, `StelAPI::addExtendedSources()` registers the
 * `asset_pack_extended` pack with the engine, which then polls Play for a
 * download state that a sideloaded install can never reach. Every poll fails,
 * and the app shows the failure in its data pack error dialog. The patch makes
 * the function that registration happens in return immediately:
 *
 * ```asm
 * sub sp, sp, #0x60    ; the call addExtendedSources() makes to register the
 * ret                  ; pack, replaced by a plain return
 * ```
 *
 * Nothing exports that function, so it is found by following that call: it is
 * the only one of the calls `addExtendedSources()` makes that both names the
 * pack and reaches Play Asset Delivery. Both places that add the extended
 * sources (the StelAPI constructor and the Plus flag handler) go through it, so
 * the pack is never registered and no request is ever made. The pack itself is
 * only deliverable to a Play install, so nothing that a patched install could
 * have used is lost.
 *
 * @throws PatchException If the library no longer has that call, which means the
 *   release moved the pack registration somewhere the patch cannot find it.
 */
private fun extendedDataPackEdit(elf: Elf64): NativeEdit {
    val label = "extended data pack"
    val sources = elf.function(ADD_EXTENDED_SOURCES)
    requireFunction(sources, label, 4)

    val pack = elf.stringAddressOrNull(ASSET_PACK)
        ?: throw PatchException("$label: the library does not name its \"$ASSET_PACK\" pack anymore")

    val playCalls = elf.pltStubs
        .filterKeys { it.startsWith(PLAY_ASSET_DELIVERY) }
        .values.toSet()
    if (playCalls.isEmpty()) {
        throw PatchException("$label: the library does not import Play Asset Delivery anymore")
    }

    // A call to an imported function goes through its PLT stub, which is not
    // what the pack registration is being searched for.
    val imports = elf.pltStubs.values.toSet()
    val registrations = elf.calledFunctions(sources)
        .filter { it !in imports && elf.isCode(it) }
        .filter { target ->
            elf.codeReferences(target, pack, PACK_FUNCTION_SIZE) &&
                elf.calledFunctions(target, PACK_FUNCTION_SIZE).any { it in playCalls }
        }
        .distinct()

    if (registrations.size != 1) {
        throw PatchException(
            "$label: expected one call that registers the pack with Play Asset Delivery, " +
                "found ${registrations.size}"
        )
    }

    return NativeEdit(
        label = label,
        offset = elf.codeOffsetOf(registrations.single()),
        replacement = instruction(RET),
    )
}

/**
 * The function [function] names: the exact mangled symbol, or the only symbol
 * whose name contains it, which finds it again when a release changed a
 * signature around it but kept the API name.
 */
private fun Elf64.function(function: NativeFunction): Elf64.Symbol {
    symbolOrNull(function.symbol)?.let { return it }

    val candidates = symbolsContaining(function.name)
    if (candidates.size == 1) return candidates.single()
    throw PatchException(
        if (candidates.isEmpty()) "The native library has no \"${function.name}\" function"
        else "The native library has ${candidates.size} functions matching \"${function.name}\""
    )
}

/** Rejects a symbol that is not code, or too small to hold [size] bytes of replacement. */
private fun requireFunction(symbol: Elf64.Symbol, label: String, size: Int) {
    if (!symbol.isFunction) {
        throw PatchException("$label is not a function (ELF symbol type ${symbol.type})")
    }
    if (symbol.size != 0L && symbol.size < size) {
        throw PatchException("$label is only ${symbol.size} bytes long, too small to hold the patch")
    }
}

/** `ldr x?, [x0, #0x10]`, the loaded bundle, or null when [word] is something else. */
private fun loadBundle(word: Int): Int? {
    if ((word and LOAD_STORE_OPCODE_MASK) != LOAD_DOUBLEWORD) return null
    if (((word ushr 5) and REGISTER_MASK) != RECEIVER) return null
    if (((word ushr IMMEDIATE_12_SHIFT) and IMMEDIATE_12_MASK) != BUNDLE_POINTER) return null
    return word and REGISTER_MASK
}

/** `ldrb w?, [x?, #0x58]`, the variant flag of [bundle], or null when [word] is something else. */
private fun loadVariantFlag(word: Int, bundle: Int): Int? {
    if ((word and LOAD_STORE_OPCODE_MASK) != LOAD_BYTE) return null
    if (((word ushr 5) and REGISTER_MASK) != bundle) return null
    if (((word ushr IMMEDIATE_12_SHIFT) and IMMEDIATE_12_MASK) != VARIANT_FLAG) return null
    return word and REGISTER_MASK
}

/** `ldr w?, [x0, #?]`, the status word of the receiver, or null when [word] is something else. */
private fun loadStatusWord(word: Int): Int? {
    if ((word and LOAD_STORE_OPCODE_MASK) != LOAD_WORD) return null
    if (((word ushr 5) and REGISTER_MASK) != RECEIVER) return null
    return word and REGISTER_MASK
}

/** `mov w?, #1`, the variant flag forced to "plus", with the register left out. */
private fun isForcedFlag(word: Int) = (word and 0xFFFFFFE0.toInt()) == MOV_W0_1

/** `mov w<register>, #1`. */
private fun moveOne(register: Int): Int = MOV_W0_1 or register

/**
 * One in place edit of a native library.
 *
 * A library that already holds the replacement is left as it is, which makes the
 * patch idempotent.
 */
private class NativeEdit(
    val label: String,
    val offset: Int,
    private val replacement: ByteArray,
) {
    /** Writes the replacement, and answers whether anything was written. */
    fun applyTo(library: ByteArray): Boolean {
        if (offset < 0 || offset > library.size - replacement.size) {
            throw PatchException(
                "$label is outside the native library (offset 0x${offset.toString(16)}, " +
                    "library size ${library.size} bytes)"
            )
        }

        // Already patched, which happens when the patch runs twice.
        if (library.copyOfRange(offset, offset + replacement.size).contentEquals(replacement)) return false

        replacement.copyInto(library, offset)
        return true
    }
}

/** The first bytes of the SHA-256 of [bytes], to identify a library in a log. */
private fun digestOf(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .take(8)
        .joinToString("") { "%02x".format(it) }

/** Encodes one AArch64 instruction as 4 little endian bytes. */
private fun instruction(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte(),
)

/** Encodes `b <to>` for an instruction placed at address [from]. */
private fun branch(from: Long, to: Long): ByteArray {
    val immediate = (to - from) shr 2
    if (immediate < -(1L shl 25) || immediate >= (1L shl 25)) {
        throw PatchException("Cannot branch from 0x${from.toString(16)} to 0x${to.toString(16)}, it is too far away")
    }

    return instruction(0x14000000 or (immediate.toInt() and 0x03FFFFFF))
}
