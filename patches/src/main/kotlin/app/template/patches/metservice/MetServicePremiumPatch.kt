package app.template.patches.metservice

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions

val MetServicePremiumPatch = bytecodePatch(
    name = "MetService Premium Unlock",
    description = "Force premium_ad_free_enabled and subscription to always be true.",
    default = true
) {
    execute {
        val method = OnResumeFingerprint.method

        method.replaceInstructions(
            0,
            """
                const/4 v0, 0x1
                const/4 v1, 0x1
                return-void
            """
        )
    }
}
