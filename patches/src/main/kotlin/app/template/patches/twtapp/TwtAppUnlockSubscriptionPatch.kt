package app.template.patches.twtapp

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.shared.Constants.COMPATIBILITY_TWTApp
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

/*
 * Stargazing Hub (TWT App) is a Flutter app whose premium state is decided by
 * the membership snapshot.
 *
 * The patch is written for the 3.7.0 release. Every hook is found by behavior,
 * not by the obfuscated app class names. A build that does not contain a
 * layer skips it instead of failing the whole patch; if no layer at all can be
 * found the patch fails, so a release it no longer understands is never
 * silently left unpatched.
 */

/** The storage key the app uses for the membership snapshot since 3.7.0. */
private const val MEMBERSHIP_SNAPSHOT_KEY = "mainland_commerce_membership_snapshot_v1"

/** The legacy backends key their entries with a "flutter." prefix. */
private const val PREFIXED_MEMBERSHIP_SNAPSHOT_KEY = "flutter.$MEMBERSHIP_SNAPSHOT_KEY"

/** The channel method the app calls to show the splash page ad. */
private const val SPLASH_AD_CHANNEL_METHOD = "showSplashAd"

private val logger = Logger.getLogger("TwtAppUnlock")

/**
 * A method that matched the shape of one of the hooks, kept as a description
 * so it can be looked up again after other layers made its class mutable.
 */
private class Candidate(
    val classDef: ClassDef,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
)

/**
 * How the backend reaches the SharedPreferences instance of the map readers.
 */
private class PreferencesAccess(
    val fieldName: String? = null,
    val helperName: String? = null,
    val helperParameterType: String? = null,
    val helperParameterIndex: Int = -1,
)

/**
 * The forged membership snapshot. The app validates a read snapshot before it
 * grants premium features ("Invalid membership snapshot", "Invalid membership
 * snapshot timestamp", "Invalid membership entitlement"), so the forged value
 * has to be shaped like a real one: the entitlement is "premium", the
 * membership is paid and active, the timestamps parse as the verification
 * time and a far-future expiry.
 */
private fun membershipSnapshotJson(): String {
    val verifiedAt = System.currentTimeMillis()
    return "{\"productId\":\"premium\",\"premium\":true,\"entitlement\":\"premium\"," +
        "\"isPaid\":true,\"isActive\":true,\"active\":true,\"status\":\"ACTIVE\"," +
        "\"source\":\"web\",\"expiresAt\":4102444800000,\"expiry\":4102444800000," +
        "\"verifiedAt\":$verifiedAt}"
}

