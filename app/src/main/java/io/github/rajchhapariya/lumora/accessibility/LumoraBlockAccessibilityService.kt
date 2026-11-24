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
package io.github.rajchhapariya.lumora.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import io.github.rajchhapariya.lumora.core.blocking.BlockingManager
import io.github.rajchhapariya.lumora.core.model.BlockOption
import io.github.rajchhapariya.lumora.core.model.BlockableApp
import io.github.rajchhapariya.lumora.core.model.BlockingConfig
import io.github.rajchhapariya.lumora.core.model.BlockingResult
import io.github.rajchhapariya.lumora.core.model.ContentBlockAction
import io.github.rajchhapariya.lumora.core.model.ResolvedBlockableApp
import io.github.rajchhapariya.lumora.core.repository.BlockingConfigRepository
import io.github.rajchhapariya.lumora.core.repository.SessionTracker
import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs
import io.github.rajchhapariya.lumora.core.repository.UserSettingsStore
import io.github.rajchhapariya.lumora.ui.overlay.TimerOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Accessibility service that monitors and blocks access to distracting short-form video content
 * based on user-configured limits.
 *
 * Using Android's Accessibility framework, this service detects when the user enters specific apps
 * or screens (such as YouTube Shorts, or Instagram Reels) and enforces blocking policies
 * according to the active [BlockOption]:
 * - [BlockOption.BlockAll]: Immediately blocks all detected content.
 * - [BlockOption.DailyLimit]: Allows usage up to a configured daily time limit.
 * - [BlockOption.IntervalTimer]: Allows usage within scheduled intervals.
 * - [BlockOption.NothingSelected]: No blocking is performed.
 *
 * Blocking is enforced by performing an automatic Back navigation to leave the video screen.
 *
 * The service tracks active viewing time, displays an optional timer overlay, and saves completed
 * sessions to the user's usage history.
 *
 * Permissions Required:
 * - Accessibility service permission must be granted by the user.
 *
 * @see BlockingManager for blocking logic and evaluation
 * @see BlockOption for available blocking strategies
 * @see BlockableApp for supported apps
 * @see ContentScanner for screen and window scanning
 */
@SuppressLint("AccessibilityPolicy") // Accessibility APIs are required to enforce user-configured blocking policies.
@AndroidEntryPoint
class LumoraBlockAccessibilityService : AccessibilityService() {

    override fun onCreate() {
        super.onCreate()
        timerOverlayManager.attachServiceContext(this)
    }

    /** Main thread handler for running UI-related actions like overlays and navigation. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Coroutine scope for service operations. Uses SupervisorJob so child failures don't cancel the service. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Tracks daily and session usage time across monitored apps. */
    @Inject
    lateinit var sessionTracker: SessionTracker

    /** Evaluates blocking rules and decides when to block or allow content. */
    @Inject
    lateinit var blockingManager: BlockingManager

    /** Repository for active blocking configurations and rules. */
    @Inject
    lateinit var blockingConfigRepository: BlockingConfigRepository

    /** Store for user settings, pause durations, and app preferences. */
    @Inject
    lateinit var userSettingsStore: UserSettingsStore

