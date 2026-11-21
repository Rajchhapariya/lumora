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
package io.github.rajchhapariya.lumora.feature.home.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.designsystem.theme.spacing
import io.github.rajchhapariya.lumora.feature.home.R

/** One quiet line that tells the user how many times blocking stopped them today. */
@Composable
fun StoppedTodayLabel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { ExtrasPrefs(context) }
    var count by remember { mutableIntStateOf(prefs.attemptsToday()) }

    DisposableEffect(prefs) {
        // Keep the number live while the screen is open, e.g. after the user comes back from Reels.
        count = prefs.attemptsToday()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> count = prefs.attemptsToday() }
        prefs.register(listener)
        onDispose { prefs.unregister(listener) }
    }

    Text(
        text = if (count == 0) {
            stringResource(R.string.stopped_today_none)
        } else {
            pluralStringResource(R.plurals.stopped_today, count, count)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small),
    )
}
