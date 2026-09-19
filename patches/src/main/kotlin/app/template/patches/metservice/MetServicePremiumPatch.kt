package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_MetService

@Suppress("unused")
val premiumPatch = bytecodePatch(
    name = "MetService Premium",
    description = "Enable ad-free state."
) {
    compatibleWith(COMPATIBILITY_MetService)
    execute {
        AppStateAdFreeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}

@Suppress("unused")
val splashDelayPatch = bytecodePatch(
    name = "MetService Remove Splash Delay",
    description = "Remove the 2-second splash delay."
) {
    compatibleWith(COMPATIBILITY_MetService)
    execute {

        val timerMatch =
            SplashPresenterTimerFingerprint.instructionMatches.first()

        SplashPresenterTimerFingerprint.method.replaceInstruction(
            timerMatch.index - 1,
            "const-wide/16 v7, 0x0"
        )
    }
}