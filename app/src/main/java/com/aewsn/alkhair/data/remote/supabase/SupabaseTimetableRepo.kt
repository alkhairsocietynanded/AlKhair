package com.aewsn.alkhair.data.remote.supabase

import com.aewsn.alkhair.data.models.Timetable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTimetableRepo @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getTimetablesUpdatedAfter(timestamp: Long): Result<List<Timetable>> {
        return try {
            val timetables = supabase.postgrest["timetable"]
                .select(columns = Columns.ALL) {
                    filter {
                        gt("updated_at_ms", timestamp)
                    }
                }.decodeList<Timetable>()
            Result.success(timetables)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertTimetable(timetable: Timetable): Result<Unit> {
        return try {
            supabase.postgrest["timetable"].upsert(timetable)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteTimetable(id: String): Result<Unit> {
         return try {
            supabase.postgrest["timetable"].delete {
                filter {
                    eq("id", id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
