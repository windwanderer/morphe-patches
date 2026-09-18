package app.template.patches.metservice

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions

val MetServicePremiumPatch = bytecodePatch(
    name = "MetService Premium Unlock",
    description = "Force AppState.t() to always return true.",
    default = true
) {
    execute {
        val method = AppStateTFingerprint.method

        method.replaceInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
