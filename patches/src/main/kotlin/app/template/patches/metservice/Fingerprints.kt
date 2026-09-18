package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

object AppStateTFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/AppState;",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/metservice/kryten/util/SubscriptionState;",
            name = "c",
        ),
        string("premium_ad_free_enabled"),
        methodCall(
            definingClass = "Landroid/content/SharedPreferences;",
            name = "getBoolean",
        ),
    )
)