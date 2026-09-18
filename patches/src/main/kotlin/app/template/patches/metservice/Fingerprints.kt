package app.template.patches.billingstatus

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * BillingStatus enum patch fingerprint
 */
object BillingStatusFingerprint : Fingerprint(
    // Full class name
    definingClass = "Lcom/metservice/kryten/util/BillingStatus;",

    // Method name
    name = "a",

    // Access flags
    accessFlags = listOf(
        AccessFlags.PUBLIC,
        AccessFlags.FINAL
    ),

    // Return type
    returnType = "Z",

    // Parameters (none)
    parameters = listOf()
)
