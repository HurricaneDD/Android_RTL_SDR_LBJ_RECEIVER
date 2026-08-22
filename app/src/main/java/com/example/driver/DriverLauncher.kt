package com.example.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object DriverLauncher {

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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
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
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
