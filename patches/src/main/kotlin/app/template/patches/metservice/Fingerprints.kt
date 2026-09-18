package app.template.patches.metservice

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

object AppStateTFingerprint : Fingerprint(
    definingClass = ":com/metservice/kryten/",
    name = "t",
    filters = listOf(
        string("premium_ad_free_enabled")
    )
)
