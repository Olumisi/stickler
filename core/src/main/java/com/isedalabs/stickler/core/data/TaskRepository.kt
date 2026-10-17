package com.isedalabs.stickler.core.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Task storage used by the reminder engine and UI; an interface so engine tests can use a fake. */
interface TaskRepository {
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun get(id: Long): Task?
    suspend fun getActive(): List<Task>
    fun observeAll(): Flow<List<Task>>
    fun observeIn(window: TimeWindow): Flow<List<Task>>
}

class RoomTaskRepository @Inject constructor(private val dao: TaskDao) : TaskRepository {
    override suspend fun insert(task: Task): Long = dao.insert(task)
    override suspend fun update(task: Task) = dao.update(task)
    override suspend fun delete(task: Task) = dao.delete(task)
    override suspend fun get(id: Long): Task? = dao.getById(id)
    override suspend fun getActive(): List<Task> = dao.getActive()
    override fun observeAll(): Flow<List<Task>> = dao.observeAll()
    override fun observeIn(window: TimeWindow): Flow<List<Task>> = dao.observeBetween(window.start, window.end)
}
