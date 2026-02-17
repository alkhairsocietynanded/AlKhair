package com.aewsn.alkhair.data.remote.supabase

import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.Result
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseResultRepo @Inject constructor(
    private val supabase: SupabaseClient
) {
    // ================== EXAMS ==================

    suspend fun getExamsUpdatedAfter(timestamp: Long): kotlin.Result<List<Exam>> {
        return try {
            val exams = supabase.postgrest["exams"]
                .select(columns = Columns.ALL) {
                    filter {
                        gt("updated_at_ms", timestamp)
                    }
                }.decodeList<Exam>()
            kotlin.Result.success(exams)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun upsertExam(exam: Exam): kotlin.Result<Unit> {
        return try {
            supabase.postgrest["exams"].upsert(exam)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun deleteExam(id: String): kotlin.Result<Unit> {
        return try {
            supabase.postgrest["exams"].delete {
                filter {
                    eq("id", id)
                }
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    // ================== RESULTS ==================

    suspend fun getResultsUpdatedAfter(timestamp: Long): kotlin.Result<List<Result>> {
        return try {
            val results = supabase.postgrest["results"]
                .select(columns = Columns.ALL) {
                    filter {
                        gt("updated_at_ms", timestamp)
                    }
                }.decodeList<Result>()
            kotlin.Result.success(results)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    /**
     * Fetch results for a specific student (Student Role optimization)
     */
    suspend fun getResultsForStudent(studentId: String, updatedAfter: Long): kotlin.Result<List<Result>> {
        return try {
             val results = supabase.postgrest["results"]
                .select(columns = Columns.ALL) {
                    filter {
                        and {
                            eq("user_id", studentId)
                            gt("updated_at_ms", updatedAfter)
                        }
                    }
                }.decodeList<Result>()
            kotlin.Result.success(results)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    /**
     * Fetch all results for a specific exam (Teacher/Admin Role optimization)
     */
    suspend fun getResultsForExam(examId: String): kotlin.Result<List<Result>> {
        return try {
             val results = supabase.postgrest["results"]
                .select(columns = Columns.ALL) {
                    filter {
                        eq("exam_id", examId)
                    }
                }.decodeList<Result>()
            kotlin.Result.success(results)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun upsertResult(result: Result): kotlin.Result<Unit> {
        return try {
            supabase.postgrest["results"].upsert(result)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    /**
     * Batch upsert results — for adding marks of entire class at once
     */
    suspend fun upsertResults(results: List<Result>): kotlin.Result<Unit> {
        return try {
            supabase.postgrest["results"].upsert(results)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    suspend fun deleteResult(id: String): kotlin.Result<Unit> {
        return try {
            supabase.postgrest["results"].delete {
                filter {
                    eq("id", id)
                }
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
}
