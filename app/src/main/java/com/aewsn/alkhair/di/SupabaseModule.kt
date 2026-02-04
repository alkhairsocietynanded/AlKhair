package com.aewsn.alkhair.di

import com.aewsn.alkhair.data.datastore.AppDataStore
import com.aewsn.alkhair.data.remote.supabase.AndroidSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(appDataStore: AppDataStore): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://eedvsfrjwkhlwqqdkbsm.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVlZHZzZnJqd2tobHdxcWRrYnNtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkxNjY3MTYsImV4cCI6MjA4NDc0MjcxNn0.vYT9SEzTH5rEdVXJjB8zF_njGIZYnnIvh6rl9KlUPZw"
        ) {
            install(Postgrest)
            install(Auth) {
                // ✅ Session Persistence Fix (Using DataStore)
                sessionManager = AndroidSessionManager(appDataStore)
            }
            install(Storage)
            install(Functions) // ✅ Enable Edge Functions
            
            // Explicitly set the serializer to ignore unknown keys - strict mode is false by default in recent versions but good to be explicit
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                coerceInputValues = true
            })
        }
    }
}
