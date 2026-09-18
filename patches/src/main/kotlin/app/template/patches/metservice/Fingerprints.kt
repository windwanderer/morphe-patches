package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object OnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/metservice/kryten/ui/location/LocationPageActivity;",
    name = "onResume",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC)
)
