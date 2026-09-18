package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_EXAMPLE = Compatibility(
        name = "MetService", // App name as it appears in the Android launcher.
        packageName = "com.metservice.kryten",
        apkFileType = ApkFileType.APK, // Preferred or recommended file type.
        appIconColor = 0xFF0045, // Icon color in Morphe Manager. Usually the same color as the icon background.
        targets = listOf(
            // "version = null" means the patch works with the latest app target
            // and is expected to work with all future app targets.
            //
            // It is highly recommended to always include the exact app version developed
            // your patches for or the last version you have confirmed as 100% working.
            //
            // It is highly preferred to use app versions that are
            // available on apkmirror.com or uptodown.com, as Morphe web-search will
            // redirect users to these sites. If an app version is not available
            // on ApkMirror or UpToDown, then the user will be sent to Google search.
            AppTarget(
                version = "2.33.0"
            )
        )
    )
}
