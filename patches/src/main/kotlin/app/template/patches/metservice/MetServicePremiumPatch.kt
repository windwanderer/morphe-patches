package app.morphe.patches.metservice

import app.morphe.patcher.patch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction

@Suppress("unused")
val SplashPatch = patch(
    name = "MetService Splash Patch",
    description = "Skip splash advertisement and remove splash delay",
) {

    // Skip loading/showing the splash advertisement.
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

    // Initialize SplashPresenter.n to TRUE instead of FALSE.
    //
    // Original:
    //     sget-object p1, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
    //     invoke-static {p1}, Lvq;->q(Ljava/lang/Object;)Lvq;
    //     move-result-object p1
    //     iput-object p1, p0, Lcom/metservice/kryten/ui/splash/SplashPresenter;->n:Lvq;
    //
    // The fingerprint points at the iput-object to field n,
    // so the FALSE instruction is three instructions before it.
    val match = SplashPresenterConstructorFingerprint.instructionMatches.first()

    SplashPresenterConstructorFingerprint.method.replaceInstruction(
        match.index - 3,
        "sget-object p1, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"
    )
}