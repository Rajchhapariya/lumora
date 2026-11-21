/*
 * Copyright (C) 2026 Rajchhapariya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.rajchhapariya.lumora.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import io.github.rajchhapariya.lumora.R
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.ui.MainActivity
import timber.log.Timber

/**
 * Foreground service with two jobs:
 *
 * 1. Raise the app's process priority, because some Android skins (ColorOS on Realme/OPPO, for
 *    example) freeze idle background processes, which silently stops the accessibility service.
 * 2. Watch the accessibility setting. If Lumora gets switched off, show a loud notification
 *    that opens the Accessibility settings, so blocking is never off without the user knowing.
 *
 * It keeps running even when the accessibility service is off, so it can raise that alert.
 * It only starts after the blocker has been set up once.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = refreshAlert()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        val notification = buildOngoingNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Never let a keep-alive failure take down the blocker itself.
            Timber.w(e, "Could not start the keep-alive foreground service")
            stopSelf()
            return START_NOT_STICKY
        }
        refreshAlert()
        return START_STICKY
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.keepalive_channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** The permanent notification. Its text follows the real state, so it never claims "active" while off. */
    private fun buildOngoingNotification(blockingOn: Boolean = isAccessibilityEnabled(this)): Notification {
        val target = if (blockingOn) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        val openTarget = PendingIntent.getActivity(
            this,
            0,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(if (blockingOn) R.string.keepalive_title else R.string.keepalive_off_title))
            .setContentText(getString(if (blockingOn) R.string.keepalive_text else R.string.keepalive_off_text))
            .setContentIntent(openTarget)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /** Shows the "blocking is off" alert while the accessibility service is disabled. */
    private fun refreshAlert() {
        val manager = getSystemService(NotificationManager::class.java)
        val blockingOn = isAccessibilityEnabled(this)
        // Keep the permanent notification honest as well as the alert.
        manager.notify(NOTIFICATION_ID, buildOngoingNotification(blockingOn))
        if (blockingOn) {
            manager.cancel(ALERT_ID)
            return
        }
        val openSettings = PendingIntent.getActivity(
            this,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alert = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(getString(R.string.alert_text))
            .setContentIntent(openSettings)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        manager.notify(ALERT_ID, alert)
    }

    companion object {
        private const val CHANNEL_ID = "lumora_keepalive"
        private const val ALERT_CHANNEL_ID = "lumora_alert"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_ID = 1002

        /** True if the Lumora accessibility service is switched on in system settings. */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.startsWith("${context.packageName}/") }
        }

        /** Starts the service, but only once the blocker has been set up so new users are not nagged. */
        fun startIfSetUp(context: Context) {
            if (!ExtrasPrefs(context).everConnected) return
            start(context)
        }

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                Timber.w(e, "Keep-alive service could not be started")
            }
        }
    }
}
