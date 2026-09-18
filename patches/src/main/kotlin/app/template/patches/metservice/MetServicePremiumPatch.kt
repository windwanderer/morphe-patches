package app.template.patches.billingstatus

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val billingStatusPatch = bytecodePatch(
    name = "BillingStatus Premium Unlock",
    description = "Force BillingStatus.a() to always return true.",
    default = true
) {
    // Match fingerprint
    dependsOn(BillingStatusFingerprint)

    // Patch logic
    execute {
        BillingStatusFingerprint.method.replaceInstructions {
            """
                const/4 v0, 0x1
                return v0
            """
        }
    }
}
