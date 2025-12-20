package com.zabibtech.alkhair.data.manager.base

import kotlinx.coroutines.flow.Flow

abstract class BaseRepoManager<T> {

    abstract fun observeLocal(): Flow<List<T>>

    protected abstract suspend fun insertLocal(items: List<T>)
    protected abstract suspend fun insertLocal(item: T)

    protected abstract suspend fun fetchRemoteUpdated(after: Long): List<T>

    suspend fun sync(lastSync: Long): Result<Unit> {
        return try {
            val updated = fetchRemoteUpdated(lastSync)
            if (updated.isNotEmpty()) {
                insertLocal(updated)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    abstract suspend fun deleteLocally(id: String)
}
