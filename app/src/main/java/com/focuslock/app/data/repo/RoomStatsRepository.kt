package com.focuslock.app.data.repo

import androidx.room.withTransaction
import com.focuslock.app.data.db.BlockEventDao
import com.focuslock.app.data.db.DailyUsageDao
import com.focuslock.app.data.db.FocusLockDatabase
import com.focuslock.app.domain.BlockEvent
import com.focuslock.app.domain.DailyUsage
import com.focuslock.app.domain.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStatsRepository(
    private val db: FocusLockDatabase,
    private val sessionHistoryClear: suspend () -> Unit,
) : StatsRepository {
    private val events: BlockEventDao = db.blockEventDao()
    private val usage: DailyUsageDao = db.dailyUsageDao()

    override fun observeEventsSince(sinceWall: Long): Flow<List<BlockEvent>> =
        events.observeSince(sinceWall).map { list -> list.map { it.toDomain() } }

    override fun observeUsageBetween(fromDay: Long, toDay: Long): Flow<List<DailyUsage>> =
        usage.observeBetween(fromDay, toDay).map { list -> list.map { it.toDomain() } }

    override suspend fun recordBlock(event: BlockEvent) = events.insert(event.toEntity())

    override suspend fun upsertUsage(rows: List<DailyUsage>) {
        if (rows.isNotEmpty()) usage.upsertAll(rows.map { it.toEntity() })
    }

    override suspend fun getAllEvents(): List<BlockEvent> = events.getAll().map { it.toDomain() }
    override suspend fun getAllUsage(): List<DailyUsage> = usage.getAll().map { it.toDomain() }

    override suspend fun replaceHistory(events: List<BlockEvent>, usage: List<DailyUsage>) {
        db.withTransaction {
            this.events.deleteAll()
            this.usage.deleteAll()
            this.events.insertAll(events.map { it.toEntity() })
            this.usage.upsertAll(usage.map { it.toEntity() })
        }
    }

    /** Deletes block events, usage summaries and finished focus sessions. */
    override suspend fun clear() {
        db.withTransaction {
            events.deleteAll()
            usage.deleteAll()
        }
        sessionHistoryClear()
    }
}
