package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.Result
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {

    // ================== EXAMS ==================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<Exam>)

    @Query("SELECT * FROM exams ORDER BY start_date DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :examId")
    suspend fun getExamById(examId: String): Exam?

    @Query("SELECT * FROM exams WHERE is_synced = 0")
    suspend fun getUnsyncedExams(): List<Exam>

    @Query("UPDATE exams SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markExamsAsSynced(ids: List<String>)

    @Query("DELETE FROM exams WHERE id = :examId")
    suspend fun deleteExam(examId: String)

    // Teacher: all exams for their class
    @Query("SELECT * FROM exams WHERE class_id = :classId ORDER BY start_date DESC")
    fun getExamsByClassId(classId: String): Flow<List<Exam>>

    // Student: only published exams for their class
    @Query("SELECT * FROM exams WHERE class_id = :classId AND is_published = 1 ORDER BY start_date DESC")
    fun getPublishedExamsByClassId(classId: String): Flow<List<Exam>>

    // ================== RESULTS ==================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<Result>)

    @Query("SELECT * FROM results WHERE exam_id = :examId AND student_id = :studentId")
    fun getResultsForStudentRef(examId: String, studentId: String): Flow<List<Result>>
    
    // For joining with Subject names, we might need a POJO or just fetch results and map manually. 
    // Given the architecture, we usually fetch entities. 
    // But let's add a joined query for convenience if needed later.

    @Query("SELECT * FROM results WHERE exam_id = :examId")
    fun getResultsForExam(examId: String): Flow<List<Result>>

    @Query("SELECT * FROM results WHERE is_synced = 0")
    suspend fun getUnsyncedResults(): List<Result>

    @Query("UPDATE results SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markResultsAsSynced(ids: List<String>)

    @Query("DELETE FROM results WHERE id = :resultId")
    suspend fun deleteResult(resultId: String)
    
    @Query("DELETE FROM results WHERE exam_id = :examId")
    suspend fun deleteResultsByExam(examId: String)

    @Transaction // Ensure atomicity
    suspend fun replaceExamResults(examId: String, results: List<Result>) {
        deleteResultsByExam(examId)
        insertResults(results)
    }
}