    /** Manages the floating timer overlay that shows remaining or elapsed time. */
    @Inject
    lateinit var timerOverlayManager: TimerOverlayManager

    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }

    /** Every package Lumora can block in, used to ignore content updates from unrelated apps. */
    private val supportedPackages: Set<String> by lazy { BlockableApp.entries.flatMap { it.getPackageIds() }.toSet() }

    /** Local-only extras: blocking schedule, "stopped today" counter and setup flag. */
    private val extrasPrefs by lazy { ExtrasPrefs(this) }

    /** Currently active content session. */
    private var contentSession: ContentSession? = null

    /** Scans the window tree to find the supported apps and the blocked screens inside them. */
    private val contentScanner by lazy {
        ContentScanner(
            service = this,
            allowVideosSentByDm = { currentAllowVideosSentByDm },
            includeStories = { currentIncludeStories },
            currentActivity = { currentActivity },
        )
    }

    /** Active viewing session where usage time is currently running. */
    private val viewingSession: ContentSession?
        get() = contentSession

    /** Latest blocking mode chosen by the user, read without touching the database. */
    @Volatile
    private var currentBlockOption: BlockOption? = null

    /** Whether the user enabled the floating timer overlay in settings. */
    private var currentTimerOverlayEnabled: Boolean = false

    /** Whether Stories participate in usage tracking and blocking. */
    private var currentIncludeStories: Boolean = false

    /** Whether videos received in direct messages are exempt from blocking. */
    private var currentAllowVideosSentByDm: Boolean = false

    /** Timestamp until which blocking is temporarily paused. */
    @Volatile
    private var pauseUntilMillis: Long = 0L

    private fun isPauseActive(now: Long = System.currentTimeMillis()): Boolean = pauseUntilMillis > now

    /** True if blocking is currently skipped due to an active pause or DM exemption. */
    private val isBlockingSuppressed: Boolean
        get() = isPauseActive() || contentSession?.blockingSuppressed == true || !extrasPrefs.isBlockingScheduledNow()

    /** Currently tracked target app in the foreground, or null if unrelated. */
    private var currentForegroundBrainRotApp: ResolvedBlockableApp? = null

    /** Last activity reported by a screen-change event; ordinary view events leave it unchanged. */
    private var currentActivity: ComponentName? = null

    /** Periodically checks if the user exceeded their usage limit while watching blocked content. */
    private val videoCheckRunnable: Runnable = Runnable {

        if (!validateTrackedAppState("Periodic check")) {
            return@Runnable
        }

        if (contentSession == null) {
            Timber.v("Periodic check runnable executed but no longer processing content")
            return@Runnable
        }

        val session = viewingSession ?: return@Runnable
        val elapsed = System.currentTimeMillis() - session.startedAtMillis

        if (isBlockingSuppressed) {
            // While paused or suppressed, keep checking so blocking resumes soon after a pause ends.
            // The timer overlay has its own ticker, so this does not need to run every second.
            Timber.v(
                "Periodic check skipped: elapsed=%d ms, paused=%b, suppressed=%b",
                elapsed,
                isPauseActive(),
                session.blockingSuppressed,
            )
            mainHandler.postDelayed(videoCheckRunnable, SUPPRESSED_CHECK_INTERVAL_MILLIS)
        } else {
            Timber.v("Periodic check running: elapsed=%d ms", elapsed)
            serviceScope.launch {
                val action = blockingManager.onPeriodicCheck(elapsed)
                if (viewingSession !== session) return@launch
                when (action) {
                    is BlockingResult.BlockNow -> {
                        Timber.i("Periodic check: limit reached (elapsed=%d). Navigating back.", elapsed)
                        enforceBlocking(session)
                    }

                    is BlockingResult.CheckLater -> {
                        Timber.v("Periodic check: limit not reached, will check again later in %d ms", action.delayMillis)
                        mainHandler.postDelayed(videoCheckRunnable, action.delayMillis)
                    }

                    is BlockingResult.Continue -> {
                        Timber.v("Periodic check: limit not reached, scheduling next check")
                        mainHandler.postDelayed(videoCheckRunnable, 10000)
                    }
                }
            }
        }
    }

    /**
     * Called when the accessibility service connects and is ready to monitor apps.
     * Sets up package listeners and begins observing settings and blocking preferences.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("Accessibility service connected")

        // Keep the process alive on aggressive battery managers such as ColorOS, and watch
        // the accessibility setting so the user is alerted if blocking gets switched off.
        extrasPrefs.everConnected = true
        KeepAliveService.start(this)

        // Start with restricted configuration to save battery
        refreshServiceConfig()

        // The user may have just enabled accessibility in Android Settings; bring them back once.
        serviceScope.launch {
            val waitingForAccessibility = userSettingsStore.getWaitingForAccessibility().distinctUntilChanged()
            waitingForAccessibility.collect { waiting ->
                // If app is waiting for accessibility, bring it to foreground
                if (waiting) {
                    Timber.i("Bringing app to foreground")
                    bringAppToForeground()
                    userSettingsStore.setWaitingForAccessibility(false)
                } else {
                    Timber.i("Skipping bringing app to foreground")
                }
            }
        }

        serviceScope.launch {
            // Usage changes constantly; only the mode and its settings need a new handler.
            blockingConfigRepository.observeConfig()
                .map { it.activeOption to it.settings }
                .distinctUntilChanged()
                .collect { (option, settings) ->
                    Timber.d("Blocking option changed: %s", option)
                currentBlockOption = option
                    blockingManager.init(option, settings)
                    reconsiderVisibleContent()
                }
        }

        serviceScope.launch {
            userSettingsStore.getTimerOverlayEnabled().collect {
                currentTimerOverlayEnabled = it
                refreshDetectedContent()
                reconsiderVisibleContent()
            }
        }

        // Re-detect an open video when the DM preference changes, then apply the new decision.
        serviceScope.launch {
            userSettingsStore.getAllowVideosSentByDm().collect {
                currentAllowVideosSentByDm = it
                refreshDetectedContent()
                reconsiderVisibleContent()
            }
        }

        serviceScope.launch {
            userSettingsStore.getIncludeStories().collect {
                currentIncludeStories = it
                refreshDetectedContent()
                reconsiderVisibleContent()
            }
        }

        // Apply a pause immediately, even if the app has not sent a new screen event.
        serviceScope.launch {
            userSettingsStore.getPauseUntil().collect { newPauseUntil ->
                pauseUntilMillis = newPauseUntil
                reconsiderVisibleContent()
            }
        }
    }

    /**
     * Cleans up tracking and overlays when the user leaves a monitored app or turns off the screen.
     *
     * @param reason Diagnostic message for logs explaining why the session ended.
     */
    private fun handleTrackedAppExit(reason: String) {
        if (contentSession == null && currentForegroundBrainRotApp == null) {
            return
        }

        Timber.i("Handling tracked app exit: %s", reason)
        if (contentSession != null) {
            onBlockedContentExited()
        }

        if (currentForegroundBrainRotApp != null) {
            updateForegroundAppState(null)
        }
    }

    /** Ends tracking when the screen turns off or the tracked app is no longer visible. */
    private fun validateTrackedAppState(source: String): Boolean {
        if (!powerManager.isInteractive) {
            handleTrackedAppExit("$source - screen is off")
            return false
        }

        val trackedForegroundApp = currentForegroundBrainRotApp ?: return true
        if (contentScanner.isAppVisible(trackedForegroundApp)) {
            return true
        }

        handleTrackedAppExit("$source - tracked app package is not visible")
        return false
    }

    /**
     * Updates package filtering so we listen broadly while an app is open to detect exits,
     * or narrowly when idle to save battery.
     */
    private fun refreshServiceConfig() {
        updateServiceConfig(contentSession != null || currentForegroundBrainRotApp != null)
    }

    /**
     * Processes incoming accessibility events to detect when the user enters or exits blocked content.
     *
     * @param event The accessibility event emitted by Android.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!powerManager.isInteractive) {
            handleTrackedAppExit("screen is off while receiving accessibility event")
            return
        }

        // Content updates in other apps (animations, video, keyboards) say nothing about leaving a
        // supported app, but each one would otherwise trigger a full scan of every window.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.packageName?.toString() !in supportedPackages
        ) {
            return
        }

        // TYPE_WINDOW_STATE_CHANGED also triggers for dialogs, popups, and keyboards.
        // Only record components ending in "Activity" so transient UI does not overwrite the active screen.
        val className = event.className?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && className?.endsWith("Activity") == true) {
            currentActivity = event.packageName?.let { ComponentName(it.toString(), className) }
        }

        val scan = contentScanner.scan(
            event.packageName?.toString(),
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            contentSession?.app,
            currentForegroundBrainRotApp,
        )
        val userActiveApp = scan.foregroundApp
        updateForegroundAppState(userActiveApp)
        if (userActiveApp == null && contentSession == null) return
        if (!scan.rootAvailable) {
            validateTrackedAppState("Root node missing")
            return
        }
        val detectedContent = scan.content

        // No match means the user left the video, even if they stayed inside the same app.
        if (detectedContent != null) {
            onBlockedContentDetected(detectedContent)
        } else {
            if (contentSession != null) {
                Timber.v("Brain rot content no longer detected, triggering exit")
                onBlockedContentExited()
            }
        }
    }

    /** Records when the user opens or leaves a monitored app and adjusts tracking accordingly. */
    private fun updateForegroundAppState(nextApp: ResolvedBlockableApp?) {
        val previousApp = currentForegroundBrainRotApp
        if (previousApp == nextApp) return

        if (previousApp != null) {
            Timber.v("*** User appears to have left a brain rot app: %s (%s)", previousApp.app.name, previousApp.packageId)
            sessionTracker.onAppClose()
            // When exiting to an unmonitored screen (e.g. launcher or home), reset the saved activity
            if (nextApp == null && currentActivity?.packageName == previousApp.packageId) {
                currentActivity = null
            }
        }

        if (nextApp != null) {
            Timber.v("**** User appears to have entered a brain rot app: %s (%s)", nextApp.app.name, nextApp.packageId)
            sessionTracker.onAppOpen(nextApp.app)
        }

        currentForegroundBrainRotApp = nextApp
        refreshServiceConfig()
    }

    /** Called when the accessibility service is interrupted by the system. */
    override fun onInterrupt() = Unit

    /** Cleans up overlays, cancels handlers, and stops all background jobs when the service is destroyed. */
    override fun onDestroy() {
        super.onDestroy()
        Timber.d(
            "Service state at destroy: hasContentSession=%b, viewingApp=%s",
            contentSession != null, viewingSession?.app,
        )
        stopPeriodicCheck()
        // Save a viewing session that is still open. serviceScope is cancelled below, and a save
        // launched on it would be lost, so use a short-lived scope of its own.
        val finished = contentSession?.finish(System.currentTimeMillis())
        contentSession = null
        if (currentForegroundBrainRotApp != null) sessionTracker.onAppClose()
        currentForegroundBrainRotApp = null
        if (finished != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { saveViewing(finished) }
        }
        timerOverlayManager.cleanup()
        serviceScope.cancel()
    }

    /** Handles detected blocked content by updating the active session or starting a new one. */
    private fun onBlockedContentDetected(detectedContent: DetectedBlockedContent) {
        val activeSession = contentSession
        if (activeSession != null) {
            // Moving to another app ends the old session; screen updates inside the same app keep it.
            if (!activeSession.content.canContinueWith(detectedContent)) {
                onBlockedContentExited()
            } else {
                val suppressionChanged = activeSession.blockingSuppressed != detectedContent.blockingSuppressed
                activeSession.content = detectedContent
                if (suppressionChanged) reconsiderVisibleContent()
                return
            }
        }

        contentSession = ContentSession(detectedContent, System.currentTimeMillis())
        onBlockedContentEntered()
    }

    /**
     * Called when the user starts viewing blocked content.
     * Starts periodic limit checks and displays the timer overlay if enabled.
     */
    private fun onBlockedContentEntered() {
        val session = viewingSession ?: return
        Timber.d("Entered blocked content at %d (app=%s, paused=%b)", session.startedAtMillis, session.app, isPauseActive())

        // Expand service scope to detect when user leaves the app (e.g. to launcher)
        refreshServiceConfig()

        startPeriodicCheck()

        serviceScope.launch {
            // Load timer settings alongside usage so displaying the timer does not add another wait.
            val timerConfig = if (currentTimerOverlayEnabled) async { loadTimerConfig() } else null
            // Block All never needs today's usage, so skip the database read and block right away.
            val dailyUsageMillis = if (currentBlockOption == BlockOption.BlockAll) 0L else sessionTracker.getDailyUsage()
            // Share this usage reading with the timer, including during a pause or DM exemption.
            val shouldBlock = blockingManager.onEnterBlockedContent(dailyUsageMillis)
            // Reading the limit may take time. Ignore the answer if the user has already left.
            if (viewingSession !== session) return@launch
            if (!isBlockingSuppressed && shouldBlock) {
                Timber.i("Blocking on enter")
                enforceBlocking(session)
                return@launch
            }

            if (currentTimerOverlayEnabled) {
                // The saved timer preference may have finished loading during the blocking check.
                val config = if (timerConfig != null) timerConfig.await() else loadTimerConfig()
                if (config != null) {
                    showTimerOverlayIfEnabled(session, config, dailyUsageMillis)
                }
            }

            if (isBlockingSuppressed) {
                Timber.d(
                    "Skipping blocking check on enter, but tracking usage (paused=%b, suppressed=%b)",
                    isPauseActive(),
                    session.blockingSuppressed,
                )
            } else {
                Timber.d("Content allowed on enter, will monitor usage")
            }
        }
    }

    /** A timer settings failure should skip the timer without interrupting blocking. */
    private suspend fun loadTimerConfig(): BlockingConfig? = try {
        blockingConfigRepository.getConfig()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Could not load timer settings; skipping the timer overlay")
        null
    }

    /** Shows the timer using the settings and usage already loaded for this viewing session. */
    private fun showTimerOverlayIfEnabled(session: ContentSession, config: BlockingConfig, dailyUsageMillis: Long) {
        // Reads can finish after navigation. Only show the timer for the same viewing session.
        if (!currentTimerOverlayEnabled || viewingSession !== session) return

        Timber.v("Showing timer overlay")
        // This coroutine already runs on the main thread, so no Handler post is needed.
        if (config.activeOption == BlockOption.IntervalTimer) {
            timerOverlayManager.showInterval(
                sessionStartAt = session.startedAtMillis,
                intervalUsage = config.intervalUsage,
                intervalLengthMillis = config.settings.intervalLengthMillis,
            )
        } else {
            timerOverlayManager.showDaily(
                sessionStartAt = session.startedAtMillis,
                dailyUsageMillis = dailyUsageMillis,
            )
        }
    }

    /**
     * Called when the user leaves blocked content.
     * Stops usage checks, hides the timer, and records the completed viewing session.
     */
    private fun onBlockedContentExited() {
        val session = contentSession
        contentSession = null
        val finished = session?.finish(System.currentTimeMillis())
        stopPeriodicCheck()
        refreshServiceConfig()
        if (finished == null) return // This visit was already saved.
        timerOverlayManager.hide(finished.startedAtMillis, finished.endedAtMillis)
        serviceScope.launch { saveViewing(finished) }
    }

    /** Records viewing time in the session tracker and closes the session with the blocking manager. */
    private suspend fun saveViewing(finished: FinishedViewing) {
        if (finished.durationMillis >= MIN_TRACKED_DURATION_MILLIS) {
            sessionTracker.addToDailyUsage(finished.durationMillis, finished.app.app)
            blockingManager.onExitBlockedContent(finished.startedAtMillis, finished.endedAtMillis)
        } else {
            // Sub-second duration (such as content immediately blocked on enter) does not count as viewed content.
            blockingManager.onExitBlockedContent(finished.startedAtMillis, finished.startedAtMillis)
        }
    }

    /** Starts periodic checks to monitor usage limits while content is visible. */
    private fun startPeriodicCheck() {
        Timber.d("Starting periodic usage checks (every 1 second)")
        mainHandler.removeCallbacks(videoCheckRunnable)
        mainHandler.postDelayed(videoCheckRunnable, 1000)
    }

    /** Stops periodic usage checks when the user exits blocked content. */
    private fun stopPeriodicCheck() {
        mainHandler.removeCallbacks(videoCheckRunnable)
        Timber.d("Stopped periodic usage checks")
    }

    /** Enforces the active blocking policy by performing the app's Back navigation. */
    private suspend fun enforceBlocking(session: ContentSession) {
        if (viewingSession !== session || isBlockingSuppressed) return
        when (val action = session.content.blockAction) {
            is ContentBlockAction.PerformGlobalAction -> {
                extrasPrefs.recordAttempt()
                performGlobalAction(action.action)
            }
        }
    }

    /** Re-scans visible windows to update the current content session. */
    private fun refreshDetectedContent() {
        if (!validateTrackedAppState("Content refresh")) return
        val session = contentSession
        val app = session?.app ?: currentForegroundBrainRotApp
        if (app == null) {
            // Preferences can load before the first app event; discover what is already open.
            val scan = contentScanner.scan(eventPackage = null, windowsChanged = true, trackedApp = null, foregroundApp = null)
            updateForegroundAppState(scan.foregroundApp)
            scan.content?.let(::onBlockedContentDetected)
            return
        }
        val content = contentScanner.findVisibleBlockedContent(app)
        if (content != null) {
            onBlockedContentDetected(content)
        } else if (session != null) {
            onBlockedContentExited()
        }
    }

    /** Checks if the user's latest settings or allowances should block or unblock the current screen. */
    private fun reconsiderVisibleContent() {
        val current = contentSession ?: return
        serviceScope.launch {
            if (!isBlockingSuppressed &&
                blockingManager.onPeriodicCheck(System.currentTimeMillis() - current.startedAtMillis) == BlockingResult.BlockNow
            ) {
                enforceBlocking(current)
            }
        }
    }

    /**
     * Updates accessibility event filtering.
     *
     * @param listenToAll If true, listens to all packages to detect exits; if false, filters to target apps to save battery.
     */
    private fun updateServiceConfig(listenToAll: Boolean) {
        val info = serviceInfo ?: return

        info.notificationTimeout = DEFAULT_NOTIFICATION_TIMEOUT_MS

        if (listenToAll) {
            info.packageNames = null // Listen to all
            Timber.d("Expanded service configuration to listen to all packages")

            // Ensure windows are available for visibility-based exit checks.
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        } else {
            info.packageNames = BlockableApp.entries.flatMap { it.getPackageIds() }.toTypedArray()
            Timber.d("Restricted service configuration to target packages only")
        }
        serviceInfo = info
    }

    /** Reopens Lumora after the user grants accessibility permissions in system settings. */
    private fun bringAppToForeground() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            Timber.i("Successfully launched app to foreground")
        } catch (e: Exception) {
            Timber.e(e, "Failed to bring app to foreground")
        }
    }

    private companion object {
        /** Default event debounce timeout in ms (matches android:notificationTimeout in accessibility_service_config.xml). */
        const val DEFAULT_NOTIFICATION_TIMEOUT_MS = 50L

        /** How often to re-check while blocking is paused or suppressed. */
        const val SUPPRESSED_CHECK_INTERVAL_MILLIS = 2_000L

        /** Minimum viewing duration required to record usage. Sub-second visits (like immediate blocks) are ignored. */
        const val MIN_TRACKED_DURATION_MILLIS = 1_000L
    }
}
