package app.morphe.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val BEJEWELED_COMPATIBILITY = Compatibility(
        // App name as it appears in the Android launcher.
        name = "Bejeweled Classic",
        packageName = "com.ea.gp.bej3",
        // This file on APKMirror is an APK Mirror bundle (APKM) containing base.apk + abi splits.
        apkFileType = ApkFileType.APKM,
        // Primary green of the Bejeweled Classic icon.
        appIconColor = 0x00A550,
        targets = listOf(
            // The exact version we developed and verified against.
            AppTarget(version = "4.0.0")
        )
    )
}