@Suppress("unused")
val twtAppUnlockSubscriptionPatch = bytecodePatch(
    name = "Unlock Subscription",
    description = "Unlocks all subscription features in TWT App.",
    default = true
) {
    compatibleWith(COMPATIBILITY_TWTApp)

    execute {
        var hooks = 0

        // ── Layer 1: the membership snapshot in the secure storage ───────
        // The plugin's write primitive stores the value; the app writes its
        // real snapshot, we store the forged one instead.
        runCatching {
            MembershipSnapshotWriteFingerprint.method
        }.getOrNull()?.let { method ->
            val keyRegister = method.parameterStart() + if (method.isStatic()) 0 else 1
            val valueRegister = keyRegister + 1
            method.addInstructions(0, membershipSnapshotWriteHook(keyRegister, valueRegister))
            hooks++
            logger.info("Forged membership snapshot write in ${method.definingClass}->${method.name}")
        }

        // The plugin's read primitive returns the decrypted value; we return
        // the forged snapshot for the snapshot key.
        runCatching {
            MembershipSnapshotReadFingerprint.method
        }.getOrNull()?.let { method ->
            val keyRegister = method.parameterStart() + if (method.isStatic()) 0 else 1
            method.addInstructions(0, membershipSnapshotReadHook(keyRegister))
            hooks++
            logger.info("Forged membership snapshot read in ${method.definingClass}->${method.name}")
        }

        // ── Layer 2: the shared_preferences backends ────────────────────
        // The map readers are seeded with the forged snapshot string, so a
        // backend that hands it out through `readAll()` carries it as well.
        val mapReaders = mutableListOf<Candidate>()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                if (method.implementation == null) return@forEach
                val parameters = method.parameterTypes.map { it.toString() }
                val returnType = method.returnType.toString()
                if ((returnType == MAP || returnType == HASH_MAP) &&
                    parameters.size >= 2 &&
                    method.calls("Landroid/content/SharedPreferences;", "getAll")) {
                    mapReaders += classDef.candidate(method)
                }
            }
        }
        mapReaders.forEach { candidate ->
            if (patchMapReader(candidate)) {
                hooks++
                logger.info("Seeded membership snapshot into ${candidate.classDef.type}->${candidate.name}")
            }
        }

        // ── Layer 3: the secure-storage readAll ─────────────────────────
        // The plugin's `readAll` primitive returns the decrypted map; we
        // inject the forged snapshot into the map before it is returned.
        runCatching {
            MembershipSnapshotReadAllFingerprint.method
        }.getOrNull()?.let { method ->
            if (patchInjectSnapshotIntoMap(method)) {
                hooks++
                logger.info("Injected membership snapshot into ${method.definingClass}->${method.name}")
            }
        }

        // ── Layer 4: the splash page ad on app open ─────────────────────
        // The app calls the plugin with the method name "showSplashAd"; the
        // intercept calls the plugin's result immediately, so the splash
        // page ad never shows.
        runCatching {
            SplashAdPluginFingerprint.method
        }.getOrNull()?.let { method ->
            val smali = splashAdInterceptHook(method)
            if (smali != null) {
                method.addInstructions(0, smali)
                hooks++
                logger.info("Intercepted splash ad in ${method.definingClass}->${method.name}")
            }
        }

        if (hooks == 0) {
            throw PatchException(
                "None of the subscription checks could be found. " +
                    "The app's storage or billing code changed shape."
            )
        }

        logger.info("Unlock Subscription: $hooks hook(s) applied")
    }
}

// ── Layer 1: the membership snapshot forge ─────────────────────────────────

/**
 * Smali that intercepts the secure-storage read: when the value is null,
 * empty, or asks about the membership snapshot, it returns the forged
 * snapshot instead of the stored one, and otherwise falls through to the
 * original method body.
 */
private fun membershipSnapshotReadHook(keyRegister: Int): String {
    val jsonLiteral = membershipSnapshotJson()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """
        if-eqz v$keyRegister, :forged
        const-string v1, "$MEMBERSHIP_SNAPSHOT_KEY"
        invoke-virtual {v$keyRegister, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
        move-result v1
        if-nez v1, :forged
        invoke-virtual {v$keyRegister}, Ljava/lang/String;->isEmpty()Z
        move-result v1
        if-eqz v1, :original
        :forged
        const-string v1, "$jsonLiteral"
        return-object v1
        :original
    """.trimIndent()
}

/**
 * Smali that intercepts the secure-storage write: when the storage key asks
 * for the membership snapshot, the value written is the forged snapshot
 * instead of the app's own, and otherwise the write is passed through.
 */
private fun membershipSnapshotWriteHook(keyRegister: Int, valueRegister: Int): String {
    val jsonLiteral = membershipSnapshotJson()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return """
        if-eqz v$keyRegister, :original
        const-string v2, "$MEMBERSHIP_SNAPSHOT_KEY"
        invoke-virtual {v$keyRegister, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
        move-result v2
        if-eqz v2, :original
        const-string v$valueRegister, "$jsonLiteral"
        :original
    """.trimIndent()
}

// ── Layer 3: inject the snapshot into the secure-storage readAll ───────────

