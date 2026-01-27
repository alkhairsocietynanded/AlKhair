package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.UserDao
import com.zabibtech.alkhair.data.models.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalUserRepository @Inject constructor(
    private val userDao: UserDao
) {
    fun getUserById(uid: String): Flow<User?> = userDao.getUserById(uid)

    suspend fun getUserByIdOneShot(uid: String): User? = userDao.getUserByIdOneShot(uid)
    suspend fun getUsersByIds(uids: List<String>): List<User> = userDao.getUsersByIds(uids)

    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    fun getUsersByRole(role: String): Flow<List<User>> = userDao.getUsersByRole(role)

    suspend fun insertUser(user: User) = userDao.insertUser(user)

    suspend fun insertUsers(users: List<User>) = userDao.insertUsers(users)

    suspend fun deleteUser(uid: String) = userDao.deleteUser(uid)

    suspend fun clearAll() = userDao.clearAllUsers()

    suspend fun getUnsyncedUsers(): List<User> = userDao.getUnsyncedUsers()

    suspend fun markUsersAsSynced(ids: List<String>) = userDao.markUsersAsSynced(ids)
}
