package com.focuslock.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        AppMetaEntity::class,
        BlockedAppEntity::class,
        FeedRuleEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ScheduleEntity::class,
        FocusSessionEntity::class,
        BlockEventEntity::class,
        DailyUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FocusLockDatabase : RoomDatabase() {
    abstract fun appMetaDao(): AppMetaDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun feedRuleDao(): FeedRuleDao
    abstract fun groupDao(): GroupDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        const val NAME = "focuslock.db"

        fun build(context: Context): FocusLockDatabase =
            Room.databaseBuilder(context.applicationContext, FocusLockDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Schema version 1 is the first release, so there are no migrations yet. When the schema changes:
 *  1. bump `version` in @Database,
 *  2. add a Migration(from, to) here with explicit SQL,
 *  3. commit the generated schema JSON (app/schemas/) and add a MigrationTestHelper test.
 * We intentionally do NOT use fallbackToDestructiveMigration(): user rules must never be wiped silently.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
