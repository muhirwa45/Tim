package com.example.data

import kotlinx.coroutines.flow.Flow

class TimRepository(
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasksFlow()
    val allSessions: Flow<List<FocusSession>> = focusSessionDao.getAllSessionsFlow()

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun insertSession(session: FocusSession): Long = focusSessionDao.insertSession(session)

    suspend fun insertSessions(sessions: List<FocusSession>) = focusSessionDao.insertSessions(sessions)

    suspend fun deleteSession(session: FocusSession) = focusSessionDao.deleteSession(session)

    suspend fun clearAllSessions() = focusSessionDao.clearAllSessions()
}
