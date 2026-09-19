package app.template.patches.metservice

import app.morphe.patcher.Fingerprint

object SplashControllerZ1Fingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashController;",
    name = "z1",
    returnType = "V",
    parameters = listOf("Ljava/util/Map;")
)

object SplashPresenterTFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashPresenter;",
    name = "t",
    returnType = "V",
    parameters = emptyList()
)