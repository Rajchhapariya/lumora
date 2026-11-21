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

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalTime

/**
 * Small local-only settings added on top of the main database: blocking schedule, strict pause,
 * the "stopped today" counter and a flag that remembers the blocker was set up once.
 *
 * Everything lives in one private SharedPreferences file on the phone and is never sent anywhere.
 */
class ExtrasPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** When true, blocking only applies between [scheduleStartMinute] and [scheduleEndMinute]. */
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    /** Minutes after midnight when blocking starts. */
    var scheduleStartMinute: Int
        get() = prefs.getInt(KEY_SCHEDULE_START, DEFAULT_START_MINUTE)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_START, value.coerceIn(0, MINUTES_PER_DAY - 1)).apply()

    /** Minutes after midnight when blocking ends. */
    var scheduleEndMinute: Int
        get() = prefs.getInt(KEY_SCHEDULE_END, DEFAULT_END_MINUTE)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_END, value.coerceIn(0, MINUTES_PER_DAY - 1)).apply()

    /** When true, tapping Pause first asks the user to wait and confirm. */
    var strictPause: Boolean
        get() = prefs.getBoolean(KEY_STRICT_PAUSE, false)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_PAUSE, value).apply()

    /** True once the accessibility service has connected at least once. */
    var everConnected: Boolean
        get() = prefs.getBoolean(KEY_EVER_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_EVER_CONNECTED, value).apply()

    /** How many times blocking stopped the user today. Resets on a new day. */
    fun attemptsToday(today: LocalDate = LocalDate.now()): Int {
        return if (prefs.getString(KEY_ATTEMPTS_DATE, null) == today.toString()) {
            prefs.getInt(KEY_ATTEMPTS_COUNT, 0)
        } else {
            0
        }
    }

    /** Counts one more stop for today. */
    @Synchronized
    fun recordAttempt(today: LocalDate = LocalDate.now()) {
        val count = attemptsToday(today) + 1
        prefs.edit()
            .putString(KEY_ATTEMPTS_DATE, today.toString())
            .putInt(KEY_ATTEMPTS_COUNT, count)
            .apply()
    }

    /** True if blocking should apply at [now]. Always true while the schedule is off. */
    fun isBlockingScheduledNow(now: LocalTime = LocalTime.now()): Boolean {
        if (!scheduleEnabled) return true
        return isWithinWindow(now.hour * 60 + now.minute, scheduleStartMinute, scheduleEndMinute)
    }

    /** Calls [listener] whenever any value in this file changes. Keep a reference to it. */
    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** The values worth saving in a backup file. The today-counter and setup flag are left out. */
    fun exportValues(): Map<String, Any> = mapOf(
        KEY_SCHEDULE_ENABLED to scheduleEnabled,
        KEY_SCHEDULE_START to scheduleStartMinute,
        KEY_SCHEDULE_END to scheduleEndMinute,
        KEY_STRICT_PAUSE to strictPause,
    )

    /** Restores values produced by [exportValues]. Unknown or wrongly typed entries are ignored. */
    fun importValues(values: Map<String, Any?>) {
        (values[KEY_SCHEDULE_ENABLED] as? Boolean)?.let { scheduleEnabled = it }
        (values[KEY_SCHEDULE_START] as? Number)?.let { scheduleStartMinute = it.toInt() }
        (values[KEY_SCHEDULE_END] as? Number)?.let { scheduleEndMinute = it.toInt() }
        (values[KEY_STRICT_PAUSE] as? Boolean)?.let { strictPause = it }
    }

    companion object {
        const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        const val KEY_SCHEDULE_START = "schedule_start_minute"
        const val KEY_SCHEDULE_END = "schedule_end_minute"
        const val KEY_STRICT_PAUSE = "strict_pause"
        private const val KEY_EVER_CONNECTED = "ever_connected"
        private const val KEY_ATTEMPTS_DATE = "attempts_date"
        private const val KEY_ATTEMPTS_COUNT = "attempts_count"
        private const val FILE_NAME = "lumora_extras"
        private const val MINUTES_PER_DAY = 24 * 60
        private const val DEFAULT_START_MINUTE = 9 * 60
        private const val DEFAULT_END_MINUTE = 21 * 60

        /**
         * Whether [minute] falls inside the window from [start] to [end] (end excluded).
         * A window that ends before it starts runs overnight, and equal times mean all day.
         */
        fun isWithinWindow(minute: Int, start: Int, end: Int): Boolean = when {
            start == end -> true
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }
    }
}
