package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val metServiceSplashAdPatch = bytecodePatch(
    name = "Disable MetService Splash Ad",
    description = "Disable the splash screen advertisement.",
    default = true
) {
    execute {
        SplashControllerZ1Fingerprint.method.addInstructions(
            0,
            """
                return-void
            """
        )
    }
}