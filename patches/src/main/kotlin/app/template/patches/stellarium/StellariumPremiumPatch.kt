package app.template.patches.stellarium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_Stellarium
import app.template.patches.shared.Elf64

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
 *   the app that ask separately and gets the same treatment.
 * - StelAPI::getAppVariant() const reports which variant the install runs as,
 *   "plus" or "free", read from the variant flag of the loaded bundle. The check
 *   is removed so it always reports "plus".
 * - StelAPI::getLicenceStatus() const reports the cached license status, which a
 *   patched install never sets to "licensed", so it is rewritten to report
 *   LICENSED.
 *
 * No bytecode patch is needed: the licensing callback in Java only feeds these
 * native checks, which no longer care what it reports.
 */

/** `StelAPI::getHasFeaturePLUS() const`, the main Plus gate. */
private const val SYMBOL_HAS_FEATURE_PLUS = "_ZNK7StelAPI17getHasFeaturePLUSEv"

/** `StelAPI::hasValidLicense() const`. */
private const val SYMBOL_HAS_VALID_LICENSE = "_ZNK7StelAPI15hasValidLicenseEv"

/** `StelAPI::getAppVariant() const`. */
private const val SYMBOL_APP_VARIANT = "_ZNK7StelAPI13getAppVariantEv"

/** `StelAPI::getLicenceStatus() const`. */
private const val SYMBOL_LICENCE_STATUS = "_ZNK7StelAPI16getLicenceStatusEv"

/** `QVariant::QVariant(bool)`, how the original code reports a successful check. */
private const val SYMBOL_QVARIANT_BOOL = "_ZN8QVariantC1Eb"

/** The library holding the license gate, as it is named inside the APK. */
private val PLUS_LIBRARY = Regex("""lib/arm64-v8a/libstellarium-mobile-plus(_arm64-v8a)?\.so""")

/** Size of the instruction sequence that is replaced in `getAppVariant()`. */
private const val APP_VARIANT_CHECK_SIZE = 12

/** Offset of the variant flag check inside `getAppVariant()`. */
private const val APP_VARIANT_CHECK_OFFSET = 0x20

// AArch64 instructions used to rewrite the license checks. They are written as
// hex words so they read like the disassembly they came from.
private val MOV_X0_X8 = 0xAA0803E0.toInt() // mov x0, x8: the return slot the caller passed.
private val MOV_W1_1 = 0x52800021.toInt() // mov w1, #1: the bool to build a QVariant from.
private val MOV_W0_1 = 0x52800020.toInt() // mov w0, #1: LICENSED in StelAPI's status enum.
private val NOP = 0xD503201F.toInt() // nop
private val RET = 0xD65F03C0.toInt() // ret

@Suppress("unused")
val premiumPatch = rawResourcePatch(
    name = "Stellarium Premium",
    description = "Unlock Stellarium Plus by patching the native license checks (arm64-v8a)."
) {
    compatibleWith(COMPATIBILITY_Stellarium)
    execute {
        val libraryEntry = listApkEntries("lib/arm64-v8a/").firstOrNull { PLUS_LIBRARY.matches(it) }
            ?: throw PatchException(
                "The APK has no lib/arm64-v8a/libstellarium-mobile-plus*.so. " +
                    "The patch needs the arm64-v8a build that 64 bit devices install, " +
                    "such as the 1.16.8 XAPK, and patches the merged APK."
            )

        val library = get(libraryEntry)
        val bytes = library.readBytes()
        val elf = Elf64(bytes)

        // Both license checks answer with a QVariant, which is written through the
        // address the caller passes in x8. Tail calling the constructor with that
        // address keeps the caller's return address, so no stack frame is needed.
        val variantConstructor = elf.findPltStub(SYMBOL_QVARIANT_BOOL)

        listOf(
            trueLicenseResult(
                label = "getHasFeaturePLUS()",
                elf = elf,
                symbol = SYMBOL_HAS_FEATURE_PLUS,
                variantConstructor = variantConstructor,
                // sub sp, sp, #0xa0 ; stp x29, x30, [sp, #0x40] ; stp x28, x27, [sp, #0x50]
                expected = "ff 83 02 d1 fd 7b 04 a9 fc 6f 05 a9",
            ),
            trueLicenseResult(
                label = "hasValidLicense()",
                elf = elf,
                symbol = SYMBOL_HAS_VALID_LICENSE,
                variantConstructor = variantConstructor,
                // ldr w9, [x0, #0x18] ; cmp w9, #1 ; b.eq ...
                expected = "09 18 40 b9 3f 05 00 71 e0 01 00 54",
            ),
            appVariant(elf),
            licenceStatus(elf),
        ).forEach { it.applyTo(bytes) }

        library.writeBytes(bytes)
    }
}

