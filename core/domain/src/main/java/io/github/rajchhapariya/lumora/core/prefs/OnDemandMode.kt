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
package io.github.rajchhapariya.lumora.core.prefs

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings

/**
 * On-demand mode: Lumora's accessibility service is switched on only while a supported app is in
 * front, and switched off everywhere else. Payment apps then see no accessibility service, and the
 * phone does no scanning while you use anything else.
 *
 * It needs two permissions that are granted once from a computer over USB debugging:
 * WRITE_SECURE_SETTINGS (to switch Lumora's own service on and off) and usage access (to see which
 * app is in front without the service). Without them the mode stays off.
 */
object OnDemandMode {

    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    /** True when the user turned the mode on and both permissions are present. */
    fun isActive(context: Context): Boolean = ExtrasPrefs(context).onDemandMode && hasPermissions(context)

    fun hasPermissions(context: Context): Boolean = hasSecureSettings(context) && hasUsageAccess(context)

    fun hasSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** True if [component] is currently switched on in the system accessibility list. */
    fun isServiceEnabled(context: Context, component: String): Boolean =
        isListed(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), component)

    /**
     * Switches Lumora's own accessibility service on or off. Other services in the list are kept.
     * Returns false if Android refused the change.
     */
    fun setServiceEnabled(context: Context, component: String, enabled: Boolean): Boolean = try {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val updated = withService(current, component, enabled)
        if (updated != current.orEmpty()) {
            if (enabled) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            if (!enabled && updated.isEmpty()) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }
        true
    } catch (_: SecurityException) {
        false
    }

    /** Whether [component] appears in a colon-separated accessibility service list. */
    fun isListed(list: String?, component: String): Boolean =
        list.orEmpty().split(':').any { it.equals(component, ignoreCase = true) }

    /** Returns [list] with [component] added or removed, leaving every other entry untouched. */
    fun withService(list: String?, component: String, enabled: Boolean): String {
        val others = list.orEmpty().split(':').filter { it.isNotBlank() && !it.equals(component, ignoreCase = true) }
        return (if (enabled) others + component else others).joinToString(":")
    }
}

/**
 * Works out which app is in front from the system's usage events, in the order they happened.
 * An app counts as in front from its "resumed" event until its own "paused" event.
 */
class ForegroundTracker {
    var packageName: String? = null
        private set

    fun onEvent(eventPackage: String, eventType: Int) {
        when (eventType) {
            EVENT_RESUMED -> packageName = eventPackage
            EVENT_PAUSED -> if (packageName == eventPackage) packageName = null
        }
    }

    fun reset() {
        packageName = null
    }

    companion object {
        /** UsageEvents.Event.ACTIVITY_RESUMED */
        const val EVENT_RESUMED = 1

        /** UsageEvents.Event.ACTIVITY_PAUSED */
        const val EVENT_PAUSED = 2
    }
}

/** What the watcher should do with the accessibility service after one look at the front app. */
enum class DemandAction { None, Enable, Disable }

/**
 * Decides when to switch the service. It switches on at once, but waits for [disableAfterMisses]
 * looks in a row before switching off, so a quick pass through a dialog does not flip it back and forth.
 */
class DemandDecider(private val disableAfterMisses: Int = 2) {
    private var misses = 0

    fun reset() {
        misses = 0
    }

    fun next(supportedInFront: Boolean, serviceOn: Boolean): DemandAction {
        if (supportedInFront) {
            misses = 0
            return if (serviceOn) DemandAction.None else DemandAction.Enable
        }
        if (!serviceOn) {
            misses = 0
            return DemandAction.None
        }
        misses++
        return if (misses >= disableAfterMisses) DemandAction.Disable else DemandAction.None
    }
}
