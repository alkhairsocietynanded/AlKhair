package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.utils.FirebaseRefs.classesRef
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClassRepository @Inject constructor() {

    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        return try {
            val key = classModel.id.ifEmpty { classesRef.push().key ?: throw Exception("Failed to generate class key") }
            val newClass = classModel.copy(id = key)
            classesRef.child(key).setValue(newClass).await()
            Result.success(newClass)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error adding class", e)
            Result.failure(e)
        }
    }

    suspend fun updateClass(classModel: ClassModel): Result<Unit> {
        return try {
            if (classModel.id.isEmpty()) throw Exception("Invalid class id for update")
            classesRef.child(classModel.id).setValue(classModel).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error updating class", e)
            Result.failure(e)
        }
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            if (classId.isEmpty()) throw Exception("Invalid class id for delete")
            classesRef.child(classId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error deleting class", e)
            Result.failure(e)
        }
    }

    suspend fun getAllClasses(): Result<List<ClassModel>> {
        return try {
            val snap = classesRef.get().await()
            val classes = snap.children.mapNotNull { it.getValue(ClassModel::class.java) }
            Result.success(classes)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error getting all classes", e)
            Result.failure(e)
        }
    }

    suspend fun getClassesByDivision(divisionName: String): Result<List<ClassModel>> {
        return try {
            val snap = classesRef.orderByChild("division").equalTo(divisionName).get().await()
            val classes = snap.children.mapNotNull { it.getValue(ClassModel::class.java) }
            Result.success(classes)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error getting classes by division", e)
            Result.failure(e)
        }
    }

    suspend fun getClassesUpdatedAfter(timestamp: Long): Result<List<ClassModel>> {
        return try {
            val snapshot = classesRef.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val classes = snapshot.children.mapNotNull { it.getValue(ClassModel::class.java) }
            Result.success(classes)
        } catch (e: Exception) {
            Log.e("FirebaseClassRepo", "Error getting updated classes", e)
            Result.failure(e)
        }
    }
}