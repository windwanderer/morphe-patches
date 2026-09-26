package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * PREMIUM LOGIC
 *
 * Premium check moved from old AppState.t() → new pj.n()
 *
 * New logic:
 *     return i().b && h().getBoolean("premium_ad_free_enabled", true);
 *
 * Search keyword to locate this method in future APK updates:
 *     getBoolean("premium_ad_free_enabled", true)
 *
 * Why this works:
 * - All premium checks reference "premium_ad_free_enabled"
 * - This string is stable across versions
 * - pj.n() is the only method combining subscription + ad-free flags
 *
 * Patch target:
 *     pj.n() → always return true
 */
object AppStateAdFreeFingerprint : Fingerprint(
    definingClass = ":defpackage/pj",
    name = "n",
    returnType = "Z",
    filters = listOf(
        string("premium_ad_free_enabled")
    )
)

/**
 * NEW SPLASH LOGIC (MetService latest APK)
 *
 * Splash delay moved from old SplashActivity → new defpackage.yt4.o()
 *
 * New delay code:
 *     od3 od3VarJ = dc3.p(2L, timeUnit, fc0Var).j(zb.a());
 *
 * Meaning:
 *     dc3.p(2L, ...) = 2-second delay before continuing
 *
 * Search keyword to locate this method in future APK updates:
 *     2L, timeUnit
 *
 * Patch target:
 *     yt4.o() → return immediately (skip splash delay)
 */

object SplashPresenterTimerFingerprint : Fingerprint(
    definingClass = ":defpackage/yt4",
    name = "o",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),

    filters = listOf(
        methodCall(
            definingClass = ":defpackage/dc3",
            name = "p"
        )
    )
)