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
package io.github.rajchhapariya.lumora.feature.home

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rajchhapariya.lumora.core.model.BlockOption
import io.github.rajchhapariya.lumora.core.model.BlockableApp
import io.github.rajchhapariya.lumora.core.model.BlockingSettings
import io.github.rajchhapariya.lumora.core.model.IntervalUsage
import io.github.rajchhapariya.lumora.core.model.SessionSegment
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.feature.home.dialogs.StrictPauseDialog
import io.github.rajchhapariya.lumora.designsystem.theme.LumoraTheme
import io.github.rajchhapariya.lumora.designsystem.theme.indicatorColor
import io.github.rajchhapariya.lumora.designsystem.theme.usageStatusFor
import io.github.rajchhapariya.lumora.designsystem.tooling.DevicePreviews
import io.github.rajchhapariya.lumora.feature.home.dialogs.AccessibilityExplainerBottomSheet
import io.github.rajchhapariya.lumora.feature.home.dialogs.AccessibilitySuccessBottomSheet
import io.github.rajchhapariya.lumora.feature.home.dialogs.AccessibilitySuccessBottomSheetPreview
import io.github.rajchhapariya.lumora.feature.home.dialogs.HelpDialog
import io.github.rajchhapariya.lumora.feature.home.dialogs.IntervalTimerDialog
import io.github.rajchhapariya.lumora.feature.home.dialogs.TimeLimitDialog
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import timber.log.Timber

