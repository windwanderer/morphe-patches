package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

object AppStateAdFreeFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/AppState;",
    name = "t",
    returnType = "Z",
    parameters = emptyList()
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