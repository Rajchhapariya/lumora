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
package io.github.rajchhapariya.lumora.feature.home.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.rajchhapariya.lumora.feature.home.R
import kotlinx.coroutines.delay

/** Seconds the user has to wait before a strict-mode pause can be confirmed. */
internal const val STRICT_PAUSE_WAIT_SECONDS = 20

/**
 * Asks the user to wait before pausing blocking. The confirm button stays disabled during the
 * countdown, which gives the urge to scroll time to pass.
 */
@Composable
fun StrictPauseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(STRICT_PAUSE_WAIT_SECONDS) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.strict_pause_title)) },
        text = { Text(stringResource(R.string.strict_pause_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = secondsLeft == 0) {
                Text(
                    if (secondsLeft > 0) {
                        stringResource(R.string.strict_pause_wait, secondsLeft)
                    } else {
                        stringResource(R.string.strict_pause_confirm)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.strict_pause_cancel)) }
        },
    )
}
