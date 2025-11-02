package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    //personal details
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
    val dueSalary: String = ""
) : Parcelable
