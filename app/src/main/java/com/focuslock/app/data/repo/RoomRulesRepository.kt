package com.focuslock.app.data.repo

import androidx.room.withTransaction
import com.focuslock.app.data.db.FocusLockDatabase
import com.focuslock.app.data.db.GroupEntity
import com.focuslock.app.data.db.GroupMemberEntity
import com.focuslock.app.domain.AppGroup
import com.focuslock.app.domain.BackupGroup
import com.focuslock.app.domain.BackupSchedule
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.DefaultFeedRules
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.domain.RulesRepository
import com.focuslock.app.domain.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomRulesRepository(private val db: FocusLockDatabase) : RulesRepository {
    private val blockedDao = db.blockedAppDao()
    private val feedDao = db.feedRuleDao()
    private val groupDao = db.groupDao()
    private val scheduleDao = db.scheduleDao()

    override fun observeBlockedApps(): Flow<List<BlockedApp>> =
        blockedDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFeedRules(): Flow<List<FeedRule>> =
        feedDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSchedules(): Flow<List<Schedule>> =
        scheduleDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<AppGroup>> =
        combine(groupDao.observeGroups(), groupDao.observeMembers()) { groups, members ->
            val byGroup = members.groupBy({ it.groupId }, { it.packageName })
            groups.map { AppGroup(it.id, it.name, byGroup[it.id].orEmpty().toSet(), it.presetKey) }
        }

    override suspend fun getBlockedApps(): List<BlockedApp> = blockedDao.getAll().map { it.toDomain() }
    override suspend fun getFeedRules(): List<FeedRule> = feedDao.getAll().map { it.toDomain() }
    override suspend fun getSchedules(): List<Schedule> = scheduleDao.getAll().map { it.toDomain() }
    override suspend fun getSchedule(id: Long): Schedule? = scheduleDao.get(id)?.toDomain()

    override suspend fun getGroups(): List<AppGroup> {
        val members = groupDao.getMembers().groupBy({ it.groupId }, { it.packageName })
        return groupDao.getGroups().map { AppGroup(it.id, it.name, members[it.id].orEmpty().toSet(), it.presetKey) }
    }

    override suspend fun setBlocked(packages: Collection<String>, blocked: Boolean) {
        if (packages.isEmpty()) return
        val now = System.currentTimeMillis()
        if (blocked) {
            val existing = blockedDao.getAll().associateBy { it.packageName }
            val rows = packages.map { pkg ->
                // Keep the user's scope / enabled choice if the app was already on the list.
                existing[pkg] ?: BlockedApp(pkg).toEntity(now)
            }
            blockedDao.upsertAll(rows)
        } else {
            // SQLite bound-variable limit safety for very large selections.
            packages.chunked(500).forEach { blockedDao.delete(it) }
        }
    }

    override suspend fun updateBlockedApp(app: BlockedApp) {
        blockedDao.upsert(app.toEntity(System.currentTimeMillis()))
    }

    override suspend fun upsertFeedRule(rule: FeedRule) = feedDao.upsert(rule.toEntity())
    override suspend fun deleteFeedRule(id: String) = feedDao.delete(id)

    override suspend fun seedDefaultFeedRulesIfEmpty() {
        if (feedDao.count() == 0) feedDao.upsertAll(DefaultFeedRules.all.map { it.toEntity() })
    }

    override suspend fun resetFeedRulesToDefault() {
        db.withTransaction {
            feedDao.deleteAll()
            feedDao.upsertAll(DefaultFeedRules.all.map { it.toEntity() })
        }
    }

    override suspend fun saveSchedule(schedule: Schedule): Long = scheduleDao.upsert(schedule.toEntity()).let {
        // Room returns the new row id for inserts and -1 for updates of an existing id.
        if (schedule.id != 0L) schedule.id else it
    }

    override suspend fun deleteSchedule(id: Long) = scheduleDao.delete(id)

    override suspend fun createGroup(name: String, members: Set<String>, presetKey: String?): Long =
        db.withTransaction {
            val id = groupDao.insertGroup(GroupEntity(name = name.trim(), presetKey = presetKey))
            groupDao.addMembers(members.map { GroupMemberEntity(id, it) })
            id
        }

    override suspend fun renameGroup(id: Long, name: String) = groupDao.rename(id, name.trim())
    override suspend fun setGroupMembers(id: Long, members: Set<String>) = groupDao.replaceMembers(id, members)
    override suspend fun deleteGroup(id: Long) = groupDao.delete(id)

    override suspend fun replaceAll(
        blocked: List<BlockedApp>,
        feeds: List<FeedRule>,
        groups: List<BackupGroup>,
        schedules: List<BackupSchedule>,
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            blockedDao.deleteAll()
            feedDao.deleteAll()
            groupDao.deleteAll() // members cascade
            scheduleDao.deleteAll()

            blockedDao.upsertAll(blocked.map { it.toEntity(now) })
            feedDao.upsertAll(feeds.map { it.toEntity() })
            val idByName = HashMap<String, Long>()
            for (g in groups) {
                val id = groupDao.insertGroup(GroupEntity(name = g.name, presetKey = g.presetKey))
                groupDao.addMembers(g.members.map { GroupMemberEntity(id, it) })
                idByName[g.name] = id
            }
            for (bs in schedules) {
                val ids = bs.groupNames.mapNotNull { idByName[it] }.toSet()
                scheduleDao.upsert(bs.schedule.copy(id = 0L, groupIds = ids).toEntity())
            }
        }
    }
}
