package org.execbit.rpker

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal object GadgetbridgeInstaller {
    private const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
    private const val FILE_INSTALLER_ACTIVITY =
        "nodomain.freeyourgadget.gadgetbridge.activities.install.FileInstallerActivity"

    fun open(activity: Activity, rpkFile: File) {
        activity.startActivity(createIntent(activity, rpkFile))
    }

    internal fun createIntent(context: Context, rpkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            rpkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setClassName(GADGETBRIDGE_PACKAGE, FILE_INSTALLER_ACTIVITY)
            setDataAndType(uri, "application/zip")
            clipData = ClipData.newRawUri("RPK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
