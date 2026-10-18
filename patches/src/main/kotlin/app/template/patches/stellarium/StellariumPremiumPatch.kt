package app.template.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_Stellarium
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val premiumPatch = bytecodePatch(
    name = "Stellarium Premium",
    description = "Enable license OK state to unlock Stellarium Plus features."
) {
    compatibleWith(COMPATIBILITY_Stellarium)
    execute {
        val method = StartCheckLicenceFingerprint.method

        // The fingerprint does not declare access flags (the patcher matches them exactly),
        // so verify here, because the register calculation below assumes a static method.
        check(AccessFlags.STATIC.isSet(method.accessFlags)) {
            "startCheckLicence is not static (access flags: 0x${method.accessFlags.toString(16)})"
        }

        val registerCount = method.implementation!!.registerCount

        // startCheckLicence is static with a single long parameter, so the two
        // parameter registers are the last two registers of the method.
        val parameterStart = registerCount - 2
        check(parameterStart >= 2) {
            "startCheckLicence only has $registerCount registers, cannot patch"
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
}
