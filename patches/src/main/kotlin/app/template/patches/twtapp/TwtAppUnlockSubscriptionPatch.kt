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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

/*
 * Stargazing Hub (TWT App) is a Flutter app whose subscription state is read
 * from several places:
 *
 * 1. RevenueCat's hybrid common bridge, `CustomerInfoMapperKt.map`. The
 *    purchases_flutter plugin calls it for every CustomerInfo it hands to
 *    Dart, so rewriting the returned map makes Dart believe every entitlement
 *    is active.
 * 2. The shared_preferences plugin, which stores the app's own
 *    `is_subscribed` flag. The plugin ships several backends: the classic
 *    SharedPreferences API (getAll only, the Dart side caches the values), the
 *    asynchronous SharedPreferences backend, and the asynchronous DataStore
 *    backend. All of them are seeded/faked here.
 * 3. PairIP's license client, when the build ships it.
 *
 * The patch is written to survive releases it was not built for. The app's
 * own classes are obfuscated, and the obfuscated names change between
 * releases, so nothing is matched by class or method name. Every hook is
 * found by behaviour instead:
 *
 * - the boolean reader of a backend is the `(String, ...) -> Boolean` method
 *   that calls `SharedPreferences.getBoolean`,
 * - the datastore backend is the class that owns the `data_store` Pigeon
 *   channel, and its reader is the `(String, ...) -> Boolean` method in it,
 * - the getAll hook is a `Map`-returning method that calls
 *   `SharedPreferences.getAll`, seeded through the SharedPreferences field or
 *   the `(Options) -> SharedPreferences` helper of its own class, and the
 *   datastore equivalent is the `(List, ...) -> Map` method of the same class.
 *
 * RevenueCat's and PairIP's classes come from their SDKs and keep their names.
 * A build that does not contain one of the layers skips it instead of failing
 * the whole patch; if no layer at all can be found the patch fails, so a
 * release it no longer understands is never silently left unpatched.
 */

private const val SUBSCRIPTION_KEY = "is_subscribed"

/**
 * The same key as the classic shared_preferences API stores it. Its Dart side
 * strips the "flutter." prefix from everything getAll returns.
 */
private const val PREFIXED_SUBSCRIPTION_KEY = "flutter.$SUBSCRIPTION_KEY"

private const val SHARED_PREFERENCES = "Landroid/content/SharedPreferences;"
private const val BOOLEAN = "Ljava/lang/Boolean;"
private const val MAP = "Ljava/util/Map;"
private const val HASH_MAP = "Ljava/util/HashMap;"
private const val LIST = "Ljava/util/List;"

/** Pigeon channel of the DataStore backed shared_preferences API. */
private const val DATA_STORE_CHANNEL = "data_store"

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
 * How the hook can reach the SharedPreferences instance of its own backend:
 * either through the field the backend keeps it in, or through the helper
 * method that opens the file the backend reads.
 */
private class PreferencesAccess(
    val fieldName: String? = null,
    val helperName: String? = null,
    val helperParameterType: String? = null,
    val helperParameterIndex: Int = -1,
)

