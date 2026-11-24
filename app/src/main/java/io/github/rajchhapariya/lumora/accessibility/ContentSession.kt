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

import io.github.rajchhapariya.lumora.core.model.ContentBlockAction
import io.github.rajchhapariya.lumora.core.model.ResolvedBlockableApp

/** Information about blocked content detected on screen. */
internal data class DetectedBlockedContent(val app: ResolvedBlockableApp, val blockingSuppressed: Boolean) {
    /** What to do when the content must be blocked: the app's own action, such as Back. */
    val blockAction: ContentBlockAction
        get() = app.getBlockAction()

    /** Screen updates keep the session; changing the app starts a new one. */
    fun canContinueWith(next: DetectedBlockedContent): Boolean = app == next.app
}

/** Tracks a viewing session while the user is inside blocked content. */
internal class ContentSession(var content: DetectedBlockedContent, val startedAtMillis: Long) {
    val app: ResolvedBlockableApp get() = content.app

    /** Whether blocking is temporarily skipped for this screen while viewing time is still counted. */
    val blockingSuppressed: Boolean get() = content.blockingSuppressed

    /** Prevents more than one usage record from being produced for this visit. */
    private var viewingFinished: Boolean = false

    /**
     * Ends this visit and returns its viewing period.
     * Later calls return `null` because a visit must be saved only once.
     */
    fun finish(nowMillis: Long): FinishedViewing? {
        if (viewingFinished) return null
        viewingFinished = true
        return FinishedViewing(app, startedAtMillis, maxOf(startedAtMillis, nowMillis))
    }
}

/** Completed viewing session ready to be saved to usage history. */
internal data class FinishedViewing(val app: ResolvedBlockableApp, val startedAtMillis: Long, val endedAtMillis: Long) {
    val durationMillis: Long get() = endedAtMillis - startedAtMillis
}
