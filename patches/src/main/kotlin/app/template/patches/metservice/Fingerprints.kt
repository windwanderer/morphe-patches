package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object BillingStatusFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/util/BillingStatus;",
    name = "a",
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL)
)
