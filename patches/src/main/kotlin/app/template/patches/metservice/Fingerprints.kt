package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object AppStateTFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/AppState;",
    name = "t",
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC)
)
