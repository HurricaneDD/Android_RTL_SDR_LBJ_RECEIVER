package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class LbjKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()
        
        // Immediately start foreground in onCreate to prevent Android 8+ ANR/crash
        val initialNotification = buildNotification("SDR-LBJ 信号监听守候中", "后台常驻保活服务运行中")
        safeStartForeground(initialNotification)
        
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Exception) {}
            try {
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }

        val trainInfo = intent?.getStringExtra(EXTRA_TRAIN_INFO) ?: "SDR-LBJ 信号监听守候中"
        val statusInfo = intent?.getStringExtra(EXTRA_STATUS_INFO) ?: "后台常驻保活服务运行中"

        val notification = buildNotification(trainInfo, statusInfo)
        
        // Update the active foreground notification
        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}

        return START_STICKY
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_lbj_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SDR-LBJ 后台监听常驻服务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持 RTL-SDR 信号流与解调核心在后台持续运行与告警"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                notificationManager?.createNotificationChannel(channel)
            } catch (_: Exception) {}
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SDR-LBJ::KeepAliveWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hours max
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        isServiceRunning = false
        releaseWakeLock()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        try {
            val nm = notificationManager ?: (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "lbj_keepalive_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_TRAIN_INFO = "extra_train_info"
        const val EXTRA_STATUS_INFO = "extra_status_info"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context, trainInfo: String = "SDR-LBJ 信号监听守候中", statusInfo: String = "已启用后台常驻保活") {
            try {
                val intent = Intent(context, LbjKeepAliveService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TRAIN_INFO, trainInfo)
                    putExtra(EXTRA_STATUS_INFO, statusInfo)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun update(context: Context, trainInfo: String, statusInfo: String) {
            try {
                if (isServiceRunning) {
                    // Update via NotificationManager directly to prevent Android 12+ Background Service Start Exceptions
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (manager != null) {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setContentTitle(trainInfo)
                            .setContentText(statusInfo)
                            .setSmallIcon(R.drawable.ic_lbj_notification)
                            .setContentIntent(pendingIntent)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setCategory(NotificationCompat.CATEGORY_SERVICE)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .build()
                        manager.notify(NOTIFICATION_ID, notification)
                        return
                    }
                }
                // Fallback if not running yet
                start(context, trainInfo, statusInfo)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            isServiceRunning = false
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}

            try {
                val intent = Intent(context, LbjKeepAliveService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}

            try {
                context.stopService(Intent(context, LbjKeepAliveService::class.java))
            } catch (_: Exception) {}
        }
    }
}
