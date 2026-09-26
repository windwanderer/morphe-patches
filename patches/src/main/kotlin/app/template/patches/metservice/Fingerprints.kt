package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Premium check: `pj.n()Z` → always return true.
 *
 * Search: getBoolean("premium_ad_free_enabled", true)
 *
 * definingClass/name are obfuscated (pj/n), kept only to pin the current
 * target so a rename fails loudly instead of patching another method.
 */
object AppStateAdFreeFingerprint : Fingerprint(
    definingClass = "Lpj;",
    name = "n",
    returnType = "Z",
    parameters = emptyList(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        string("premium_ad_free_enabled"),
        methodCall(smali = "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately())
    )
)

/**
 * Splash delay: `yt4.o()V`, `dc3.p(2L, timeUnit, fc0Var)`.
 *
 * Search: 2L, timeUnit
 *
 * Patch reads instructionMatches.first().index - 1 (the const-wide before the
 * call), so methodCall(Ldc3;->p) must stay the first filter.
 */
object SplashPresenterTimerFingerprint : Fingerprint(
    definingClass = "Lyt4;",
    name = "o",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Ldc3;",
            name = "p"
        )
    )
)
