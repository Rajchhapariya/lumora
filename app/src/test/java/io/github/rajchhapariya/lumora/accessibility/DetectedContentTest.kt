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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedContentTest {
    private val app = ResolvedBlockableApp(BlockableApp.SNAPCHAT, "com.snapchat.android")

    @Test
    fun `detected content uses the app's own block action`() {
        BlockableApp.entries.forEach { blockable ->
            val resolved = ResolvedBlockableApp(blockable, blockable.getPackageIds().first())
            assertEquals(resolved.getBlockAction(), DetectedBlockedContent(resolved, false).blockAction)
        }
    }

    @Test
    fun `suppression updates keep the same session but app changes do not`() {
        val content = DetectedBlockedContent(app, false)
        assertTrue(content.canContinueWith(content.copy(blockingSuppressed = true)))
        assertFalse(content.canContinueWith(content.copy(app = app.copy(packageId = "com.snapchat.android.beta"))))
    }

    @Test
    fun `dm exemption rules are configured on supported apps`() {
        val reelsRule = BlockableApp.REELS.getDmExemptionRule()
        assertNotNull(reelsRule)
        assertTrue(reelsRule!!.requiredViewIds.contains("reply_bar_edittext"))
        assertTrue(reelsRule.forbiddenViewIds.contains("suggested_title"))

        assertNull(BlockableApp.SHORTS.getDmExemptionRule())
        assertNull(BlockableApp.FACEBOOK.getDmExemptionRule())
    }
}
