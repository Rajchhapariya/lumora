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
package io.github.rajchhapariya.lumora.util

import android.content.Context
import android.net.Uri
import io.github.rajchhapariya.lumora.core.model.BlockOption
import io.github.rajchhapariya.lumora.core.model.BlockableApp
import io.github.rajchhapariya.lumora.core.model.SessionSegment
import io.github.rajchhapariya.lumora.core.model.ThemeMode
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.core.repository.BlockingConfigRepository
import io.github.rajchhapariya.lumora.core.repository.SessionSegmentStore
import io.github.rajchhapariya.lumora.core.repository.UserSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves and restores the user's settings and recent usage as one JSON file.
 *
 * The file is written and read only through the system file picker, so it stays wherever the
 * user puts it. Nothing is sent over the network.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockingConfigRepository: BlockingConfigRepository,
    private val userSettingsStore: UserSettingsStore,
    private val sessionSegmentStore: SessionSegmentStore,
) {

    /** Writes a backup to [uri]. Returns true on success. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = buildBackup().toString(2)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
        } catch (_: Exception) {
            false
        }
    }

    /** Reads a backup from [uri] and applies it. Returns true on success. */
    suspend fun restore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@withContext false
            applyBackup(JSONObject(text))
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun buildBackup(): JSONObject {
        val config = blockingConfigRepository.getConfig()
        val today = LocalDate.now()
        val segments = sessionSegmentStore
            .getListSessionSegments(today.minusDays(BACKUP_DAYS), today)
            .first()

        return JSONObject().apply {
            put("version", VERSION)
            put(
                "config",
                JSONObject().apply {
                    put("activeOption", config.activeOption.name)
                    put("dailyLimitMillis", config.settings.dailyLimitMillis)
                    put("intervalAllowanceMillis", config.settings.intervalAllowanceMillis)
                    put("intervalLengthMillis", config.settings.intervalLengthMillis)
                },
            )
            put(
                "settings",
                JSONObject().apply {
                    put("pauseDurationMillis", userSettingsStore.getPauseDuration().first())
                    put("timerOverlay", userSettingsStore.getTimerOverlayEnabled().first())
                    put("allowVideosSentByDm", userSettingsStore.getAllowVideosSentByDm().first())
                    put("includeStories", userSettingsStore.getIncludeStories().first())
                    put("themeMode", userSettingsStore.getThemeMode().first().name)
                    put("dynamicColor", userSettingsStore.getDynamicColorEnabled().first())
                },
            )
            put("extras", JSONObject(ExtrasPrefs(context).exportValues()))
            put(
                "usage",
                JSONArray().apply {
                    segments.forEach { segment ->
                        put(
                            JSONObject().apply {
                                put("app", segment.app.name)
                                put("durationMillis", segment.durationMillis)
                                put("start", segment.startDateTime.toString())
                            },
                        )
                    }
                },
            )
        }
    }

    private suspend fun applyBackup(root: JSONObject) {
        require(root.optInt("version", -1) in 1..VERSION) { "Unsupported backup" }

        // Read the usage list first. A damaged entry then fails the restore before anything is changed,
        // instead of leaving the settings half applied.
        val usageByDate = mutableMapOf<LocalDate, MutableList<SessionSegment>>()
        root.optJSONArray("usage")?.let { usage ->
            for (index in 0 until usage.length()) {
                val item = usage.getJSONObject(index)
                val app = BlockableApp.entries.firstOrNull { it.name == item.optString("app") } ?: continue
                val start = LocalDateTime.parse(item.getString("start"))
                val duration = item.getLong("durationMillis")
                require(duration >= 0L) { "Invalid usage entry" }
                usageByDate.getOrPut(start.toLocalDate()) { mutableListOf() } += SessionSegment(app, duration, start)
            }
        }

        root.optJSONObject("settings")?.let { settings ->
            if (settings.has("pauseDurationMillis")) userSettingsStore.setPauseDuration(settings.getLong("pauseDurationMillis"))
            if (settings.has("timerOverlay")) userSettingsStore.setTimerOverlayToggle(settings.getBoolean("timerOverlay"))
            if (settings.has("allowVideosSentByDm")) {
                userSettingsStore.setAllowVideosSentByDm(settings.getBoolean("allowVideosSentByDm"))
            }
            if (settings.has("includeStories")) userSettingsStore.setIncludeStories(settings.getBoolean("includeStories"))
            settings.optString("themeMode").takeIf { it.isNotEmpty() }
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?.let { userSettingsStore.setThemeMode(it) }
            if (settings.has("dynamicColor")) userSettingsStore.setDynamicColorEnabled(settings.getBoolean("dynamicColor"))
        }

        root.optJSONObject("config")?.let { config ->
            val daily = config.optLong("dailyLimitMillis", 0L)
            val allowance = config.optLong("intervalAllowanceMillis", 0L)
            val length = config.optLong("intervalLengthMillis", 0L)
            // Each configure call also selects its own mode, so the saved mode is applied last.
            if (daily > 0L) blockingConfigRepository.configureDailyLimit(daily)
            if (allowance > 0L && length > 0L) blockingConfigRepository.configureIntervalTimer(allowance, length)
            config.optString("activeOption").takeIf { it.isNotEmpty() }
                ?.let { name -> BlockOption.entries.firstOrNull { it.name == name } }
                ?.let { blockingConfigRepository.setActiveOption(it) }
        }

        root.optJSONObject("extras")?.let { extras ->
            ExtrasPrefs(context).importValues(extras.keys().asSequence().associateWith { extras.get(it) })
        }

        usageByDate.forEach { (date, list) -> sessionSegmentStore.replaceSessionSegmentsForDate(date, list) }
    }

    private companion object {
        const val VERSION = 1
        const val BACKUP_DAYS = 90L
    }
}
