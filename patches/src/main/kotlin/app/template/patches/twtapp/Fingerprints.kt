package app.template.patches.twtapp

import app.morphe.patcher.Fingerprint

/**
 * `CustomerInfoMapperKt.map(CustomerInfo)`, the Kotlin bridge that turns
 * RevenueCat's `CustomerInfo` into the map the `purchases_flutter` plugin
 * hands to Dart.
 *
 * The class belongs to the RevenueCat SDK, not to the app, so it keeps its
 * name between releases. The fingerprint only describes the shape of the
 * method (a `CustomerInfo` in, a `Map` out), which is the contract the plugin
 * relies on, so compiler or flag changes do not break it.
 */
val CustomerInfoMapFingerprint = Fingerprint(
    definingClass = "Lcom/revenuecat/purchases/hybridcommon/mappers/CustomerInfoMapperKt;",
    name = "map",
    parameters = listOf("Lcom/revenuecat/purchases/CustomerInfo;"),
    returnType = "Ljava/util/Map;"
)

/**
 * PairIP's `LicenseClient.processResponse(int, Bundle)`, which applies the
 * Play license verdict.
 *
 * PairIP is injected by Play and keeps its package and method names, so this
 * fingerprint does not depend on the app's obfuscation. Builds that do not
 * ship PairIP simply skip the layer.
 */
val ProcessResponseFingerprint = Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "processResponse",
    parameters = listOf("I", "Landroid/os/Bundle;"),
    returnType = "V"
)
