package com.aewsn.alkhair.data.remote.supabase

import com.aewsn.alkhair.data.models.Subject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSubjectRepo @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getSubjectsUpdatedAfter(timestamp: Long): Result<List<Subject>> {
        return try {
            val subjects = supabase.postgrest["subjects"]
                .select(columns = Columns.ALL) {
                    filter {
                        gt("updated_at_ms", timestamp)
                    }
                }.decodeList<Subject>()
            Result.success(subjects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertSubject(subject: Subject): Result<Unit> {
        return try {
            supabase.postgrest["subjects"].upsert(subject)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteSubject(id: String): Result<Unit> {
         return try {
            supabase.postgrest["subjects"].delete {
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