@Suppress("unused")
val twtAppUnlockSubscriptionPatch = bytecodePatch(
    name = "Unlock Subscription",
    description = "Unlocks all subscription features in TWT App.",
    default = true
) {
    compatibleWith(COMPATIBILITY_TWTApp)

    execute {
        var hooks = 0

        // ── Layer 1: RevenueCat's CustomerInfo bridge ────────────────────────
        runCatching {
            CustomerInfoMapFingerprint.method
        }.getOrNull()?.let { method ->
            val returnIndex = method.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
            val returnInstruction = method.instructions.elementAtOrNull(returnIndex) as? OneRegisterInstruction
            if (returnIndex >= 0 && returnInstruction != null && method.localRegisterCount() >= 11) {
                method.addInstructions(returnIndex, spoofedCustomerInfo(returnInstruction.registerA))
                hooks++
                logger.info("Spoofed ${method.definingClass}->${method.name}")
            } else {
                logger.warning("RevenueCat map found, but its return does not have room for the spoof")
            }
        }

        // ── Layer 2: the shared_preferences backends ─────────────────────────
        val booleanReaders = mutableListOf<Candidate>()
        val mapReaders = mutableListOf<Candidate>()

        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                if (method.implementation == null) return@forEach
                val parameters = method.parameterTypes.map { it.toString() }
                val returnType = method.returnType.toString()

                when {
                    returnType == BOOLEAN &&
                        parameters.size == 2 &&
                        parameters[0] == "Ljava/lang/String;" &&
                        method.calls("Landroid/content/SharedPreferences;", "getBoolean") ->
                        booleanReaders += classDef.candidate(method)

                    (returnType == MAP || returnType == HASH_MAP) &&
                        parameters.size >= 2 &&
                        method.calls("Landroid/content/SharedPreferences;", "getAll") ->
                        mapReaders += classDef.candidate(method)
                }
            }
        }

        booleanReaders.forEach { candidate ->
            if (patchBooleanReader(candidate)) {
                hooks++
                logger.info("Forced $SUBSCRIPTION_KEY in ${candidate.classDef.type}->${candidate.name}")
            }
        }

        mapReaders.forEach { candidate ->
            if (patchMapReader(candidate)) {
                hooks++
                logger.info("Seeded $SUBSCRIPTION_KEY into ${candidate.classDef.type}->${candidate.name}")
            }
        }

        // ── Layer 3: the DataStore backed reader ─────────────────────────────
        classDefByStrings(DATA_STORE_CHANNEL).forEach { classDef ->
            classDef.methods.forEach { method ->
                if (method.implementation == null) return@forEach
                val parameters = method.parameterTypes.map { it.toString() }
                val returnType = method.returnType.toString()

                val patched = when {
                    returnType == BOOLEAN &&
                        parameters.size == 2 &&
                        parameters[0] == "Ljava/lang/String;" ->
                        patchBooleanReader(classDef.candidate(method))

                    (returnType == MAP || returnType == HASH_MAP) &&
                        parameters.size == 2 &&
                        parameters[0] == LIST ->
                        patchDataStoreMapReader(classDef.candidate(method))

                    else -> false
                }

                if (patched) {
                    hooks++
                    logger.info("Forced $SUBSCRIPTION_KEY in ${classDef.type}->${method.name}")
                }
            }
        }

        // ── Layer 4: PairIP's license response ───────────────────────────────
        runCatching {
            ProcessResponseFingerprint.method
        }.getOrNull()?.let { method ->
            method.addInstructions(0, "return-void")
            hooks++
            logger.info("Bypassed ${method.definingClass}->${method.name}")
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

/**
 * Returns true for [SUBSCRIPTION_KEY] without touching the backend.
 *
 * The key is the first declared parameter of the method, both for static and
 * instance methods: smali puts it right after the receiver.
 */
private fun BytecodePatchContext.patchBooleanReader(candidate: Candidate): Boolean {
    val method = mutableMethod(candidate) ?: return false
    if (method.localRegisterCount() < 1) return false

    val keyRegister = method.parameterStart() + if (method.isStatic()) 0 else 1
    method.addInstructions(0, booleanEarlyReturn(keyRegister, scratch = 0))
    return true
}

/**
 * Seeds [SUBSCRIPTION_KEY] into the preferences before the backend reads them.
 *
 * Writing at the entry of getAll instead of injecting the returned map is
 * deliberate: getAll loops, and its single `return` is a branch target, so
 * instructions inserted in front of it would be skipped by every branch that
 * jumps straight to the return.
 */
private fun BytecodePatchContext.patchMapReader(candidate: Candidate): Boolean {
    val classDef = mutableClassDefBy(candidate.classDef)
    val method = classDef.mutableMethod(candidate) ?: return false
    if (method.isStatic() || method.localRegisterCount() < 3) return false

    val access = classDef.preferencesAccess(method) ?: return false
    val legacy = candidate.parameters.firstOrNull() == "Ljava/lang/String;"
    val keys = if (legacy) {
        listOf(PREFIXED_SUBSCRIPTION_KEY, SUBSCRIPTION_KEY)
    } else {
        listOf(SUBSCRIPTION_KEY)
    }

    method.addInstructions(0, preferencesSeedSmali(candidate.classDef.type, method, access, keys))
    return true
}

/**
 * Adds [SUBSCRIPTION_KEY] to the map the DataStore backend returns.
 *
 * The method is a straight line coroutine bridge, so its return has no other
 * branches and the map can be edited in place right before returning it.
 */
private fun BytecodePatchContext.patchDataStoreMapReader(candidate: Candidate): Boolean {
    val method = mutableMethod(candidate) ?: return false
    val implementation = method.implementation ?: return false
    val instructions = implementation.instructions.toList()
    val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    if (returnIndex < 0) return false

    val mapRegister = (instructions[returnIndex] as? OneRegisterInstruction)?.registerA ?: return false
    val scratch = (0 until method.localRegisterCount()).filter { it != mapRegister }
    if (scratch.size < 2) return false

    method.addInstructions(
        returnIndex,
        """
        const-string v${scratch[0]}, "$SUBSCRIPTION_KEY"
        sget-object v${scratch[1]}, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
        invoke-interface {v$mapRegister, v${scratch[0]}, v${scratch[1]}}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        """.trimIndent()
    )
    return true
}

/** The `map`, `mapper` and `all` parts of the CustomerInfo the plugin expects. */
private fun spoofedCustomerInfo(returnRegister: Int): String {
    val copyOriginal = if (returnRegister == 0) "" else "move-object v0, v$returnRegister"
    return """
        $copyOriginal
        new-instance v1, Ljava/util/HashMap;
        invoke-direct {v1, v0}, Ljava/util/HashMap;-><init>(Ljava/util/Map;)V

        new-instance v2, Ljava/util/HashMap;
        invoke-direct {v2}, Ljava/util/HashMap;-><init>()V

        const-string v3, "identifier"
        const-string v4, "premium"
        invoke-interface {v2, v3, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "isActive"
        const/4 v5, 0x1
        invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
        move-result-object v5
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "willRenew"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "periodType"
        const-string v5, "NORMAL"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "latestPurchaseDate"
        const-string v5, "2026-06-07T00:00:00.000Z"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "latestPurchaseDateMillis"
        const-wide v6, 0x19ebea24000L
        invoke-static {v6, v7}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
        move-result-object v6
        invoke-interface {v2, v3, v6}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "originalPurchaseDate"
        const-string v5, "2026-06-07T00:00:00.000Z"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "originalPurchaseDateMillis"
        invoke-interface {v2, v3, v6}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "expirationDate"
        const-string v5, "2035-06-07T00:00:00.000Z"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "expirationDateMillis"
        const-wide v8, 0x1e0e854d000L
        invoke-static {v8, v9}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
        move-result-object v8
        invoke-interface {v2, v3, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "store"
        const-string v5, "PLAY_STORE"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "productIdentifier"
        const-string v5, "premium"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "productPlanIdentifier"
        const/4 v5, 0x0
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "isSandbox"
        const/4 v5, 0x0
        invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
        move-result-object v5
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "unsubscribeDetectedAt"
        const/4 v5, 0x0
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "unsubscribeDetectedAtMillis"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "billingIssueDetectedAt"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "billingIssueDetectedAtMillis"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "ownershipType"
        const-string v5, "PURCHASED"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "verification"
        const-string v5, "VERIFIED"
        invoke-interface {v2, v3, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        new-instance v3, Ljava/util/HashMap;
        invoke-direct {v3}, Ljava/util/HashMap;-><init>()V

        new-instance v5, Ljava/util/HashMap;
        invoke-direct {v5}, Ljava/util/HashMap;-><init>()V
        invoke-interface {v5, v4, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        new-instance v10, Ljava/util/HashMap;
        invoke-direct {v10}, Ljava/util/HashMap;-><init>()V
        invoke-interface {v10, v4, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v2, "active"
        invoke-interface {v3, v2, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v2, "all"
        invoke-interface {v3, v2, v10}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v2, "verification"
        const-string v4, "VERIFIED"
        invoke-interface {v3, v2, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v2, "entitlements"
        invoke-interface {v1, v2, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        new-instance v2, Ljava/util/ArrayList;
        invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V
        const-string v3, "premium"
        invoke-interface {v2, v3}, Ljava/util/List;->add(Ljava/lang/Object;)Z

        const-string v3, "activeSubscriptions"
        invoke-interface {v1, v3, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        const-string v3, "allPurchasedProductIdentifiers"
        invoke-interface {v1, v3, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

        move-object v$returnRegister, v1
        """.trimIndent()
}

/** Returns `true` when the key parameter equals [SUBSCRIPTION_KEY]. */
private fun booleanEarlyReturn(keyRegister: Int, scratch: Int): String =
    """
    const-string v$scratch, "$SUBSCRIPTION_KEY"
    invoke-virtual {v$keyRegister, v$scratch}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v$scratch
    if-eqz v$scratch, :not_subscribed
    const/4 v$scratch, 0x1
    invoke-static {v$scratch}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v$scratch
    return-object v$scratch
    :not_subscribed
    """.trimIndent()

/**
 * Writes [keys] into the backend's SharedPreferences before its original
 * body reads them. `edit().apply()` updates the in-memory map synchronously,
 * so the getAll that follows already returns the injected values.
 */
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

    keys.forEach { key ->
        builder.appendLine("const-string v1, \"$key\"")
        builder.appendLine("const/4 v2, 0x1")
        builder.appendLine(
            "invoke-interface {v0, v1, v2}, " +
                "Landroid/content/SharedPreferences\$Editor;->putBoolean(Ljava/lang/String;Z)" +
                "Landroid/content/SharedPreferences\$Editor;"
        )
        builder.appendLine("move-result-object v0")
    }

    builder.appendLine("invoke-interface {v0}, Landroid/content/SharedPreferences\$Editor;->apply()V")
    return builder.toString().trimEnd()
}

/** The field or helper the backend uses to open its SharedPreferences file. */
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

/** Number of parameter registers, including the receiver of an instance method. */
private fun Method.parameterRegisterCount(): Int =
    parameterTypes.size + if (isStatic()) 0 else 1

/** First local register of the method, which is also the receiver's register. */
private fun Method.parameterStart(): Int =
    implementation!!.registerCount - parameterRegisterCount()

/** Registers the method has besides its parameters. */
private fun Method.localRegisterCount(): Int =
    implementation!!.registerCount - parameterRegisterCount()
