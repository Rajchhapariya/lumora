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
package io.github.rajchhapariya.lumora

import android.app.Application
import io.github.rajchhapariya.lumora.core.logging.DebugLogTree
import io.github.rajchhapariya.lumora.core.logging.ReleaseLogTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class
 */
@HiltAndroidApp
class LumoraApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugLogTree())
        } else {
            Timber.plant(ReleaseLogTree())
        }
    }
}
