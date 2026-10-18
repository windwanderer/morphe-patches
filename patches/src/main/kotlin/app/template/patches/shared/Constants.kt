package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_MetService = Compatibility(
        name = "MetService", // App name as it appears in the Android launcher.
        packageName = "com.metservice.kryten",
        apkFileType = ApkFileType.APK, // Preferred or recommended file type.
        appIconColor = 0xFF0045, // Icon color in Morphe Manager. Usually the same color as the icon background.
        targets = listOf(
            // "version = null" means the patch works with the latest app target
            // and is expected to work with all future app targets.
            //
            // It is highly recommended to always include the exact app version you used to develop
            // your patches so users have a version 100% confirmed as working.
            //
            // It is highly preferred to use app versions that are
            // available on apkmirror.com or uptodown.com, as Morphe web-search will
            // redirect users to these sites. If an app version is not available
            // on ApkMirror or UpToDown, then the user is sent to Google search. 
            AppTarget(
                version = "2.35.0"
            ), 
            AppTarget(
                version = "2.34.1"
            ), 
            AppTarget(
                version = "2.33.0"
            )
        )
    )

    val COMPATIBILITY_Stellarium = Compatibility(
        name = "Stellarium", // App name as it appears in the Android launcher.
        packageName = "com.noctuasoftware.stellarium_free",
        apkFileType = ApkFileType.APK, // Preferred or recommended file type.
        appIconColor = 0xFF0B1D3A, // Icon color in Morphe Manager. Usually the same color as the icon background.
        targets = listOf(
            // Versions confirmed working with the patch.
            AppTarget(
                version = "1.16.8"
            ),
            AppTarget(
                version = "1.16.7"
            ),
            AppTarget(
                version = "1.16.6"
            )
        )
    )
}
