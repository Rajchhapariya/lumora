/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
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
package io.github.rajchhapariya.lumora.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.rajchhapariya.lumora.core.blocking.time.SystemTimeProvider
import io.github.rajchhapariya.lumora.core.blocking.time.TimeProvider
import io.github.rajchhapariya.lumora.core.data.database.LumoraDatabase
import io.github.rajchhapariya.lumora.core.data.database.dao.SessionSegmentDao
import io.github.rajchhapariya.lumora.core.data.database.dao.UserSettingsDao
import io.github.rajchhapariya.lumora.core.data.repository.BlockingConfigRepositoryImpl
import io.github.rajchhapariya.lumora.core.data.repository.SessionSegmentStoreImpl
import io.github.rajchhapariya.lumora.core.data.repository.SessionTrackerImpl
import io.github.rajchhapariya.lumora.core.data.repository.UserSettingsStoreImpl
import io.github.rajchhapariya.lumora.core.repository.BlockingConfigRepository
import io.github.rajchhapariya.lumora.core.repository.SessionSegmentStore
import io.github.rajchhapariya.lumora.core.repository.SessionTracker
import io.github.rajchhapariya.lumora.core.repository.UserSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataDiModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LumoraDatabase =
        Room.databaseBuilder(context, LumoraDatabase::class.java, "data.db")
            .fallbackToDestructiveMigration(true) // Not recommended but for now it shouldn't matter
            .fallbackToDestructiveMigrationOnDowngrade(true).addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Insert default user settings row
                        db.execSQL(
                            """
                        INSERT INTO user_settings (id, active_block_option, daily_limit,
                                                   interval_allowance, interval_length,
                                                   interval_window_start_at, interval_usage,
                                                   timer_overlay_enabled,
                                                   timer_overlay_x, timer_overlay_y, waiting_for_accessibility,
                                                   has_seen_accessibility_explainer, pause_until_at,
                                                   first_launch_at, has_seen_review_prompt,
                                                   review_prompt_attempt_count, review_prompt_last_attempt_at,
                                                   pause_duration_millis, except_reels_sent_by_dm,
                                                   theme_mode, dynamic_color_enabled)
                        VALUES (1, 'NothingSelected', 0, 0, 0, 0, 0, 1, 0, 100, 0, 0, 0,
                                CAST(strftime('%s','now') AS INTEGER) * 1000, 0, 0, 0, 300000, 0,
                                'SYSTEM', 0)
                        """,
                        )
                    }
                },
            ).build()

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: LumoraDatabase): UserSettingsDao = database.userSettingsDao()

    @Provides
    @Singleton
    fun provideUserSettingsStore(userSettingsDao: UserSettingsDao): UserSettingsStore =
        UserSettingsStoreImpl(userSettingsDao = userSettingsDao)

    @Provides
    @Singleton
    fun provideBlockingConfigRepository(userSettingsDao: UserSettingsDao, timeProvider: TimeProvider): BlockingConfigRepository =
        BlockingConfigRepositoryImpl(userSettingsDao = userSettingsDao, timeProvider = timeProvider)

    @Provides
    @Singleton
    fun provideSessionSegmentDao(database: LumoraDatabase): SessionSegmentDao = database.sessionSegmentDao()

    @Provides
    @Singleton
    fun provideSessionSegmentStore(timeProvider: TimeProvider, sessionSegmentDao: SessionSegmentDao): SessionSegmentStore =
        SessionSegmentStoreImpl(timeProvider = timeProvider, sessionSegmentDao = sessionSegmentDao)

    @Provides
    @Singleton
    fun provideSessionTracker(timeProvider: TimeProvider, sessionSegmentStore : SessionSegmentStore): SessionTracker = SessionTrackerImpl(
        sessionSegmentStore = sessionSegmentStore,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider
}