/**
 * Injects the forged snapshot into the map that the secure-storage `readAll`
 * primitive returns, right before it is returned.
 */
private fun BytecodePatchContext.patchInjectSnapshotIntoMap(candidate: Candidate): Boolean {
    val method = mutableMethod(candidate) ?: return false
    val implementation = method.implementation ?: return false
    val instructions = implementation.instructions.toList()
    val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    if (returnIndex < 0) return false

    val mapRegister = (instructions[returnIndex] as? OneRegisterInstruction)?.registerA ?: return false
    val scratch = (0 until method.localRegisterCount()).filter { it != mapRegister }
    if (scratch.size < 2) return false

    val snapshotLiteral = membershipSnapshotJson()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    method.addInstructions(
        returnIndex,
        """
        const-string v${scratch[0]}, "$MEMBERSHIP_SNAPSHOT_KEY"
        const-string v${scratch[1]}, "$snapshotLiteral"
        invoke-interface {v$mapRegister, v${scratch[0]}, v${scratch[1]}}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        """.trimIndent()
    )
    return true
}

// ── Layer 4: the splash page ad intercept ──────────────────────────────────

/**
 * Smali that intercepts the Gromore plugin's `showSplashAd` channel call:
 * when the app calls the plugin with the method name "showSplashAd", the
 * intercept calls `Result.success(null)` right away, so the app proceeds and
 * the splash page ad never shows.
 *
 * The register layout follows the smali convention: for an instance method
 * the parameter registers start after the receiver, the MethodCall is the
 * next one and the Result is the last one.
 */
private fun splashAdInterceptHook(method: Method): String? {
    val methodCallRegister = method.parameterStart() + if (method.isStatic()) 0 else 1
    val resultRegister = method.parameterStart() + if (method.isStatic()) 1 else 2

    // Behavioral check: the handler must read the MethodCall's method-name
    // field and call the Result's `success`, otherwise there is nothing to
    // intercept — e.g. the method might have been renamed or restructured.
    val instructions = method.implementation?.instructions ?: return null
    var readsMethodName = false
    var callsSuccess = false
    for (ins in instructions) {
        when (val reference = (ins as? ReferenceInstruction)?.reference) {
            is FieldReference ->
                if (reference.definingClass == "Lyj/n" && reference.name == "a") readsMethodName = true
            is MethodReference ->
                if (reference.definingClass == "Lfi/a" && reference.name == "success") callsSuccess = true
        }
    }
    if (!readsMethodName || !callsSuccess) return null

    return """
        iget-object v2, v$methodCallRegister, Lyj/n;->a
        const-string v3, "$SPLASH_AD_CHANNEL_METHOD"
        invoke-virtual {v3, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v3
        if-eqz v3, :original
        const-null v3
        invoke-virtual {v$resultRegister, v3}, Lfi/a;->success(Ljava/Lang.Object;)V
        return-void
        :original
    """.trimIndent()
}

// ── Layer 2: the shared_preferences backends ────────────────────────────────

/** Seeds the forged snapshot into the map readers of the prefs backends. */
private fun BytecodePatchContext.patchMapReader(candidate: Candidate): Boolean {
    val classDef = mutableClassDefBy(candidate.classDef)
    val method = classDef.mutableMethod(candidate) ?: return false
    if (method.isStatic() || method.localRegisterCount() < 3) return false

    val access = classDef.preferencesAccess(method) ?: return false
    val legacy = candidate.parameters.firstOrNull() == "Ljava/lang/String;"
    val keys = if (legacy) {
        listOf(PREFIXED_MEMBERSHIP_SNAPSHOT_KEY, MEMBERSHIP_SNAPSHOT_KEY)
    } else {
        listOf(MEMBERSHIP_SNAPSHOT_KEY)
    }

    method.addInstructions(0, preferencesSeedSmali(candidate.classDef.type, method, access, keys))
    return true
}