private val DEFAULT_INTERVAL_BREAK_MILLIS = TimeUnit.MINUTES.toMillis(60)
private val DEFAULT_INTERVAL_ALLOWANCE_MILLIS = TimeUnit.MINUTES.toMillis(5)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    accessibilityServiceClass: Class<out AccessibilityService>? = null,
    onRequestAppReview: (Activity, (ReviewPromptResult) -> Unit) -> Unit = { _, onResult ->
        onResult(ReviewPromptResult.SkippedPermanent)
    },
    forceLegacyOverlay: Boolean = false,
    onForceLegacyOverlayChanged: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showTimeLimitDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showStrictPauseDialog by remember { mutableStateOf(false) }
    val extrasPrefs = remember(context) { ExtrasPrefs(context) }
    var debugBypassAccessibilityCheck by remember { mutableStateOf(false) }
    var showIntervalTimerDialog by remember { mutableStateOf(false) }
    var pendingIntervalBreak by remember { mutableLongStateOf(DEFAULT_INTERVAL_BREAK_MILLIS) }
    var pendingIntervalAllowance by remember { mutableLongStateOf(DEFAULT_INTERVAL_ALLOWANCE_MILLIS) }
    val pauseRemainingMillis = rememberPauseRemainingTime(uiState.pauseUntilMillis)
    val isPauseActive = pauseRemainingMillis > 0L

    val activity = context as? Activity
    val accessibility = rememberHomeAccessibilityState(
        context = context,
        uiState = uiState,
        accessibilityServiceClass = accessibilityServiceClass,
        viewModel = viewModel,
    )

    val hasLimitTimer = uiState.hasConfiguredGoal
    val limitProgressFraction = if (hasLimitTimer) {
        uiState.progress.coerceIn(0, 100) / 100f
    } else {
        0f
    }
    // A single blocking state drives the background accent, the status headline and the
    // hero chip, so those three can never disagree about what is going on.
    val isBlockingActive = resolveIsBlockingActive(uiState)
    val blockingState = resolveHomeBlockingState(
        blockOption = uiState.blockOption,
        isPauseActive = isPauseActive,
        isBlockingActive = isBlockingActive,
        hasConfiguredGoal = hasLimitTimer,
    )
    val usageStatus = usageStatusFor(progressPercent = uiState.progress, hasGoal = hasLimitTimer)
    val accentStatus = blockingState.tintStatus(usageStatus)
    val motionScheme = MaterialTheme.motionScheme

    val backgroundAccentColor by animateColorAsState(
        targetValue = accentStatus?.indicatorColor ?: Color.Transparent,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "backgroundAccentColor",
    )
    val backgroundAccentStrength by animateFloatAsState(
        targetValue = when (blockingState) {
            HomeBlockingState.PAUSED, HomeBlockingState.BLOCKING_ALL -> 1f
            HomeBlockingState.WITHIN_LIMIT, HomeBlockingState.LIMIT_REACHED -> limitProgressFraction
            HomeBlockingState.NO_GOAL, HomeBlockingState.LIMIT_UNCONFIGURED -> 0f
        },
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "backgroundAccentStrength",
    )

    HomeBackground(
        modifier = modifier.fillMaxSize(),
        accentColor = backgroundAccentColor,
        accentStrength = backgroundAccentStrength,
    ) {
        fun openIntervalConfig() {
            pendingIntervalBreak = uiState.settings.intervalLengthMillis.takeIf { it > 0L } ?: DEFAULT_INTERVAL_BREAK_MILLIS
            pendingIntervalAllowance = uiState.settings.intervalAllowanceMillis.takeIf { it > 0L } ?: DEFAULT_INTERVAL_ALLOWANCE_MILLIS
            showIntervalTimerDialog = true
        }

        HomeContent(
            modifier = modifier,
            uiState = uiState,
            onNavigateToSettings = onNavigateToSettings,
            onBlockOptionSelected = { blockOption ->
                val shouldBypass = BuildConfig.DEBUG && debugBypassAccessibilityCheck
                if (shouldBypass || context.isAccessibilityServiceEnabled(accessibilityServiceClass)) {
                    Timber.i("Block option click -> %s (debug bypass: %s)", blockOption, shouldBypass)
                    viewModel.onBlockOptionSelected(blockOption)
                } else {
                    Timber.w("Accessibility service not enabled. Showing explainer.")
                    accessibility.showExplainerPrompt()
                }
            },
            onConfigureDailyLimit = {
                val shouldBypass = BuildConfig.DEBUG && debugBypassAccessibilityCheck
                if (shouldBypass || context.isAccessibilityServiceEnabled(accessibilityServiceClass)) {
                    Timber.d("Open TimeLimitDialog (debug bypass: %s)", shouldBypass)
                    showTimeLimitDialog = true
                } else {
                    Timber.w("Accessibility service not enabled. Showing explainer (daily limit).")
                    accessibility.showExplainerPrompt()
                }
            },
            onHelpClicked = {
                Timber.d("Help clicked -> show HelpDialog")
                showHelpDialog = true
            },
            onIntervalTimerClick = {
                val shouldBypass = BuildConfig.DEBUG && debugBypassAccessibilityCheck
                if (shouldBypass || context.isAccessibilityServiceEnabled(accessibilityServiceClass)) {
                    Timber.i("Interval timer clicked -> current=%s", uiState.blockOption)
                    if (uiState.blockOption == BlockOption.IntervalTimer) {
                        viewModel.onBlockOptionSelected(BlockOption.NothingSelected)
                    } else if (uiState.settings.intervalLengthMillis == 0L || uiState.settings.intervalAllowanceMillis == 0L) {
                        openIntervalConfig()
                    } else {
                        viewModel.onBlockOptionSelected(
                            BlockOption.IntervalTimer,
                        )
                    }
                } else {
                    Timber.w("Accessibility service not enabled. Showing explainer (interval timer).")
                    accessibility.showExplainerPrompt()
                }
            },
            onIntervalTimerEdit = {
                val shouldBypass = BuildConfig.DEBUG && debugBypassAccessibilityCheck
                if (shouldBypass || context.isAccessibilityServiceEnabled(accessibilityServiceClass)) {
                    Timber.d("Interval timer edit requested")
                    openIntervalConfig()
                } else {
                    Timber.w("Accessibility service not enabled. Showing explainer (interval timer config).")
                    accessibility.showExplainerPrompt()
                }
            },
            onPauseToggle = { shouldPause ->

                val shouldBypass = BuildConfig.DEBUG && debugBypassAccessibilityCheck
                if (shouldBypass || context.isAccessibilityServiceEnabled(accessibilityServiceClass)) {
                    if (shouldPause && extrasPrefs.strictPause) {
                        Timber.i("Pause clicked in strict mode -> asking for confirmation")
                        showStrictPauseDialog = true
                    } else {
                        if (shouldPause) {
                            Timber.i("Pause clicked -> pausing blocking")
                        } else {
                            Timber.i("Pause clicked -> resuming blocking immediately")
                        }
                        viewModel.onPauseToggle(shouldPause)
                    }
                } else {
                    Timber.w("Accessibility service not enabled. Showing explainer (pause).")
                    accessibility.showExplainerPrompt()
                }
            },
            onDebugUsageChanged = { usageSegments ->
                viewModel.onDebugUsageSegmentsChanged(uiState.usageAnalytics.selectedDate, usageSegments)
            },
            onDebugUsageReset = {
                viewModel.onDebugResetUsage(uiState.usageAnalytics.selectedDate)
            },
            onUsageAnalyticsDateSelected = viewModel::onUsageAnalyticsDateSelected,
            onUsageAnalyticsTodaySelected = viewModel::onUsageAnalyticsTodaySelected,
            onAveragePeriodSelected = viewModel::onAveragePeriodSelected,
            forceLegacyOverlay = forceLegacyOverlay,
            onForceLegacyOverlayChanged = onForceLegacyOverlayChanged,
        )
    }

    if (showTimeLimitDialog) {
        TimeLimitDialog(
            onDismiss = { selectedSeconds ->
                Timber.d("TimeLimitDialog dismissed: selected=%d s", selectedSeconds)
                showTimeLimitDialog = false
                if (selectedSeconds > 0) {
                    Timber.i("Setting time limit to %d seconds", selectedSeconds)
                    viewModel.onTimeLimitChange(selectedSeconds * 1000)
                }
            },
        )
    }

    if (showIntervalTimerDialog) {
        IntervalTimerDialog(
            initialBreakMillis = pendingIntervalBreak,
            initialAllowanceMillis = pendingIntervalAllowance,
            onConfirm = { breakMillis, allowanceMillis ->
                Timber.i(
                    "Interval timer schedule saved: break=%d, allowance=%d",
                    breakMillis,
                    allowanceMillis,
                )
                showIntervalTimerDialog = false
                viewModel.onIntervalTimerConfigChange(breakMillis, allowanceMillis)
            },
            onDismiss = {
                Timber.d("Interval timer dialog dismissed")
                showIntervalTimerDialog = false
            },
        )
    }

    if (showStrictPauseDialog) {
        StrictPauseDialog(
            onConfirm = {
                showStrictPauseDialog = false
                viewModel.onPauseToggle(true)
            },
            onDismiss = { showStrictPauseDialog = false },
        )
    }

    if (showHelpDialog) {
        HelpDialog(
            onDismiss = {
                Timber.d("HelpDialog dismissed")
                showHelpDialog = false
            },
        )
    }

    if (accessibility.showExplainer) {
        AccessibilityExplainerBottomSheet(
            onDismiss = accessibility.dismissExplainer,
        )
    }

    if (accessibility.showSuccess) {
        AccessibilitySuccessBottomSheet(
            onDismiss = accessibility.dismissSuccess,
        )
    }

    LaunchedEffect(uiState.requestReview) {
        if (uiState.requestReview && activity != null) {
            Timber.i("Requesting in-app review")
            viewModel.onReviewRequestStarted()
            onRequestAppReview(activity) { result ->
                viewModel.onReviewPromptResult(result)
                viewModel.onReviewRequestHandled()
            }
        }
    }
}

