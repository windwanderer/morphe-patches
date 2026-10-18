package app.template.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_Stellarium
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val premiumPatch = bytecodePatch(
    name = "Stellarium Premium",
    description = "Enable license OK state to unlock Stellarium Plus features."
) {
    compatibleWith(COMPATIBILITY_Stellarium)
    execute {
        val startCheckLicence = StartCheckLicenceFingerprint.methodOrNull
        val failCallback = LicenseFailCallbackFingerprint.methodOrNull
        val errorCallback = LicenseErrorCallbackFingerprint.methodOrNull

        check(startCheckLicence != null || failCallback != null || errorCallback != null) {
            "Could not locate the Stellarium license check in the APK."
        }

        // Primary target: report "license OK" right away and skip the obfuscated license SDK.
        startCheckLicence?.let { method ->
            val registerCount = method.implementation!!.registerCount

            // The license request id is the only parameter, so its two registers are the
            // last two registers of the method. This holds for static methods as well as
            // for instance methods (the receiver only sits before the long parameter).
            val parameterStart = registerCount - 2
            check(parameterStart >= 2) {
                "${method.definingClass}->${method.name} only has $registerCount registers, cannot patch"
            }

            // Skip the obfuscated license SDK entirely and tell the native engine
            // right away that the license is valid: cbLicenceStatusChanged(requestId, 1, 0).
            // Status 1 = license OK (0 = fail, 2 = error), reason 0 = no error.
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    const/4 v1, 0x0
                    invoke-static {v$parameterStart, v${parameterStart + 1}, v0, v1}, ${method.definingClass}->cbLicenceStatusChanged(JII)V
                    return-void
                """
            )
        }

        // Fallback target: force the fail and error callbacks to report "license OK"
        // as well, in case the license SDK is triggered from somewhere else.
        // Each callback has exactly one int constant, the status passed to the
        // native license callback, which is rewritten from 0/2 to 1 = license OK.
        if (failCallback != null) {
            val statusMatch = LicenseFailCallbackFingerprint.instructionMatches[0]
            failCallback.replaceInstruction(
                statusMatch.index,
                "const/4 v${statusMatch.getInstruction<OneRegisterInstruction>().registerA}, 0x1"
            )
        }

        if (errorCallback != null) {
            val statusMatch = LicenseErrorCallbackFingerprint.instructionMatches[0]
            errorCallback.replaceInstruction(
                statusMatch.index,
                "const/4 v${statusMatch.getInstruction<OneRegisterInstruction>().registerA}, 0x1"
            )
        }
    }
}
