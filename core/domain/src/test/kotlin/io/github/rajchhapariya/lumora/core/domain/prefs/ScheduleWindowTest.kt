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

import io.github.rajchhapariya.lumora.core.prefs.ExtrasPrefs.Companion.isWithinWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWindowTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `daytime window includes start and excludes end`() {
        assertTrue(isWithinWindow(at(9), at(9), at(17)))
        assertTrue(isWithinWindow(at(16, 59), at(9), at(17)))
        assertFalse(isWithinWindow(at(17), at(9), at(17)))
        assertFalse(isWithinWindow(at(8, 59), at(9), at(17)))
    }

    @Test
    fun `overnight window wraps past midnight`() {
        assertTrue(isWithinWindow(at(23), at(22), at(7)))
        assertTrue(isWithinWindow(at(3), at(22), at(7)))
        assertFalse(isWithinWindow(at(12), at(22), at(7)))
        assertFalse(isWithinWindow(at(7), at(22), at(7)))
    }

    @Test
    fun `equal start and end means all day`() {
        assertTrue(isWithinWindow(at(0), at(8), at(8)))
        assertTrue(isWithinWindow(at(13), at(8), at(8)))
    }
}
