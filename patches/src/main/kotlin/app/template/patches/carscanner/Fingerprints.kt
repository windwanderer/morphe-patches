package app.template.patches.carscanner

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

internal object PurchaseStateFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    returnType = "I",
    accessFlags = listOf(AccessFlags.PUBLIC),
    strings = listOf("purchaseState"),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/json/JSONObject;",
            name = "optInt",
        )
    ),
)