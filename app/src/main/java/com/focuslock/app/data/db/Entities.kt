package com.focuslock.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cache of installed-app metadata (label etc.) so lists render before PackageManager answers. */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val category: Int,
    val updatedAt: Long,
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean,
    val scope: String,
    val addedAt: Long,
)

@Entity(tableName = "feed_rules")
data class FeedRuleEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val name: String,
    val enabled: Boolean,
    val scope: String,
    /** Tokens joined with '|'. */
    val idTokens: String,
    val textTokens: String,
    val fallbackWholeApp: Boolean,
    val minVersion: Long,
    val maxVersion: Long,
)

// NOTE: "groups" is a reserved word in modern SQLite, hence the table name.
@Entity(tableName = "app_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val presetKey: String?,
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packageName")],
)
data class GroupMemberEntity(
    val groupId: Long,
    val packageName: String,
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    /** Comma separated package names. */
    val packages: String,
    /** Comma separated group ids. */
    val groupIds: String,
    /** Comma separated feed rule ids. */
    val feedRuleIds: String,
    val exceptions: String,
    val requireFocusSession: Boolean,
)

@Entity(tableName = "focus_sessions", indices = [Index("state"), Index("startWall")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startWall: Long,
    val startElapsed: Long,
    val startBoot: Int,
    val plannedMillis: Long,
    val strict: Boolean,
    val groupId: Long?,
    val state: String,
    val endedWall: Long?,
)

@Entity(tableName = "block_events", indices = [Index("timestamp")])
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val kind: String,
    val ruleId: String,
    val ruleLabel: String,
    val source: String,
    val action: String,
)

@Entity(tableName = "daily_usage", primaryKeys = ["epochDay", "packageName"])
data class DailyUsageEntity(
    val epochDay: Long,
    val packageName: String,
    val foregroundMillis: Long,
)
