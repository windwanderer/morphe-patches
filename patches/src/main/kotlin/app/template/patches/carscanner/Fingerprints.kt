package app.template.patches.carscanner

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

object PurchaseStateFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    name = "getPurchaseState",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    parameters = emptyList(),

    filters = listOf(
        string("purchaseState"),
        literal(1),
        literal(4),
        opcode(Opcode.IF_NE),
        opcode(Opcode.RETURN)
    )
)
