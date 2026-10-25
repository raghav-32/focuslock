package com.focuslock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetaDao {
    @Query("SELECT * FROM app_meta ORDER BY label COLLATE NOCASE")
    suspend fun getAll(): List<AppMetaEntity>

    @Upsert
    suspend fun upsertAll(items: List<AppMetaEntity>)

    @Query("DELETE FROM app_meta")
    suspend fun clear()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Upsert
    suspend fun upsert(item: BlockedAppEntity)

    @Upsert
    suspend fun upsertAll(items: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps WHERE packageName IN (:packages)")
    suspend fun delete(packages: List<String>)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
}

@Dao
interface FeedRuleDao {
    @Query("SELECT * FROM feed_rules ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FeedRuleEntity>>

    @Query("SELECT * FROM feed_rules")
    suspend fun getAll(): List<FeedRuleEntity>

    @Query("SELECT COUNT(*) FROM feed_rules")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: FeedRuleEntity)

    @Upsert
    suspend fun upsertAll(items: List<FeedRuleEntity>)

    @Query("DELETE FROM feed_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM feed_rules")
    suspend fun deleteAll()
}

@Dao
abstract class GroupDao {
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE")
    abstract fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_members")
    abstract fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM app_groups")
    abstract suspend fun getGroups(): List<GroupEntity>

    @Query("SELECT * FROM group_members")
    abstract suspend fun getMembers(): List<GroupMemberEntity>

    @Insert
    abstract suspend fun insertGroup(group: GroupEntity): Long

    @Query("UPDATE app_groups SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM app_groups WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM app_groups")
    abstract suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :id")
    abstract suspend fun clearMembers(id: Long)

    @Transaction
    open suspend fun replaceMembers(id: Long, packages: Collection<String>) {
        clearMembers(id)
        addMembers(packages.map { GroupMemberEntity(id, it) })
    }
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY startMinute")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY startMinute")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun get(id: Long): ScheduleEntity?

    @Upsert
    suspend fun upsert(item: ScheduleEntity): Long

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE state = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE state = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActive(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE startWall >= :since ORDER BY startWall DESC")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(item: FocusSessionEntity): Long

    /** Compare-and-set so exactly one caller wins the ACTIVE -> final transition. */
    @Query("UPDATE focus_sessions SET state = :newState, endedWall = :endedWall WHERE id = :id AND state = 'ACTIVE'")
    suspend fun finish(id: Long, newState: String, endedWall: Long): Int

    @Query("DELETE FROM focus_sessions WHERE state != 'ACTIVE'")
    suspend fun clearFinished()
}

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(item: BlockEventEntity)

    @Insert
    suspend fun insertAll(items: List<BlockEventEntity>)

    @Query("SELECT * FROM block_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<BlockEventEntity>>

    @Query("SELECT * FROM block_events ORDER BY timestamp")
    suspend fun getAll(): List<BlockEventEntity>

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}

@Dao
interface DailyUsageDao {
    @Upsert
    suspend fun upsertAll(items: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage WHERE epochDay BETWEEN :from AND :to")
    fun observeBetween(from: Long, to: Long): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage ORDER BY epochDay")
    suspend fun getAll(): List<DailyUsageEntity>

    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}
