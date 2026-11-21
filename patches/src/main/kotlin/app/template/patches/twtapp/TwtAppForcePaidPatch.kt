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
 * Forces the app's paid-status flag.
 *
 * The app's static entry class `com.byazt.sp.tc` exposes `v(boolean)`: it
 * builds a `Bundle`, writes the boolean under the key `"is_paid"` and
 * submits it to the SDK manager (`DispatchAdSdkInitializerHolder.getCsjManger()`).
 * The app decides the value from its membership state, so the flag is only
 * true when the membership snapshot grants premium; forcing the parameter to
 * true makes the app believe it is paid.
 *
 * The hook is found by behavior: the method must call
 * `Landroid/os/Bundle;->putBoolean` / `Landroid/os/BaseBundle;->putBoolean`
 * (the "is_paid" write).
 */

private val logger = Logger.getLogger("TwtAppForcePaid")

/**
 * The paid-status setter: `com.byazt.sp.tc;->v(Z)V` — behavioral check:
 * the body calls `android/os/Bundle;->putBoolean`.
 */
val ForcePaidFingerprint = Fingerprint(
    definingClass = "Lcom/byazt/sp/tc;",
    name = "v",
    parameters = listOf("Z"),
    returnType = "V"
)

@Suppress("unused")
val twtAppForcePaidPatch = bytecodePatch(
    name = "Force Paid Status",
    description = "Forces the app's paid-status flag (\"is_paid\") to true.",
    default = true
) {
    compatibleWith(COMPATIBILITY_TWTApp)

    execute {
        val method = ForcePaidFingerprint.method ?: return@execute

        // Behavioral check: the method must write "is_paid" via Bundle/BaseBundle.putBoolean.
        val callsPutBoolean = method.implementation?.instructions?.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref != null && ref.name == "putBoolean" &&
                (ref.definingClass == "Landroid/os/Bundle;" || ref.definingClass == "Landroid/os/BaseBundle;")
        } == true
        if (!callsPutBoolean) return@execute

        // The boolean parameter register: static -> first param at parameterStart(),
        // instance -> the register after the receiver.
        val ps = method.parameterStart() + if (method.isStatic()) 0 else 1
        method.addInstructions(0, "const/16 v$ps, 1")
        logger.info("Forced paid status in ${method.definingClass}->${method.name}")
    }
}

private fun Method.isStatic(): Boolean = AccessFlags.STATIC.isSet(accessFlags)

private fun Method.parameterStart(): Int =
    implementation!!.registerCount - parameterRegisterCount()

private fun Method.parameterRegisterCount(): Int =
    parameterTypes.size + if (isStatic()) 0 else 1