private fun preferencesSeedSmali(
    classType: String,
    method: MutableMethod,
    access: PreferencesAccess,
    keys: List<String>,
): String {
    val builder = StringBuilder()

    if (access.fieldName != null) {
        builder.appendLine(
            "iget-object v0, p0, $classType->${access.fieldName}:$SHARED_PREFERENCES"
        )
    } else {
        val parameterStart = method.parameterStart()
        val receiverOffset = if (method.isStatic()) 0 else 1
        val optionsRegister = parameterStart + receiverOffset + access.helperParameterIndex
        builder.appendLine(
            "invoke-virtual {p0, v$optionsRegister}, " +
                "$classType->${access.helperName}(${access.helperParameterType})$SHARED_PREFERENCES"
        )
        builder.appendLine("move-result-object v0")
    }

    builder.appendLine("invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences\$Editor;")
    builder.appendLine("move-result-object v0")

    val snapshotLiteral = membershipSnapshotJson()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    keys.forEach { key ->
        builder.appendLine("const-string v1, \"$key\"")
        builder.appendLine("const-string v2, \"$snapshotLiteral\"")
        builder.appendLine(
            "invoke-interface {v0, v1, v2}, " +
                "Landroid/content/SharedPreferences\$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)" +
                "Landroid/content/SharedPreferences\$Editor;"
        )
        builder.appendLine("move-result-object v0")
    }

    builder.appendLine("invoke-interface {v0}, Landroid/content/SharedPreferences\$Editor;->apply()V")
    return builder.toString().trimEnd()
}

// ── Shared helpers ──────────────────────────────────────────────────────────

private fun BytecodePatchContext.mutableMethod(candidate: Candidate): MutableMethod? =
    mutableClassDefBy(candidate.classDef).mutableMethod(candidate)

private fun MutableClass.mutableMethod(candidate: Candidate): MutableMethod? =
    methods.firstOrNull { method ->
        method.name == candidate.name &&
            method.returnType == candidate.returnType &&
            method.parameterTypes.map { it.toString() } == candidate.parameters
    }

private fun ClassDef.candidate(method: Method) = Candidate(
    classDef = this,
    name = method.name,
    parameters = method.parameterTypes.map { it.toString() },
    returnType = method.returnType.toString(),
)

/** True when the method calls the given method of the given class. */
private fun Method.calls(owner: String, name: String): Boolean =
    implementation?.instructions?.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference != null && reference.definingClass == owner && reference.name == name
    } == true

private fun Method.isStatic(): Boolean = AccessFlags.STATIC.isSet(accessFlags)

private fun Method.parameterStart(): Int =
    implementation!!.registerCount - parameterRegisterCount()

private fun Method.localRegisterCount(): Int =
    implementation!!.registerCount - parameterRegisterCount()

private fun Method.parameterRegisterCount(): Int =
    parameterTypes.size + if (isStatic()) 0 else 1

private fun MutableClass.preferencesAccess(method: MutableMethod): PreferencesAccess? {
    fields.firstOrNull { it.type == SHARED_PREFERENCES }?.let {
        return PreferencesAccess(fieldName = it.name)
    }

    val parameters = method.parameterTypes.map { it.toString() }
    methods.forEach { helper ->
        if (helper.returnType == SHARED_PREFERENCES &&
            helper.parameterTypes.size == 1 &&
            !AccessFlags.STATIC.isSet(helper.accessFlags) &&
            helper.implementation != null
        ) {
            val helperParameter = helper.parameterTypes.first().toString()
            val index = parameters.indexOf(helperParameter)
            if (index >= 0) {
                return PreferencesAccess(
                    helperName = helper.name,
                    helperParameterType = helperParameter,
                    helperParameterIndex = index,
                )
            }
        }
    }
    return null
}

private const val SHARED_PREFERENCES = "Landroid/content/SharedPreferences;"
private const val MAP = "Ljava/util/Map;"
private const val HASH_MAP = "Ljava/util/HashMap;"
