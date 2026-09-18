package app.template.patches.metservice

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val metServiceSplashSkipPatch = bytecodePatch(
    name = "Skip MetService Splash Ad",
    description = "Skip the splash advertisement and continue to the next screen.",
    default = true
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