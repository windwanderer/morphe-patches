package app.template.patches.carscanner.premium

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.data.PatchContext

class CarScannerPremiumPatch : Patch() {

    override val name = "CarScanner Premium Unlock"
    override val description = "Unlock premium features by overriding purchase state"
    override val default = true
    override val category = "Premium"

    override fun getFingerprints() = listOf(
        PurchaseStateFingerprint
    )

    override fun apply(context: PatchContext) {
        context.hook(PurchaseStateFingerprint) {
            replace { 2 }
        }
    }
}
