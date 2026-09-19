package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val splashAdPatch = bytecodePatch(
    name = "MetService Skip Splash Ad",
    description = "Skip splash advertisement."
) {
    execute {

        SplashControllerZ1Fingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/metservice/kryten/ui/splash/SplashController;->M:Lso1;
                invoke-interface {v0}, Lso1;->getValue()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Lcom/metservice/kryten/ui/splash/SplashPresenter;
                const/4 v1, 0x0
                invoke-virtual {v0, v1}, Lcom/metservice/kryten/ui/splash/SplashPresenter;->C(I)V
                return-void
            """
        )
    }
}

@Suppress("unused")
val splashDelayPatch = bytecodePatch(
    name = "MetService Remove Splash Delay",
    description = "Remove the 2-second splash delay."
) {
    execute {

        val timerMatch =
            SplashPresenterTimerFingerprint.instructionMatches.first()

        SplashPresenterTimerFingerprint.method.replaceInstruction(
            timerMatch.index - 1,
            "const-wide/16 v7, 0x0"
        )
    }
}

@Suppress("unused")
val premiumPatch = bytecodePatch(
    name = "MetService Premium",
    description = "Enable ad-free state."
) {
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