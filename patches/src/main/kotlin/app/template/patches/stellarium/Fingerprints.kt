package app.template.patches.stellarium

import app.morphe.patcher.Fingerprint
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
 *   so declaring a subset such as STATIC would never match a public static method.
 *   The patch itself verifies that the matched method is static.
 * - No other fields are declared either, so matching uses the deterministic full class scan
 *   instead of the indexed candidate search.
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
                    ?.equals("Check license", ignoreCase = true) == true
        } == true
    }
)
