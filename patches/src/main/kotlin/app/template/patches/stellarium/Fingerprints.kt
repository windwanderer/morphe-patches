package app.template.patches.stellarium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * License check → always report "license OK" to the native engine.
 *
 * Search: Log.i("Stellarium", "Check license") ("Check License" in some releases,
 * so the string is compared case-insensitively).
 *
 * The license SDK itself (com.a.a.a.a.*, an obfuscated copy of Google's Play
 * licensing library) is fully obfuscated and changes every release, so the
 * method is located by its stable log message instead.
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
 * No definingClass/name (the main activity is stable, but the enclosing method
 * has changed before): located by the unique log message plus the
 * Log.i(String, String) call structure.
 */
object StartCheckLicenceFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J"),
    accessFlags = listOf(AccessFlags.STATIC),
    filters = listOf(
        methodCall(smali = "Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I")
    ),
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
