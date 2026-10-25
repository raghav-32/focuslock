package com.focuslock.app.data.repo

import com.focuslock.app.data.db.FocusSessionDao
import com.focuslock.app.data.db.FocusSessionEntity
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.ReconcileResult
import com.focuslock.app.domain.SessionRepository
import com.focuslock.app.domain.SessionState
import com.focuslock.app.domain.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomSessionRepository(
    private val dao: FocusSessionDao,
    private val time: TimeSource,
) : SessionRepository {

    private val mutex = Mutex()

    override fun observeActive(): Flow<FocusSession?> = dao.observeActive().map { it?.toDomain() }

    override fun observeSince(sinceWall: Long): Flow<List<FocusSession>> =
        dao.observeSince(sinceWall).map { list -> list.map { it.toDomain() } }

    override suspend fun start(plannedMillis: Long, strict: Boolean, groupId: Long?): FocusSession =
        mutex.withLock {
            val running = dao.getActive()?.toDomain()
            if (running != null && running.remainingMillis(time) > 0L) return@withLock running

            val entity = FocusSessionEntity(
                startWall = time.wallMillis(),
                startElapsed = time.elapsedMillis(),
                startBoot = time.bootCount(),
                plannedMillis = plannedMillis.coerceAtLeast(60_000L),
                strict = strict,
                groupId = groupId,
                state = SessionState.ACTIVE.name,
                endedWall = null,
            )
            val id = dao.insert(entity)
            entity.copy(id = id).toDomain()
        }

    override suspend fun cancel(id: Long): Boolean = mutex.withLock {
        val active = dao.getActive()?.toDomain() ?: return@withLock true
        if (active.id != id) return@withLock true
        if (active.strict && active.remainingMillis(time) > 0L) return@withLock false
        dao.finish(id, SessionState.CANCELLED.name, time.wallMillis())
        true
    }

    override suspend fun reconcile(): ReconcileResult = mutex.withLock {
        val active = dao.getActive()?.toDomain() ?: return@withLock ReconcileResult(null, null)
        if (active.remainingMillis(time) > 0L) return@withLock ReconcileResult(active, null)
        // Exactly one caller sees changed == 1 and therefore sends the completion notification.
        val changed = dao.finish(active.id, SessionState.COMPLETED.name, time.wallMillis())
        ReconcileResult(null, if (changed == 1) active else null)
    }

    override suspend fun clearHistory() = dao.clearFinished()
}
