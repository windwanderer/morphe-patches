package app.template.patches.twtapp

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_TWTApp
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

/*
 * Skips the app's startup splash screen.
 *
 * The app shows its own splash by handing a `SplashScreenView` to
 * `MainActivity.q(MainActivity, SplashScreenView)`, which removes the view
 * when the device animation scale is 0 and otherwise animates it out.
 * Hooking `q` to remove the view and return immediately skips the splash
 * no matter what the animation scale is.
 *
 * The hook is found by behavior: the method must remove the
 * `SplashScreenView`, which is its last parameter.
 */

private val logger = Logger.getLogger("TwtAppSkipSplashScreen")

/**
 * The splash-screen method: `com.twtapp.MainActivity;->q(MainActivity, SplashScreenView)`
 * — behavioral check: the body removes the `SplashScreenView`.
 */
val SkipSplashScreenFingerprint = Fingerprint(
    definingClass = "Lcom/twtapp/MainActivity;",
    name = "q",
    parameters = listOf("Lcom/twtapp/MainActivity;", "Landroid/window/SplashScreenView;"),
    returnType = "V"
)

@Suppress("unused")
val twtAppSkipSplashScreenPatch = bytecodePatch(
    name = "Skip Splash Screen",
    description = "Removes the app's startup splash screen by removing the SplashScreenView immediately.",
    default = true
) {
    compatibleWith(COMPATIBILITY_TWTApp)

    execute {
        val method = SkipSplashScreenFingerprint.method ?: return@execute

        // Behavioral check: the method must remove the splash view.
        val callsRemove = method.implementation?.instructions?.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref != null && ref.definingClass == "Landroid/window/SplashScreenView;" && ref.name == "remove"
        } == true
        if (!callsRemove) return@execute

        // The SplashScreenView is the last parameter. The first parameter
        // register is parameterStart() + 1 for instance methods, so the
        // splash view register is ps + parameterCount - 1.
        val ps = method.parameterStart() + if (method.isStatic()) 0 else 1
        val splashViewRegister = ps + method.parameterTypes.size - 1

        method.addInstructions(
            0,
            "invoke-virtual {v$splashViewRegister}, Landroid/window/SplashScreenView;->remove()V\n" +
                "return-void"
        )
        logger.info("Skipped splash screen in ${method.definingClass}->${method.name}")
    }
}

private fun Method.isStatic(): Boolean = AccessFlags.STATIC.isSet(accessFlags)

private fun Method.parameterStart(): Int = implementation!!.registerCount - parameterRegisterCount()

private fun Method.parameterRegisterCount(): Int = parameterTypes.size + if (isStatic()) 0 else 1
