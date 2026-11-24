/*
 * Copyright (C) 2026 Rajchhapariya
 * Copyright (C) 2026 Scrolless
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
package io.github.rajchhapariya.lumora.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.lifecycleScope
import io.github.rajchhapariya.lumora.R
import io.github.rajchhapariya.lumora.accessibility.KeepAliveService
import io.github.rajchhapariya.lumora.accessibility.LumoraBlockAccessibilityService
import io.github.rajchhapariya.lumora.feature.settings.SettingsExtrasActions
import io.github.rajchhapariya.lumora.util.BackupManager
import kotlinx.coroutines.launch
import io.github.rajchhapariya.lumora.core.model.ThemeMode
import io.github.rajchhapariya.lumora.core.repository.UserSettingsStore
import io.github.rajchhapariya.lumora.designsystem.theme.LocalSharedTransitionScope
import io.github.rajchhapariya.lumora.designsystem.theme.LumoraTheme
import io.github.rajchhapariya.lumora.feature.home.HomeScreen
import io.github.rajchhapariya.lumora.feature.settings.SettingsScreen
import io.github.rajchhapariya.lumora.util.requestAppReview
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userSettingsStore: UserSettingsStore

    @Inject
    lateinit var backupManager: BackupManager

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runBackup { backupManager.export(uri) to R.string.backup_saved }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runBackup { backupManager.restore(uri) to R.string.backup_restored }
    }

    /** Android 13+ needs the user's OK before the "Blocking is OFF" alert can be shown. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Restart the watcher so it can show the alert right away if blocking is already off.
        if (granted) KeepAliveService.startIfSetUp(this)
    }

    private fun askForNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Runs a backup action off the main thread and tells the user how it went. */
    private fun runBackup(action: suspend () -> Pair<Boolean, Int>) {
        lifecycleScope.launch {
            val (ok, successMessage) = action()
            Toast.makeText(this@MainActivity, if (ok) successMessage else R.string.backup_failed, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Make sure the "blocking is off" watcher is running whenever the app is opened.
        KeepAliveService.startIfSetUp(this)
        askForNotificationPermissionIfNeeded()

        setContent {

            val appState: LumoraAppState = rememberLumoraAppState()
            val themeMode by userSettingsStore.getThemeMode().collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamicColorEnabled by userSettingsStore.getDynamicColorEnabled().collectAsStateWithLifecycle(false)

            LumoraTheme(
                darkTheme = themeMode.resolveDarkTheme(),
                dynamicColor = dynamicColorEnabled,
            ) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavDisplay(
                            appState.backStack,
                            onBack = { appState.navigateBack() },
                            entryProvider = entryProvider {
                                entry<LumoraRoute.Home> {
                                    HomeScreen(
                                        onNavigateToSettings = appState::navigateToSettings,
                                        accessibilityServiceClass = LumoraBlockAccessibilityService::class.java,
                                        onRequestAppReview = ::requestAppReview,
                                    )
                                }
                                entry<LumoraRoute.Settings> {
                                    SettingsScreen(
                                        onNavigateBack = appState::navigateBack,
                                        extrasActions = SettingsExtrasActions(
                                            onExportBackup = { exportBackup.launch(BACKUP_FILE_NAME) },
                                            onRestoreBackup = { restoreBackup.launch(arrayOf("application/json", "*/*")) },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val BACKUP_FILE_NAME = "lumora-backup.json"

/**
 * Resolves the persisted preference against the device setting.
 *
 * Must be composable because [ThemeMode.SYSTEM] reads the current configuration.
 */
@Composable
private fun ThemeMode.resolveDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
