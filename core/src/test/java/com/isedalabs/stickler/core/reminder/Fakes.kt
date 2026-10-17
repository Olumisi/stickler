package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.Settings
import com.isedalabs.stickler.core.data.SettingsRepository
import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
import com.isedalabs.stickler.core.data.TimeWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<Long, Task>()
    private var nextId = 1L

    override suspend fun insert(task: Task): Long {
        val id = nextId++
        tasks[id] = task.copy(id = id)
        return id
    }

    /** Like Room's @Update: a no-op if the row was deleted. */
    override suspend fun update(task: Task) {
        if (task.id in tasks) tasks[task.id] = task
    }

    override suspend fun delete(task: Task) {
        tasks.remove(task.id)
    }

    /**
     * When set, the next [get] reads the row and then suspends until this completes before
     * returning it, so a test can run another call in between (the stale-read race).
     */
    var holdAfterGet: CompletableDeferred<Unit>? = null

    override suspend fun get(id: Long): Task? {
        val task = tasks[id]
        holdAfterGet?.let {
            holdAfterGet = null
            it.await()
        }
        return task
    }

    override suspend fun getActive(): List<Task> = tasks.values.filter { it.status.isActive }

    override fun observeAll(): Flow<List<Task>> = throw UnsupportedOperationException()

    override fun observeIn(window: TimeWindow): Flow<List<Task>> = throw UnsupportedOperationException()

    fun require(id: Long): Task = tasks.getValue(id)

    fun contains(id: Long): Boolean = id in tasks
}

class FakeSettingsRepository(var settings: Settings = Settings()) : SettingsRepository {
    override suspend fun get(): Settings = settings
}

class FakeAlarmScheduler : AlarmScheduler {
    var result = ScheduleResult.Exact

    /** Pending alarms, as the platform would hold them: one per (task, kind). */
    val pending = mutableMapOf<Pair<Long, AlarmKind>, Instant>()

    override fun schedule(taskId: Long, at: Instant, kind: AlarmKind): ScheduleResult {
        pending[taskId to kind] = at
        return result
    }

    override fun cancel(taskId: Long, kind: AlarmKind) {
        pending.remove(taskId to kind)
    }
}

class FakeNotifier : ReminderNotifier {
    /** Currently visible notifications: task id to canSnooze. */
    val showing = mutableMapOf<Long, Boolean>()
    var showCount = 0

    /** Whether the most recent [show] was silent. */
    var lastSilent: Boolean? = null

    override fun show(task: Task, canSnooze: Boolean, silent: Boolean) {
        showing[task.id] = canSnooze
        showCount++
        lastSilent = silent
    }

    override fun cancel(taskId: Long) {
        showing.remove(taskId)
    }

    /** Simulates the user swiping the notification away. */
    fun swipe(taskId: Long) {
        showing.remove(taskId)
    }
}

class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(by: Duration) {
        now = now.plus(by)
    }
}
