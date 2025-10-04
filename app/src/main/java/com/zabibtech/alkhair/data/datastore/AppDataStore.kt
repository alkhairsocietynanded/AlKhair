package com.zabibtech.alkhair.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore("app_data_store")

@Singleton
class AppDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val gson: Gson // 🔹 public to allow inline functions access
) {
     val dataStore: DataStore<Preferences> = context.appDataStore

    // -----------------------------
    // Save any object
    // -----------------------------
    suspend fun <T> saveObject(keyName: String, obj: T) {
        val json = gson.toJson(obj)
        val key = stringPreferencesKey(keyName)
        dataStore.edit { prefs -> prefs[key] = json }
    }

    // -----------------------------
    // Get single object (nullable)
    // -----------------------------
    suspend inline fun <reified T> getObject(keyName: String): T? {
        val key = stringPreferencesKey(keyName)
        val json = dataStore.data.map { prefs -> prefs[key] ?: "" }.first()
        return if (json.isNotEmpty()) gson.fromJson(json, T::class.java) else null
    }

    // -----------------------------
    // Save a list of objects
    // -----------------------------
    suspend fun <T> saveList(keyName: String, list: List<T>) {
        val json = gson.toJson(list)
        val key = stringPreferencesKey(keyName)
        dataStore.edit { prefs -> prefs[key] = json }
    }

    // -----------------------------
    // Get a list of objects (empty if not exists)
    // -----------------------------
    suspend inline fun <reified T> getList(keyName: String): List<T> {
        val key = stringPreferencesKey(keyName)
        val json = dataStore.data.map { prefs -> prefs[key] ?: "" }.first()
        return if (json.isNotEmpty()) {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type)
        } else emptyList()
    }

    // -----------------------------
    // Clear a key
    // -----------------------------
    suspend fun clearKey(keyName: String) {
        val key = stringPreferencesKey(keyName)
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    // -----------------------------
    // Clear all data
    // -----------------------------
    suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
