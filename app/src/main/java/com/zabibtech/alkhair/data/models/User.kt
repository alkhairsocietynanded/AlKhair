package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "users")
@com.google.firebase.database.IgnoreExtraProperties
data class User(
    //personal details
    @PrimaryKey
    val uid: String = "",
    val name: String = "",
    val parentName: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val address: String = "",
    val dateOfBirth: String = "",

    // Academic details
    val role: String = "",
    val classId: String = "",
    val className: String = "",
    val divisionId: String = "",
    val divisionName: String = "",
    val subject: String = "",
    val shift: String = "",
    val isActive: Boolean = true,
    val dateOfJoining: String = "",

    // 🔹 FeesModel-related (for Students)
    val totalFees: String = "",
    val paidFees: String = "",
    val dueFees: String = "",

    // 🔹 Salary-related (for Teachers)
    val salary: String = "",
    val paidSalary: String = "",
    val dueSalary: String = "",
    override val updatedAt: Long = System.currentTimeMillis() // ✅ Override zaroori hai
) : Parcelable, Syncable // ✅ Interface implement karein
