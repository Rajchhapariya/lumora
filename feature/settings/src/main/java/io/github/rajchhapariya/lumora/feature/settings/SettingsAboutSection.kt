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

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rajchhapariya.lumora.designsystem.component.SettingsGroup
import io.github.rajchhapariya.lumora.designsystem.component.SettingsSectionLabel
import io.github.rajchhapariya.lumora.designsystem.theme.spacing

/**
 * About card: name, version, licence, offline promise and the credit to the project this app
 * was built on, which the GPL requires.
 */
@Composable
internal fun SettingsAboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { context.versionName() }

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        SettingsSectionLabel(stringResource(R.string.settings_section_about))
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.small + 2.dp))
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.extraLarge,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.settings_about_name, version),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AboutLine(stringResource(R.string.settings_about_offline))
                AboutLine(stringResource(R.string.settings_about_license))
                AboutLine(stringResource(R.string.settings_about_credit))
            }
        }
    }
}

@Composable
private fun AboutLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("DEPRECATION")
private fun Context.versionName(): String = try {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}
