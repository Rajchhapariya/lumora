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
package io.github.rajchhapariya.lumora.core.domain.model

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import io.github.rajchhapariya.lumora.core.model.BlockableApp
import io.github.rajchhapariya.lumora.core.model.ContentBlockAction
import io.github.rajchhapariya.lumora.core.model.DetectionNode
import io.github.rajchhapariya.lumora.core.model.ResolvedBlockableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlockActionTest {
    @Test
    fun `every app leaves blocked content with the Back action`() {
        for (app in BlockableApp.entries) {
            assertEquals(ContentBlockAction.PerformGlobalAction(GLOBAL_ACTION_BACK), app.getBlockAction())
        }
    }

    @Test
    fun `YouTube variants resolve to the Shorts rule and match a visible player`() {
        val packageId = "app.revanced.android.youtube"
        val matches = BlockableApp.entries.filter { it.resolvePackage(packageId) != null }
        assertEquals(listOf(BlockableApp.SHORTS), matches)
        val app = ResolvedBlockableApp(matches.single(), packageId)
        val player = DetectionNode(nodeId = 1, viewId = "$packageId:id/reel_player_page_container")
        assertTrue(app.matchesFastDetectionNode(player))
        assertFalse(app.matchesFastDetectionNode(player.copy(isVisible = false)))
        assertFalse(app.matchesFastDetectionNode(player.copy(viewId = "$packageId:id/watch_player")))
    }
}
