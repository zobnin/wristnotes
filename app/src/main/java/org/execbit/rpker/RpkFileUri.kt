package org.execbit.rpker

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal fun rpkFileUri(context: Context, rpkFile: File): Uri =
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        rpkFile,
    )
