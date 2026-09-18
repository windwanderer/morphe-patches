package app.template.patches.carscanner

import app.morphe.patcher.patch.bytecodePatch

val carScannerPremiumPatch = bytecodePatch(
    name = "CarScanner Premium Unlock",
    description = "Unlock premium features by overriding purchase state.",
    default = true
) {
    // compatibleWith(COMPATIBILITY_CARSCANNER)

    execute {
        PurchaseStateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x2
                return v0
            """
        )
    }
}
