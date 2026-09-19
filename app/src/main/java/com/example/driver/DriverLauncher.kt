package com.example.driver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

object DriverLauncher {

    const val DRIVER_PACKAGE_MARTO = "marto.rtl_tcp_andro"
    const val DRIVER_PACKAGE_TOUCH = "com.sdrtouch.rtlsdr"

    /**
     * Checks whether any compatible RTL-SDR driver package is installed on the device.
     */
    fun isDriverInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in listOf(DRIVER_PACKAGE_MARTO, DRIVER_PACKAGE_TOUCH)) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            } catch (_: Exception) {
            }
        }
        return false
    }

    /**
     * Checks if the app has permission to request package installs (API 26+).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                true
            }
        } else {
            true
        }
    }

    /**
     * Opens system unknown app sources settings for this app.
     */
    fun openInstallPermissionSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    /**
     * Extracts the bundled sdr-driver.apk from assets to cache and launches the system installer.
     */
    fun installDriverApk(context: Context): Pair<Boolean, String?> {
        return try {
            val apkFile = File(context.cacheDir, "sdr-driver.apk")

            // Always ensure the file is present and matches the asset length
            var needsCopy = !apkFile.exists() || apkFile.length() < 1024L
            if (!needsCopy) {
                try {
                    context.assets.open("sdr-driver.apk").use { input ->
                        val assetLength = input.available()
                        if (assetLength > 0 && apkFile.length() != assetLength.toLong()) {
                            needsCopy = true
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (needsCopy) {
                try {
                    context.assets.open("sdr-driver.apk").use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (assetEx: Exception) {
                    val fallbackFile = File("/app/media/sdr-driver.apk")
                    if (fallbackFile.exists() && fallbackFile.length() > 0L) {
                        fallbackFile.copyTo(apkFile, overwrite = true)
                    } else {
                        throw assetEx
                    }
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: e.message ?: "安装包启动失败")
        }
    }

    /**
     * Launches the Android RTL-SDR driver app via URI scheme `iqsrc://`.
     */
    fun startRtlDriver(
        context: Context,
        host: String = "127.0.0.1",
        port: Int = 1234,
        sampleRate: Int = 960000,
        freqHz: Long = 821237500L
    ): Boolean {
        val uriStr = "iqsrc://-a $host -p $port -s $sampleRate -f $freqHz -T 0"
        val intent = Intent(Intent.ACTION_VIEW, uriStr.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Broadcasts train alert intent to MacroDroid / Tasker / System listeners.
     */
    fun sendAlertBroadcast(
        context: Context,
        train: String,
        direction: String,
        speed: String,
        position: String,
        loco: String,
        locoCode: String,
        route: String,
        category: String
    ) {
        if (train == "----" || train.isEmpty()) return
        try {
            val intent = Intent("com.train.alert").apply {
                putExtra("train", train)
                putExtra("dir", direction)
                putExtra("speed", speed)
                putExtra("pos", position)
                putExtra("loco", loco)
                putExtra("code", locoCode)
                putExtra("route", route)
                putExtra("cat", category)
                // Set package if targeting MacroDroid specifically, or broad
                setPackage("com.arlosoft.macrodroid")
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {
            // Fallback unrestricted broadcast
            try {
                val broadIntent = Intent("com.train.alert").apply {
                    putExtra("train", train)
                    putExtra("dir", direction)
                    putExtra("speed", speed)
                    putExtra("pos", position)
                    putExtra("loco", loco)
                    putExtra("code", locoCode)
                    putExtra("route", route)
                    putExtra("cat", category)
                }
                context.sendBroadcast(broadIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens Application Details / Battery Optimization settings for the driver package marto.rtl_tcp_andro.
     */
    fun openDriverAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", "marto.rtl_tcp_andro", null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }
    }
}
