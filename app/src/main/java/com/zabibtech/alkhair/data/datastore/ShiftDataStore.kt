package com.zabibtech.alkhair.data.datastore

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftDataStore @Inject constructor(
    private val appDataStore: AppDataStore
) {
    companion object {
        private const val SHIFT_KEY = "shift_data"
    }

    suspend fun saveShift(shift: String) {
        appDataStore.saveString(SHIFT_KEY, shift)
    }

    suspend fun getShift(): String {
        return appDataStore.getString(SHIFT_KEY)
    }

    suspend fun clearShift() {
        appDataStore.clearKey(SHIFT_KEY)
    }
}