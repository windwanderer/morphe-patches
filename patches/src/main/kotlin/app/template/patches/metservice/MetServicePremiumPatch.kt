package app.template.patches.metservice

import app.morphe.patcher.patch.bytecodePatch

val MetServicePremiumPatch = bytecodePatch(
    name = "MetService Premium Unlock",
    description = "Force BillingStatus.a() to always return true.",
    default = true
) {
    execute {
        val method = BillingStatusFingerprint.method

        method.replaceInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
