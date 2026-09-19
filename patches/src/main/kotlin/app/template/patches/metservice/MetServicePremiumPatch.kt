package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val splashPatch = bytecodePatch(
    name = "MetService Splash",
    description = "Skip splash advertisement and remove splash delay."
) {
    execute {

        // ------------------------------------------------------------
        // 1. Skip splash advertisement
        // ------------------------------------------------------------

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

        // ------------------------------------------------------------
        // 2. Remove the 2-second splash delay
        //
        // Original:
        //
        // const-wide/16 v7, 0x2
        // invoke-static {v7, v8, v2, v0}, Lm92;->o(...)
        //
        // Change 2 seconds -> 0 seconds.
        //
        // There are two m92.o() calls in SplashPresenter.t():
        //
        //   first  = 2 seconds
        //   second = 20 seconds timeout
        //
        // instructionMatches.first() targets the first one.
        // ------------------------------------------------------------

        val timerMatch =
            SplashPresenterTimerFingerprint.instructionMatches.first()

        SplashPresenterTimerFingerprint.method.replaceInstruction(
            timerMatch.index - 1,
            "const-wide/16 v7, 0x0"
        )
    }
}