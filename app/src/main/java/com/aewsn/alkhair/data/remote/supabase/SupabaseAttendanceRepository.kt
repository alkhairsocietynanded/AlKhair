package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.Attendance
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAttendanceRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun saveAttendanceForClass(
        classId: String,
        date: String,
        shift: String,
        attendanceMap: Map<String, String>
    ): Result<Unit> {
        return try {
            val currentTime = System.currentTimeMillis()
            val safeShift = shift.ifBlank { "General" }
            
            val attendanceList = attendanceMap.map { (uid, status) ->
                Attendance(
                    studentId = uid,
                    classId = classId,
                    date = date,
                    status = status,
                    // shift = safeShift, // Removed from Supabase payload
                    updatedAt = currentTime,
                    isSynced = true // Remote is always synced relative to itself
                )
            }
            
            if (attendanceList.isNotEmpty()) {
                supabase.from("attendance").upsert(attendanceList)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAttendanceRepo", "Error saving attendance", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForStudentUpdatedAfter(
        studentId: String, 
        timestamp: Long
    ): Result<List<Attendance>> {
        return try {
            val list = supabase.from("attendance").select {
                filter {
                    eq("user_id", studentId)
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<Attendance>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseAttendanceRepo", "Error fetching student attendance", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceUpdatedAfter(timestamp: Long): Result<List<Attendance>> {
        return try {
            val allRecords = mutableListOf<Attendance>()
            val pageSize = 1000L
            var offset = 0L
            
            while (true) {
                val batch = supabase.from("attendance").select {
                    filter {
                        gt("updated_at_ms", timestamp)
                    }
                    range(offset, offset + pageSize - 1)
                }.decodeList<Attendance>()
                
                allRecords.addAll(batch)
                Log.d("SupabaseAttendanceRepo", "Fetched batch: ${batch.size} records (offset=$offset)")
                
                if (batch.size < pageSize) break // Last page
                offset += pageSize
            }
            
            Log.d("SupabaseAttendanceRepo", "Total attendance fetched: ${allRecords.size}")
            Result.success(allRecords)
        } catch (e: Exception) {
            Log.e("SupabaseAttendanceRepo", "Error fetching updated attendance (Global)", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForClassAndShiftUpdatedAfter(
        classId: String,
        shift: String, // Kept for signature compatibility but unused
        timestamp: Long
    ): Result<List<Attendance>> {
        return try {
            val list = supabase.from("attendance").select {
                filter {
                    eq("class_id", classId)
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<Attendance>()
            Result.success(list)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }

    suspend fun saveAttendanceBatch(attendanceList: List<Attendance>): Result<Unit> {
        return try {
            if (attendanceList.isEmpty()) return Result.success(Unit)
            supabase.from("attendance").upsert(attendanceList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAttendanceRepo", "Error saving batch attendance", e)
            Result.failure(e)
        }
    }
}
