package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.utils.FirebaseRefs.feesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeesRepository @Inject constructor() {

    // ================================
    // Add or Update FeesModel
    // ================================
    suspend fun addOrUpdateFee(feesModel: FeesModel) {
        val key =
            feesModel.id.ifEmpty { feesRef.push().key ?: throw Exception("Failed to generate feesModel id") }

        val newFee = feesModel.copy(id = key)
        feesRef.child(key).setValue(newFee).await()
    }

    // ================================
    // Delete FeesModel
    // ================================
    suspend fun deleteFee(feeId: String) {
        if (feeId.isEmpty()) throw Exception("Invalid fee id")
        feesRef.child(feeId).removeValue().await()
    }

    // ================================
    // Get All Fees
    // ================================
    suspend fun getAllFees(): List<FeesModel> {
        val snapshot = feesRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
    }

    // ================================
    // Get Fees by Student ID
    // ================================
    suspend fun getFeesByStudent(studentId: String): List<FeesModel> {
        val snapshot = feesRef.orderByChild("studentId").equalTo(studentId).get().await()
        return snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
    }

    // ================================
    // Get Single FeesModel Record
    // ================================
    suspend fun getFeeById(feeId: String): FeesModel? {
        if (feeId.isEmpty()) throw Exception("Invalid fee id")
        val snapshot = feesRef.child(feeId).get().await()
        return snapshot.getValue(FeesModel::class.java)
    }
}
