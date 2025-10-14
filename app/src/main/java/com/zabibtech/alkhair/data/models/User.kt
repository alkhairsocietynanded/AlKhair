package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val classId: String = "",
    val className: String = "",
    val divisionName: String = "",
    val subject: String = "",
    val phone: String = "",
    val password: String = "",
    val address: String = "",
    val shift: String = "",
    val isActive: Boolean = true,

    // 🔹 Common extra fields
    val parentName: String = "",
    val dateOfBirth: String = "",

    // 🔹 FeesModel-related (for Students)
    val totalFees: String = "",
    val paidFees: String = "",
    val dueFees: String = "",

    // 🔹 Salary-related (for Teachers)
    val salary: String = "",
    val paidSalary: String = "",
    val dueSalary: String = ""
) : Parcelable
