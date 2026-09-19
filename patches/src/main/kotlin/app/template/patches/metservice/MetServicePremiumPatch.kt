package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val metServiceSplashSkipPatch = bytecodePatch(
    name = "Skip MetService Splash",
    description = "Skips the splash advertisement and removes the two second splash delay.",
    default = true
) {
    execute {

        // Skip splash advertisement
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

        // The first Lm92->o() call is the 2-second timer.
        // The second one is the 20-second timeout.
        SplashPresenterDelayFingerprint.let {
            val match = it.instructionMatches[0]

            it.method.replaceInstruction(
                match.index - 1,
                "const-wide/16 v7, 0x0"
            )
        }
    }
}