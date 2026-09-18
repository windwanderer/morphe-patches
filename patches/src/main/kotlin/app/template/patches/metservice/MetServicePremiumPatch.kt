package com.metservice.kryten.patches

import app.revanced.morphe.Patch
import app.revanced.morphe.patch.bytecodePatch

class BillingStatusPatch : Patch() {
    override fun getFingerprints() = listOf(
        BillingStatusFingerprint
    )

    override fun apply(context: Patch.Context) {
        bytecodePatch(BillingStatusFingerprint) {
            replaceInstructions {
                loadBoolean(true)
                returnBoolean()
            }
        }
    }
}
