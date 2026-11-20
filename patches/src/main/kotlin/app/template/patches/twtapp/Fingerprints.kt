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
 * The read primitive of the FlutterSecureStorage plugin as obfuscated by the
 * plugin's own packer. The plugin's worker thread (`Lhc/n`) calls it to
 * decrypt the value of a read request, so it sees every value the app reads
 * — including the membership snapshot the app gates premium features on
 * since 3.7.0.
 *
 * Behavior-based: the class is in the plugin's obfuscated namespace, not the
 * app's, and its method name is stable for the plugin, so the fingerprint is
 * by shape rather than by app behavior. Behavior-based description: the
 * `String`-returning method of the plugin helper that reads a value.
 */
val MembershipSnapshotReadFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/internal/consent_sdk/c;",
    name = "b",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;"
)

/**
 * The write primitive of the FlutterSecureStorage plugin as obfuscated by the
 * plugin's own packer: the worker thread (`Lhc/n`) calls it for every write
 * request, with the storage key and the value, so it sees every key the app
 * writes — including the membership snapshot since 3.7.0.
 *
 * Behavior-based description: the `Void`-returning method of the plugin
 * helper that stores a key/value pair.
 */
val MembershipSnapshotWriteFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/internal/consent_sdk/c;",
    name = "x",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "V"
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
