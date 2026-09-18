package app.template.patches.carscanner.premium

object PurchaseStateFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    methodName = "getPurchaseState",
    parameters = emptyList(),
    filters = listOf(
        string("purchaseState"),
        literal(1),
        literal(4),
        opcode(Opcode.IF_NE),
        opcode(Opcode.RETURN)
    )
)
