package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.Fee
import com.zabibtech.alkhair.utils.FirebaseRefs.feesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeeRepository @Inject constructor() {

    // ================================
    // Add or Update Fee
    // ================================
    suspend fun addOrUpdateFee(fee: Fee) {
        val key =
            fee.id.ifEmpty { feesRef.push().key ?: throw Exception("Failed to generate fee id") }

        val newFee = fee.copy(id = key)
        feesRef.child(key).setValue(newFee).await()
    }

    // ================================
    // Delete Fee
    // ================================
    suspend fun deleteFee(feeId: String) {
        if (feeId.isEmpty()) throw Exception("Invalid fee id")
        feesRef.child(feeId).removeValue().await()
    }

    // ================================
    // Get All Fees
    // ================================
    suspend fun getAllFees(): List<Fee> {
        val snapshot = feesRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(Fee::class.java) }
    }

    // ================================
    // Get Fees by Student ID
    // ================================
    suspend fun getFeesByStudent(studentId: String): List<Fee> {
        val snapshot = feesRef.orderByChild("studentId").equalTo(studentId).get().await()
        return snapshot.children.mapNotNull { it.getValue(Fee::class.java) }
    }

    // ================================
    // Get Single Fee Record
    // ================================
    suspend fun getFeeById(feeId: String): Fee? {
        if (feeId.isEmpty()) throw Exception("Invalid fee id")
        val snapshot = feesRef.child(feeId).get().await()
        return snapshot.getValue(Fee::class.java)
    }
}