@DevicePreviews
@Composable
fun HomeScreenPreview() {
    val mockState = HomeUiState(
        blockOption = BlockOption.DailyLimit,
        settings = BlockingSettings(dailyLimitMillis = TimeUnit.MINUTES.toMillis(60)),
        currentUsage = TimeUnit.MINUTES.toMillis(42),
        progress = 70,
        listSessionSegments = listOf(
            SessionSegment(BlockableApp.FACEBOOK, TimeUnit.MINUTES.toMillis(8), LocalDateTime.of(2026, 10, 2, 0, 55)),
            SessionSegment(BlockableApp.FACEBOOK_LITE, TimeUnit.MINUTES.toMillis(5), LocalDateTime.of(2026, 10, 2, 1, 0)),
            SessionSegment(BlockableApp.REELS, TimeUnit.MINUTES.toMillis(10), LocalDateTime.of(2026, 10, 2, 1, 2)),
            SessionSegment(BlockableApp.REELS, TimeUnit.MINUTES.toMillis(3), LocalDateTime.of(2026, 10, 2, 1, 2)),
            SessionSegment(BlockableApp.SHORTS, TimeUnit.MINUTES.toMillis(3), LocalDateTime.of(2026, 10, 2, 1, 2)),
        ),
    )

    LumoraTheme {
        HomeBackground(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                uiState = mockState,
                onBlockOptionSelected = {},
                onConfigureDailyLimit = {},
                onHelpClicked = {},
                onIntervalTimerClick = {},
                onIntervalTimerEdit = {},
                onPauseToggle = { _ -> },
            )
        }
    }
}

