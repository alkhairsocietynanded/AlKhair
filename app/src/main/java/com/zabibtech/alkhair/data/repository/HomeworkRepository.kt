package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.FirebaseRefs.homeworkRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("Use HomeworkRepoManager instead")
@Singleton
class HomeworkRepository @Inject constructor() {

    suspend fun addHomework(homework: Homework) {
        val id = homeworkRef.push().key ?: return
        val hw = homework.copy(id = id)
        homeworkRef.child("${hw.className}_${hw.division}").child(id).setValue(hw).await()
    }

    /**
     * Updates an existing homework item in the database.
     * The homework ID must not be blank.
     */
    suspend fun updateHomework(homework: Homework) {
        if (homework.id.isBlank()) return
        homeworkRef.child("${homework.className}_${homework.division}").child(homework.id)
            .setValue(homework).await()
    }

    suspend fun getHomeworkListByClass(className: String, division: String): List<Homework> {
        val snapshot = homeworkRef.child("${className}_${division}").get().await()
        return snapshot.children.mapNotNull { it.getValue(Homework::class.java) }
    }

    /**
     * Fetches all homework items from all classes and divisions.
     */
    suspend fun getAllHomeworkList(): List<Homework> {
        val snapshot = homeworkRef.get().await()
        // Iterate through each class/division child, then map the homework children within each
        return snapshot.children.flatMap { classDivisionSnapshot ->
            classDivisionSnapshot.children.mapNotNull { homeworkSnapshot ->
                homeworkSnapshot.getValue(Homework::class.java)
            }
        }
    }

    /**
     * Deletes a homework item from the database using its full object
     * to construct the correct database path.
     */
    suspend fun deleteHomework(homework: Homework) {
        if (homework.id.isBlank()) return
        homeworkRef.child("${homework.className}_${homework.division}").child(homework.id)
            .removeValue().await()
    }
}