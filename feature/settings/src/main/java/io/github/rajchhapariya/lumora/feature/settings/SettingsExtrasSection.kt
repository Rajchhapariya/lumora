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
package io.github.rajchhapariya.lumora.feature.settings

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.core.prefs.OnDemandMode
import io.github.rajchhapariya.lumora.designsystem.component.SettingsActionRow
import io.github.rajchhapariya.lumora.designsystem.component.SettingsDivider
import io.github.rajchhapariya.lumora.designsystem.component.SettingsGroup
import io.github.rajchhapariya.lumora.designsystem.component.SettingsSectionLabel
import io.github.rajchhapariya.lumora.designsystem.component.SettingsSwitchRow
import io.github.rajchhapariya.lumora.designsystem.component.SettingsValueRow
import io.github.rajchhapariya.lumora.designsystem.theme.spacing
import java.util.Locale

/** What the host app does for the backup buttons, since the file picker lives in the activity. */
data class SettingsExtrasActions(val onExportBackup: () -> Unit = {}, val onRestoreBackup: () -> Unit = {})

/**
 * Extra settings sections: keep-running shortcuts, schedule, strict pause and backup.
 * Each section is its own card so the screen stays easy to scan.
 */
@Composable
internal fun SettingsExtrasSections(actions: SettingsExtrasActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { ExtrasPrefs(context) }
    var scheduleEnabled by remember { mutableStateOf(prefs.scheduleEnabled) }
    var startMinute by remember { mutableStateOf(prefs.scheduleStartMinute) }
    var endMinute by remember { mutableStateOf(prefs.scheduleEndMinute) }
    var strictPause by remember { mutableStateOf(prefs.strictPause) }
    var onDemand by remember { mutableStateOf(prefs.onDemandMode) }

    // Re-read everything when the values change from elsewhere, such as after restoring a backup.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            scheduleEnabled = prefs.scheduleEnabled
            startMinute = prefs.scheduleStartMinute
            endMinute = prefs.scheduleEndMinute
            strictPause = prefs.strictPause
            onDemand = prefs.onDemandMode
        }
        prefs.register(listener)
        onDispose { prefs.unregister(listener) }
    }

    Column(modifier = modifier) {
        SectionGap()
        SettingsSectionLabel(stringResource(R.string.settings_section_keep_running))
        LabelGap()
        SettingsGroup {
            SettingsActionRow(
                title = stringResource(R.string.settings_app_info_title),
                description = stringResource(R.string.settings_app_info_description),
                onClick = { context.openAppInfo() },
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_battery_title),
                description = stringResource(R.string.settings_battery_description),
                onClick = { context.openBatterySettings() },
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_accessibility_title),
                description = stringResource(R.string.settings_accessibility_description),
                onClick = { context.openAccessibilitySettings() },
            )
        }

        SectionGap()
        SettingsSectionLabel(stringResource(R.string.settings_section_schedule))
        LabelGap()
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_schedule_title),
                description = stringResource(R.string.settings_schedule_description),
                checked = scheduleEnabled,
                onCheckedChange = {
                    scheduleEnabled = it
                    prefs.scheduleEnabled = it
                },
            )
            if (scheduleEnabled) {
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_schedule_from),
                    value = formatMinute(context, startMinute),
                    onClick = {
                        context.pickTime(startMinute) {
                            startMinute = it
                            prefs.scheduleStartMinute = it
                        }
                    },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_schedule_to),
                    value = formatMinute(context, endMinute),
                    onClick = {
                        context.pickTime(endMinute) {
                            endMinute = it
                            prefs.scheduleEndMinute = it
                        }
                    },
                )
            }
        }

        SectionGap()
        SettingsSectionLabel(stringResource(R.string.settings_section_strict))
        LabelGap()
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_strict_title),
                description = stringResource(R.string.settings_strict_description),
                checked = strictPause,
                onCheckedChange = {
                    strictPause = it
                    prefs.strictPause = it
                },
            )
        }

        SectionGap()
        SettingsSectionLabel(stringResource(R.string.settings_section_ondemand))
        LabelGap()
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_ondemand_title),
                description = stringResource(R.string.settings_ondemand_description),
                checked = onDemand,
                onCheckedChange = { wanted ->
                    if (wanted && !OnDemandMode.hasPermissions(context)) {
                        Toast.makeText(context, R.string.settings_ondemand_needs_setup, Toast.LENGTH_LONG).show()
                    } else {
                        onDemand = wanted
                        prefs.onDemandMode = wanted
                    }
                },
            )
        }

        SectionGap()
        SettingsSectionLabel(stringResource(R.string.settings_section_backup))
        LabelGap()
        SettingsGroup {
            SettingsActionRow(
                title = stringResource(R.string.settings_backup_export_title),
                description = stringResource(R.string.settings_backup_export_description),
                onClick = actions.onExportBackup,
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_backup_restore_title),
                description = stringResource(R.string.settings_backup_restore_description),
                onClick = actions.onRestoreBackup,
            )
        }
    }
}

@Composable
private fun SectionGap() = Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

@Composable
private fun LabelGap() = Spacer(modifier = Modifier.height(MaterialTheme.spacing.small + 2.dp))

private fun formatMinute(context: Context, minute: Int): String {
    val hour = minute / 60
    val min = minute % 60
    return if (DateFormat.is24HourFormat(context)) {
        String.format(Locale.getDefault(), "%02d:%02d", hour, min)
    } else {
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        String.format(Locale.getDefault(), "%d:%02d %s", hour12, min, if (hour < 12) "AM" else "PM")
    }
}

private fun Context.pickTime(initialMinute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        this,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initialMinute / 60,
        initialMinute % 60,
        DateFormat.is24HourFormat(this),
    ).show()
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/** The system page for this app. On Realme/ColorOS it holds Auto launch and Battery usage. */
private fun Context.openAppInfo() {
    startSafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

/** The system list of apps exempt from battery optimisation, with the app info page as fallback. */
private fun Context.openBatterySettings() {
    if (!startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppInfo()
}

private fun Context.openAccessibilitySettings() {
    startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