@Preview(name = "Block All Active")
@Composable
fun PreviewBlockAll() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(blockOption = BlockOption.BlockAll),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
    }
}

@Preview(name = "Nothing Selected")
@Composable
fun PreviewNothingSelected() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(blockOption = BlockOption.NothingSelected, currentUsage = 3590000L),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
    }
}

@Preview(name = "Interval Timer Selected")
@Composable
fun PreviewIntervalTimerSelected() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(
                blockOption = BlockOption.IntervalTimer,
                settings = BlockingSettings(
                    intervalAllowanceMillis = TimeUnit.MINUTES.toMillis(5),
                    intervalLengthMillis = TimeUnit.MINUTES.toMillis(60),
                ),
                intervalUsage = IntervalUsage(
                    startMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30),
                    usageMillis = TimeUnit.MINUTES.toMillis(3),
                ),
                currentUsage = TimeUnit.MINUTES.toMillis(42),
            ),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
    }
}

@Preview(name = "Interval Timer Active")
@Composable
fun PreviewIntervalTimer() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(
                blockOption = BlockOption.IntervalTimer,
                settings = BlockingSettings(
                    intervalAllowanceMillis = TimeUnit.MINUTES.toMillis(5),
                    intervalLengthMillis = TimeUnit.MINUTES.toMillis(60),
                ),
                intervalUsage = IntervalUsage(
                    startMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(45),
                    usageMillis = TimeUnit.MINUTES.toMillis(4),
                ),
                currentUsage = TimeUnit.MINUTES.toMillis(50),
            ),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
        IntervalTimerDialog(
            initialBreakMillis = TimeUnit.MINUTES.toMillis(60),
            initialAllowanceMillis = TimeUnit.MINUTES.toMillis(5),
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview(name = "Help Dialog")
@Composable
fun PreviewHelpDialog() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(blockOption = BlockOption.BlockAll),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
        HelpDialog { }
    }
}

@Preview(name = "Accessibility Explainer")
@Composable
fun PreviewAccessibilityExplainer() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(blockOption = BlockOption.NothingSelected),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
        AccessibilityExplainerBottomSheet { }
    }
}

@Preview(name = "Accessibility success dialog")
@Composable
fun PreviewAccessibilitySuccessDialog() {
    LumoraTheme {
        HomeContent(
            uiState = HomeUiState(blockOption = BlockOption.NothingSelected),
            onBlockOptionSelected = {},
            onConfigureDailyLimit = {},
            onHelpClicked = {},
            onIntervalTimerClick = {},
            onIntervalTimerEdit = {},
            onPauseToggle = { _ -> },
        )
        AccessibilitySuccessBottomSheetPreview()
    }
}
