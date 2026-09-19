package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

object SplashControllerZ1Fingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashController;",
    name = "z1",
    returnType = "V",
    parameters = listOf("Ljava/util/Map;")
)

object SplashPresenterTimerFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashPresenter;",
    name = "t",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lm92;",
            name = "o"
        )
    )
)