package app.template.patches.stellarium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * License check → always report "license OK" to the native engine.
 *
 * Search: Log.i("Stellarium", "Check license")
 * ("Check License" in some releases, so the message is compared case-insensitively.)
 *
 * The license SDK itself (com.a.a.a.a.*, an obfuscated copy of Google's Play
 * licensing library) is fully obfuscated and changes every release, so the
 * method is located by the signature and its stable log message instead of by name.
 *
 * Original body:
 *   public static void startCheckLicence(long requestId) {
 *       Log.i("Stellarium", "Check license");
 *       b.f.a(new d() {
 *           public final void a(int reason) { Stellarium.cbLicenceStatusChanged(requestId, 1, reason); } // OK
 *           public final void b(int reason) { Stellarium.cbLicenceStatusChanged(requestId, 0, reason); } // fail
 *           public final void c(int reason) { Stellarium.cbLicenceStatusChanged(requestId, 2, reason); } // error
 *       });
 *   }
 *
 * Notes:
 * - accessFlags is intentionally not declared: the patcher matches access flags exactly,
 *   so declaring a subset such as STATIC never matches a public static method.
 *   The patch itself verifies that the matched method is static.
 * - No filters or strings are declared either, so matching uses the deterministic
 *   full class scan instead of the indexed candidate search.
 */
object StartCheckLicenceFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J"),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            val opcode = instruction.opcode
            (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) &&
                (instruction as? ReferenceInstruction)
                    ?.reference
                    ?.let { it as? StringReference }
                    ?.string
                    ?.contains("check licen", ignoreCase = true) == true
        } == true
    }
)

/**
 * License check failure callback → report license OK anyway.
 *
 * Search: Log.e("Stellarium", "License fail, reason:")
 *
 * Original body (com.noctuasoftware.stellarium_free.Stellarium$2):
 *   public final void b(int reason) {
 *       Stellarium.cbLicenceStatusChanged(this.a, 0, reason); // 0 = fail
 *       Log.e("Stellarium", "License fail, reason:".concat(String.valueOf(reason)));
 *   }
 *
 * Located by the exact log message and the int constant 0 that is passed to the
 * native license callback, so obfuscated names are not needed. The literal is
 * restricted to CONST_4 opcodes and is the only int constant of the method.
 */
object LicenseFailCallbackFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("License fail, reason:"),
    filters = listOf(
        literal(0, opcodes = listOf(Opcode.CONST_4)),
        methodCall(
            parameters = listOf("J", "I", "I"),
            returnType = "V",
            location = MatchAfterWithin(2)
        )
    )
)

/**
 * License check error callback → report license OK anyway.
 *
 * Search: Log.e("Stellarium", "License error, error:")
 *
 * Original body (com.noctuasoftware.stellarium_free.Stellarium$2):
 *   public final void c(int reason) {
 *       Stellarium.cbLicenceStatusChanged(this.a, 2, reason); // 2 = error
 *       Log.e("Stellarium", "License error, error:".concat(String.valueOf(reason)));
 *   }
 *
 * Located by the exact log message and the int constant 2 that is passed to the
 * native license callback, so obfuscated names are not needed. The literal is
 * restricted to CONST_4 opcodes and is the only int constant of the method.
 */
object LicenseErrorCallbackFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    strings = listOf("License error, error:"),
    filters = listOf(
        literal(2, opcodes = listOf(Opcode.CONST_4)),
        methodCall(
            parameters = listOf("J", "I", "I"),
            returnType = "V",
            location = MatchAfterWithin(2)
        )
    )
)
