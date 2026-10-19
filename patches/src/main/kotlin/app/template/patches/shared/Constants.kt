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
        apkFileType = ApkFileType.XAPK, // Preferred or recommended file type.
        appIconColor = 0x0B1D3A, // Icon color in Morphe Manager. Usually the same color as the icon background.
        targets = listOf(
            // The patch finds the native license checks by symbol name and verifies what
            // they do instead of matching their bytes, so it is expected to keep working
            // with releases after these. Both builds of the app are handled: 1.16.8 ships
            // the arm64-v8a library that 64 bit devices install, 1.16.5 only ships the
            // armeabi-v7a one, and the patch rewrites whichever library the XAPK holds.
            //
            // Releases after 1.16.8 are not listed as a target. Pinning the releases
            // that were tested keeps the manager's "Unsupported Version" dialog in the
            // way for the ones that were not, instead of promising support that was
            // never verified. Add each release that was tested as a target of its own.
            AppTarget(
                version = "1.16.8",
                versionCodes = mapOf(SupportedAbi.ARM64_V8A to 11205)
            ),
            AppTarget(
                version = "1.16.5",
                versionCodes = mapOf(SupportedAbi.ARMEABI_V7A to 11202)
            )
        )
    )
}
