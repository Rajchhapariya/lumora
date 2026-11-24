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
package io.github.rajchhapariya.lumora.core.domain.prefs

import io.github.rajchhapariya.lumora.core.prefs.DemandAction
import io.github.rajchhapariya.lumora.core.prefs.DemandDecider
import io.github.rajchhapariya.lumora.core.prefs.ForegroundTracker
import io.github.rajchhapariya.lumora.core.prefs.OnDemandMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDemandModeTest {
    private val lumora = "io.github.rajchhapariya.lumora/io.github.rajchhapariya.lumora.accessibility.Svc"
    private val other = "com.example.reader/com.example.reader.Svc"

    @Test
    fun `adding keeps other services and does not duplicate`() {
        assertEquals(lumora, OnDemandMode.withService(null, lumora, true))
        assertEquals("$other:$lumora", OnDemandMode.withService(other, lumora, true))
        assertEquals("$other:$lumora", OnDemandMode.withService("$other:$lumora", lumora, true))
    }

    @Test
    fun `removing keeps other services and handles empty lists`() {
        assertEquals(other, OnDemandMode.withService("$other:$lumora", lumora, false))
        assertEquals("", OnDemandMode.withService(lumora, lumora, false))
        assertEquals("", OnDemandMode.withService(null, lumora, false))
        assertEquals(other, OnDemandMode.withService("$other::", lumora, false))
    }

    @Test
    fun `isListed matches the whole component only`() {
        assertTrue(OnDemandMode.isListed("$other:$lumora", lumora))
        assertFalse(OnDemandMode.isListed(other, lumora))
        assertFalse(OnDemandMode.isListed(null, lumora))
    }

    @Test
    fun `foreground follows the latest resumed app and clears when it pauses`() {
        val tracker = ForegroundTracker()
        assertNull(tracker.packageName)
        tracker.onEvent("com.instagram.android", ForegroundTracker.EVENT_RESUMED)
        assertEquals("com.instagram.android", tracker.packageName)
        tracker.onEvent("com.android.launcher", ForegroundTracker.EVENT_RESUMED)
        // A late pause from the app that already left must not clear the new front app.
        tracker.onEvent("com.instagram.android", ForegroundTracker.EVENT_PAUSED)
        assertEquals("com.android.launcher", tracker.packageName)
        tracker.onEvent("com.android.launcher", ForegroundTracker.EVENT_PAUSED)
        assertNull(tracker.packageName)
    }

    @Test
    fun `service switches on at once for a supported app`() {
        val decider = DemandDecider()
        assertEquals(DemandAction.Enable, decider.next(supportedInFront = true, serviceOn = false))
        assertEquals(DemandAction.None, decider.next(supportedInFront = true, serviceOn = true))
    }

    @Test
    fun `service switches off only after repeated misses`() {
        val decider = DemandDecider(disableAfterMisses = 2)
        assertEquals(DemandAction.None, decider.next(supportedInFront = false, serviceOn = true))
        assertEquals(DemandAction.Disable, decider.next(supportedInFront = false, serviceOn = true))
    }

    @Test
    fun `a quick return to a supported app resets the miss count`() {
        val decider = DemandDecider(disableAfterMisses = 2)
        assertEquals(DemandAction.None, decider.next(false, true))
        assertEquals(DemandAction.None, decider.next(true, true))
        assertEquals(DemandAction.None, decider.next(false, true))
        assertEquals(DemandAction.Disable, decider.next(false, true))
    }

    @Test
    fun `nothing happens while off and no supported app is in front`() {
        val decider = DemandDecider()
        repeat(5) { assertEquals(DemandAction.None, decider.next(false, false)) }
    }
}