/**
 * Rewrites a function that returns a `QVariant(bool)` so that it always returns
 * `true`, by constructing the `QVariant` itself:
 *
 * ```asm
 * mov x0, x8          ; the return slot the caller passed in x8
 * mov w1, #1          ; true
 * b   QVariant(bool)  ; construct and return to the caller
 * ```
 */
private fun trueLicenseResult(
    label: String,
    elf: Elf64,
    symbol: String,
    variantConstructor: Long,
    expected: String,
): NativeEdit {
    val function = elf.symbol(symbol)
    if (function.size > 0 && function.size < 12) {
        throw PatchException("$label is only ${function.size} bytes long, not a function returning a QVariant")
    }

    return NativeEdit(
        label = label,
        offset = elf.offsetOf(function.address),
        expected = hexBytes(expected),
        replacement = instruction(MOV_X0_X8) +
            instruction(MOV_W1_1) +
            branch(function.address + 8, variantConstructor),
    )
}

/**
 * Removes the variant flag check of `getAppVariant()`:
 *
 * ```asm
 * ldr  x9, [x0, #0x10]   ; the loaded bundle
 * ldrb w9, [x9, #0x58]   ; its plus flag
 * cbz  w9, "free"        ; jump over the "plus" name when not set
 * ```
 *
 * With the branch gone the function falls through to "plus" for every install.
 */
private fun appVariant(elf: Elf64): NativeEdit {
    val function = elf.symbol(SYMBOL_APP_VARIANT)
    if (function.size > 0 && function.size < APP_VARIANT_CHECK_OFFSET + APP_VARIANT_CHECK_SIZE) {
        throw PatchException(
            "getAppVariant() is only ${function.size} bytes long and holds no variant check " +
                "at offset 0x${APP_VARIANT_CHECK_OFFSET.toString(16)}"
        )
    }

    return NativeEdit(
        label = "getAppVariant()",
        offset = elf.offsetOf(function.address) + APP_VARIANT_CHECK_OFFSET,
        expected = hexBytes("09 08 40 f9 29 61 41 39 89 00 00 34"),
        replacement = instruction(NOP) + instruction(NOP) + instruction(NOP),
    )
}

/**
 * Rewrites `getLicenceStatus()` from `ldr w0, [x0, #0x18] ; ret` to `mov w0, #1 ; ret`,
 * so the status reads as `LICENSED` instead of `FAILED`.
 */
private fun licenceStatus(elf: Elf64): NativeEdit = NativeEdit(
    label = "getLicenceStatus()",
    offset = elf.offsetOf(elf.symbol(SYMBOL_LICENCE_STATUS).address),
    expected = hexBytes("00 18 40 b9 c0 03 5f d6"),
    replacement = instruction(MOV_W0_1) + instruction(RET)
)

/**
 * One in place edit of a native library.
 *
 * The bytes that are about to be replaced are checked first, so a library the
 * patch was not built for is never written to, and a library that already holds
 * the replacement is left as it is, which makes the patch idempotent.
 */
private class NativeEdit(
    private val label: String,
    private val offset: Int,
    private val expected: ByteArray,
    private val replacement: ByteArray,
) {
    fun applyTo(library: ByteArray) {
        if (offset < 0 || offset + replacement.size > library.size) {
            throw PatchException(
                "$label is outside the native library (offset 0x${offset.toString(16)}, " +
                    "library size ${library.size} bytes)"
            )
        }

        val found = library.copyOfRange(offset, offset + replacement.size)
        when {
            // Already patched, which happens when a patch is applied twice.
            found.contentEquals(replacement) -> Unit

            found.contentEquals(expected) -> replacement.copyInto(library, offset)

            else -> throw PatchException(
                "$label does not look like the supported build: expected ${expected.toHex()} " +
                    "at offset 0x${offset.toString(16)}, found ${found.toHex()}"
            )
        }
    }
}

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

/** Parses space separated hex bytes such as `"09 08 40 f9"`. */
private fun hexBytes(hex: String): ByteArray = hex
    .split(' ')
    .filter { it.isNotBlank() }
    .map { it.toInt(16).toByte() }
    .toByteArray()

private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
