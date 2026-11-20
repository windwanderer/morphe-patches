package app.template.patches.twtapp

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_TWTApp
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

/*
 * Intercepts the splash page ad.
 *
 * The app calls the Gromore plugin's `onMethodCall` with the method name
 * "showSplashAd" when it wants to show the splash page ad on open. This patch
 * prepends an early check to that method: when the app calls the plugin with
 * the method name "showSplashAd", the intercept calls the plugin's result
 * immediately, so the splash page ad never shows.
 *
 * Every hook is found by behavior, not by the obfuscated app class names. A
 * build that does not contain the handler skips it; the patch fails if the
 * splash handler is not found at all.
 */

/** The channel method the app calls to show the splash page ad. */
private const val SPLASH_AD_CHANNEL_METHOD = "showSplashAd"

private val logger = Logger.getLogger("TwtAppSplashAd")

/**
 * The Gromore plugin's MethodChannel handler — the obfuscated `Lxi/d`
 * serving `flutter_gromore_ads`. Its `onMethodCall` handles the
 * `showSplashAd` channel call that makes the app show the splash page ad
 * on open.
 */
val SplashAdPluginFingerprint = Fingerprint(
    definingClass = "Lxi/d;",
    name = "onMethodCall",
    parameters = listOf("Lyj/n;", "Lyj/p;"),
    returnType = "V"
)

@Suppress("unused")
val twtAppSplashAdInterceptPatch = bytecodePatch(
    name = "Splash Ad Intercept",
    description = "Intercepts the TWT App splash page ad on app open.",
    default = false
) {
    compatibleWith(COMPATIBILITY_TWTApp)

    execute {
        var hooks = 0
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
                "The splash ad handler could not be found. " +
                    "The app's MethodChannel code changed shape."
            )
        }
        logger.info("Splash Ad Intercept: $hooks hook(s) applied")
    }
}

/**
 * Smali that intercepts the Gromore plugin's `showSplashAd` channel call:
 * when the app calls the plugin with the method name "showSplashAd", the
 * intercept calls the plugin's result immediately, so the app proceeds and
 * the splash page ad never shows.
 */
private fun splashAdInterceptHook(method: Method): String? {
    val methodCallRegister = method.parameterStart() + if (method.isStatic()) 0 else 1

    // Behavioral check: the handler must read the MethodCall's method-name
    // field and call the Result's `success`, otherwise there is nothing to
    // intercept — e.g. the method might have been renamed or restructured.
    val instructions = method.implementation?.instructions ?: return null
    var readsMethodName = false
    var callsSuccess = false
    for (ins in instructions) {
        when (val reference = (ins as? ReferenceInstruction)?.reference) {
            is FieldReference ->
                if (reference.definingClass.contains("Ly") && reference.name == "a") readsMethodName = true
            is MethodReference ->
                if (reference.definingClass.contains("Lfi") && reference.name == "success") callsSuccess = true
        }
    }
    logger.info("splashAdInterceptHook: readsMethodName=$readsMethodName callsSuccess=$callsSuccess")
    if (!readsMethodName || !callsSuccess) return null

    return """
        iget-object v2, v$methodCallRegister, Lyj/n;->a:Ljava/lang/String;
        const-string v3, "$SPLASH_AD_CHANNEL_METHOD"
        invoke-virtual {v3, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
        move-result v3
        if-eqz v3, :original
        invoke-virtual {v2, v$methodCallRegister}, Lfi/a;->success(Ljava/lang/Object;)V
        return-void
        :original
    """.trimIndent()
}
