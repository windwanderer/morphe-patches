package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val metServicePremiumPatch = bytecodePatch(
    name = "MetService Premium Patch",
    description = "Modify AppState premium state.",
    default = true
) {
    execute {
        AppStateTFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
