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

import io.github.rajchhapariya.lumora.core.model.BlockableApp
import io.github.rajchhapariya.lumora.core.model.ResolvedBlockableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSessionTest {
    private val app = ResolvedBlockableApp(BlockableApp.SNAPCHAT, "com.snapchat.android")
    private val content = DetectedBlockedContent(app, false)

    @Test
    fun `finishing records the viewing time only once`() {
        val session = ContentSession(content, 1_000)

        assertEquals(5_000L, session.finish(6_000)!!.durationMillis)
        assertNull(session.finish(10_000))
    }

    @Test
    fun `a new visit starts at its own time`() {
        val first = ContentSession(content, 1_000)
        assertEquals(1_000L, first.finish(2_000)!!.durationMillis)

        val second = ContentSession(content, 20_000)
        assertEquals(20_000L, second.startedAtMillis)
        assertEquals(5_000L, second.finish(25_000)!!.durationMillis)
    }

    @Test
    fun `suppressed content still records viewing time`() {
        val session = ContentSession(content.copy(blockingSuppressed = true), 1_000)

        assertTrue(session.blockingSuppressed)
        assertEquals(5_000L, session.finish(6_000)!!.durationMillis)
    }

    @Test
    fun `screen updates keep the original viewing start`() {
        val session = ContentSession(content, 1_000)
        session.content = content.copy(blockingSuppressed = true)

        assertEquals(1_000L, session.startedAtMillis)
        assertEquals(4_000L, session.finish(5_000)!!.durationMillis)
    }

    @Test
    fun `clock moving backwards cannot record negative usage`() {
        val session = ContentSession(content, 10_000)

        assertEquals(0L, session.finish(1_000)!!.durationMillis)
    }
}
