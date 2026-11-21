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
package io.github.rajchhapariya.lumora.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.rajchhapariya.lumora.core.data.database.dao.SessionSegmentDao
import io.github.rajchhapariya.lumora.core.data.database.dao.UserSettingsDao
import io.github.rajchhapariya.lumora.core.data.database.model.SessionSegmentEntity
import io.github.rajchhapariya.lumora.core.data.database.model.UserSettingsEntity

/**
 * The [RoomDatabase]
 */
@Database(
    entities = [
        UserSettingsEntity::class,
        SessionSegmentEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LocalDateTypeConverters::class, BlockableAppTypeConverters::class, LocalDateTimeTypeConverters::class)
abstract class LumoraDatabase : RoomDatabase() {
    abstract fun userSettingsDao(): UserSettingsDao

    abstract fun sessionSegmentDao(): SessionSegmentDao
}
