package app.template.patches.carscanner

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

val carScannerPremiumPatch = bytecodePatch(
    name = "CarScanner Premium Unlock",
    description = "Force Google Billing PurchaseState to PURCHASED for Car Scanner",
    default = true,
) {
    execute {
        PurchaseStateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}
