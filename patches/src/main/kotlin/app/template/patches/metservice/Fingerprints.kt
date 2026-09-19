package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

object SplashControllerZ1Fingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashController;",
    name = "z1",
    returnType = "V",
    parameters = listOf("Ljava/util/Map;")
)

object SplashPresenterConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/splash/SplashPresenter;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Lcom/metservice/kryten/service/broker/LocationBroker;",
        "Lcom/metservice/kryten/service/location/LocationSource;",
        "Lcom/metservice/kryten/util/TimeProvider;",
        "Lcom/metservice/kryten/AppState;",
        "Z",
        "I",
        "Lcom/metservice/kryten/model/Location;",
        "Lcom/metservice/kryten/util/AnalyticsAdapter;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/lang/System;",
            name = "currentTimeMillis",
            returnType = "J"
        )
    )
)