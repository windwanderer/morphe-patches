package app.template.patches.twtapp

import app.morphe.patcher.Fingerprint

/**
 * The plugin's write primitive, `Lcom/google/android/gms/internal/consent_sdk/c;->x(String, String)V`.
 *
 * The Gromore plugin class is obfuscated by the plugin's own packer and its
 * name changes between plugin versions; the class is in the plugin's
 * obfuscated namespace, not the app's, and the method name is stable for the
 * plugin, so the fingerprint is by shape rather than by app behavior.
 * Behavior-based: the `Void`-returning method of the plugin helper that
 * stores a key/value pair, called by the plugin worker for every write.
 */
val MembershipSnapshotWriteFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/internal/consent_sdk/c;",
    name = "x",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "V"
)

/**
 * The plugin's read primitive, `Lcom/google/android/gms/internal/consent_sdk/c;->b(String)Ljava/lang/String;`.
 *
 * Behavior-based: the `String`-returning method of the plugin helper that
 * decrypts the value of a read, called by the plugin worker for every read.
 */
val MembershipSnapshotReadFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/internal/consent_sdk/c;",
    name = "b",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/String;"
)

/**
 * The plugin's `readAll` primitive, `Lcom/google/android/gms/internal/consent_sdk/c;->s()Ljava/util/HashMap;`.
 *
 * Behavior-based: the `HashMap`-returning method of the plugin helper that
 * returns the decrypted map of all stored values, called by the plugin
 * worker for the "readAll" channel method.
 */
val MembershipSnapshotReadAllFingerprint = Fingerprint(
    definingClass = "Lcom/google/android/gms/internal/consent_sdk/c;",
    name = "s",
    parameters = listOf(),
    returnType = "Ljava/util/HashMap;"
)

/**
 * The Gromore plugin's MethodChannel handler — the obfuscated `Lxi/d`
 * serving `flutter_gromore_ads`. Its `onMethodCall` handles the
 * `showSplashAd` channel call that makes the app show the splash page ad
 * on open.
 */
val SplashAdPluginFingerprint = Fingerprint(
    definingClass = "Lxi/d;",
    name = "onMethodCall",
    parameters = listOf("Lyj/n;", "Lyj/p;"),
    returnType = "V"
)
