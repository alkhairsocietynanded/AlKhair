package com.zabibtech.alkhair.data.datastore

import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.repository.ClassManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassDivisionStore @Inject constructor(
    private val appDataStore: AppDataStore,
    private val classManagerRepository: ClassManagerRepository
) {
    companion object {
        private const val DIVISION_KEY = "division_list"
        private const val CLASS_KEY = "class_list"
    }

    // ==============================
    // Divisions
    // ==============================
    suspend fun saveDivisionList(list: List<DivisionModel>) {
        appDataStore.saveList(DIVISION_KEY, list)
    }

    suspend fun getDivisionList(): List<DivisionModel> {
        return appDataStore.getList(DIVISION_KEY)
    }

    suspend fun getOrFetchDivisionList(): List<DivisionModel> {
        val cached = getDivisionList()

        // background refresh
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fresh = classManagerRepository.getAllDivisions()
                saveDivisionList(fresh)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return cached.ifEmpty {
            val fresh = classManagerRepository.getAllDivisions()
            saveDivisionList(fresh)
            fresh
        }
    }

    // ==============================
    // Classes
    // ==============================
    suspend fun saveClassList(list: List<ClassModel>) {
        appDataStore.saveList(CLASS_KEY, list)
    }

    suspend fun getClassList(): List<ClassModel> {
        return appDataStore.getList(CLASS_KEY)
    }

    suspend fun getOrFetchClassList(): List<ClassModel> {
        val cached = getClassList()

        // background refresh
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fresh = classManagerRepository.getAllClasses()
                saveClassList(fresh)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return cached.ifEmpty {
            val fresh = classManagerRepository.getAllClasses()
            saveClassList(fresh)
            fresh
        }
    }

    // ==============================
    // Clear
    // ==============================
    suspend fun clearAll() {
        appDataStore.clearKey(DIVISION_KEY)
        appDataStore.clearKey(CLASS_KEY)
    }
}
