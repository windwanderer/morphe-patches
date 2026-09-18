package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

object AppStateTFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/AppState;",
    name = "t",
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC),

    filters = listOf(
        string("premium_ad_free_enabled")
    )
)
