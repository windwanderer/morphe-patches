package com.metservice.kryten.patches

import app.revanced.morphe.Fingerprint
import app.revanced.morphe.AccessFlags

object BillingStatusFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/util/BillingStatus;",
    name = "a",
    returnType = "Z",
    accessFlags = listOf(
        AccessFlags.PUBLIC,
        AccessFlags.FINAL
    )
)
