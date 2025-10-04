package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.utils.FirebaseRefs.classesRef
import com.zabibtech.alkhair.utils.FirebaseRefs.divisionsRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassManagerRepository @Inject constructor() {

    // =============================
    // Division CRUD
    // =============================
    suspend fun addDivision(division: DivisionModel) {
        val key = divisionsRef.push().key ?: throw Exception("Failed to generate division key")
        val newDivision = division.copy(id = key)
        divisionsRef.child(key).setValue(newDivision).await()
    }

    suspend fun updateDivision(division: DivisionModel) {
        if (division.id.isEmpty()) throw Exception("Invalid division id")
        divisionsRef.child(division.id).setValue(division).await()
    }

    suspend fun deleteDivision(divisionId: String) {
        divisionsRef.child(divisionId).removeValue().await()
    }

    suspend fun getAllDivisions(): List<DivisionModel> {
        val snap = divisionsRef.get().await()
        return snap.children.mapNotNull { it.getValue(DivisionModel::class.java) }
    }

    // =============================
    // Class CRUD
    // =============================
    suspend fun addClass(classModel: ClassModel) {
        val key = classesRef.push().key ?: throw Exception("Failed to generate class key")
        val newClass = classModel.copy(id = key)
        classesRef.child(key).setValue(newClass).await()
    }

    suspend fun updateClass(classModel: ClassModel) {
        if (classModel.id.isEmpty()) throw Exception("Invalid class id")
        classesRef.child(classModel.id).setValue(classModel).await()
    }

    suspend fun deleteClass(classId: String) {
        classesRef.child(classId).removeValue().await()
    }

    suspend fun getAllClasses(): List<ClassModel> {
        val snap = classesRef.get().await()
        return snap.children.mapNotNull { it.getValue(ClassModel::class.java) }
    }

    suspend fun getClassesByDivision(divisionName: String): List<ClassModel> {
        val snap = classesRef.orderByChild("division").equalTo(divisionName).get().await()
        return snap.children.mapNotNull { it.getValue(ClassModel::class.java) }
    }

    // =============================
    // Add Class + Ensure Division
    // =============================
    suspend fun addClassWithDivisionCheck(classModel: ClassModel) {
        val snap = divisionsRef.orderByChild("name")
            .equalTo(classModel.division)
            .get().await()

        if (!snap.exists()) {
            val divKey = divisionsRef.push().key ?: throw Exception("Failed to generate division key")
            val newDivision = DivisionModel(id = divKey, name = classModel.division)
            divisionsRef.child(divKey).setValue(newDivision).await()
        }

        addClass(classModel)
    }
}
