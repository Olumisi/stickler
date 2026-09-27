package com.isedalabs.stickler.core.reminder

import com.isedalabs.stickler.core.data.Task
import com.isedalabs.stickler.core.data.TaskRepository
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

    override suspend fun update(task: Task) {
        tasks[task.id] = task
    }

    override suspend fun get(id: Long): Task? = tasks[id]

    override suspend fun getActive(): List<Task> = tasks.values.filter { it.status.isActive }

    override fun observeAll(): Flow<List<Task>> = throw UnsupportedOperationException()

    fun require(id: Long): Task = tasks.getValue(id)
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

    override fun show(task: Task, canSnooze: Boolean) {
        showing[task.id] = canSnooze
        showCount++
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
