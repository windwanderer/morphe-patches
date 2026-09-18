package app.template.patches.carscanner

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object PurchaseStateFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    returnType = "I",
    accessFlags = listOf(AccessFlags.PUBLIC),
    strings = listOf("purchaseState"),
    literals = listOf(1, 4),
)
