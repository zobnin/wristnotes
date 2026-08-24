package org.execbit.rpker

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File

internal object GadgetbridgeBroadcastInstaller {
    private const val URI_PERMISSION_DURATION_MS = 60_000L
    private const val ACTION_INSTALL_APP =
        "nodomain.freeyourgadget.gadgetbridge.command.INSTALL_APP"
    private const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
    private const val GADGETBRIDGE_NIGHTLY_PACKAGE = "$GADGETBRIDGE_PACKAGE.nightly"
    private val gadgetbridgePackages = listOf(
        GADGETBRIDGE_PACKAGE,
        GADGETBRIDGE_NIGHTLY_PACKAGE,
    )
    private val permissionHandler by lazy { Handler(Looper.getMainLooper()) }
    private val pendingRevocations = mutableMapOf<Uri, Runnable>()

    fun install(context: Context, rpkFile: File, deviceAddress: String? = null) {
        installApp(context, rpkFileUri(context, rpkFile), deviceAddress)
    }

    fun installApp(context: Context, appUri: Uri, deviceAddress: String? = null) {
        val grantedPackages = grantReadPermission(context, appUri)
        try {
            context.sendBroadcast(createInstallAppIntent(appUri, deviceAddress))
        } catch (error: Exception) {
            revokeReadPermission(context, appUri, grantedPackages)
            throw error
        }
        schedulePermissionRevocation(context, appUri, grantedPackages)
    }

    internal fun createInstallAppIntent(
        appUri: Uri,
        deviceAddress: String? = null,
    ): Intent = Intent(ACTION_INSTALL_APP).apply {
        putExtra(Intent.EXTRA_STREAM, appUri)
        clipData = ClipData.newRawUri("app", appUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        deviceAddress?.let { putExtra("device", it) }
    }

    private fun grantReadPermission(context: Context, appUri: Uri): List<String> {
        val grantedPackages = mutableListOf<String>()
        try {
            gadgetbridgePackages
                .filter { context.packageManager.isPackageInstalled(it) }
                .forEach { packageName ->
                    context.grantUriPermission(
                        packageName,
                        appUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    grantedPackages += packageName
                }
        } catch (error: Exception) {
            revokeReadPermission(context, appUri, grantedPackages)
            throw error
        }
        return grantedPackages
    }

    private fun schedulePermissionRevocation(
        context: Context,
        appUri: Uri,
        grantedPackages: List<String>,
    ) {
        if (grantedPackages.isEmpty()) return

        val applicationContext = context.applicationContext
        lateinit var revocation: Runnable
        revocation = Runnable {
            revokeReadPermission(applicationContext, appUri, grantedPackages)
            synchronized(pendingRevocations) {
                if (pendingRevocations[appUri] === revocation) {
                    pendingRevocations.remove(appUri)
                }
            }
        }
        synchronized(pendingRevocations) {
            pendingRevocations.remove(appUri)?.let(permissionHandler::removeCallbacks)
            pendingRevocations[appUri] = revocation
        }
        permissionHandler.postDelayed(revocation, URI_PERMISSION_DURATION_MS)
    }

    private fun revokeReadPermission(
        context: Context,
        appUri: Uri,
        grantedPackages: List<String>,
    ) {
        grantedPackages.forEach { packageName ->
            runCatching {
                context.revokeUriPermission(
                    packageName,
                    appUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.isPackageInstalled(packageName: String): Boolean =
        try {
            getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
