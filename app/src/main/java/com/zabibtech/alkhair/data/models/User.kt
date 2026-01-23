package com.zabibtech.alkhair.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@Entity(tableName = "users")

data class User(
    //personal details
    //personal details
    @PrimaryKey
    var uid: String = "",
    var name: String = "",
    // Supabase: email is handled by auth but synced to public.users
    var email: String = "",
    
    // Transient password for UI/Signup flow only - DO NOT SAVE TO DB
    @androidx.room.Ignore
    @kotlinx.serialization.Transient

    var password: String = "",

    @SerialName("role")
    var role: String = "student", // 'student', 'teacher', 'admin'
    
    // Profile
    @SerialName("parent_name")
    var parentName: String = "",
    var phone: String = "",
    var address: String = "",
    @SerialName("dob")
    var dateOfBirth: String = "", // Mapped to 'dob' in SQL
    @SerialName("doj")
    var dateOfJoining: String = "", // Mapped to 'doj' in SQL

    // Academic details (UUIDs)
    @SerialName("class_id")
    var classId: String? = null,
    @SerialName("division_id")
    var divisionId: String? = null,
    
    // These might be joins in Supabase, but for local cache we might keep them or fetch via relation
    // For now we keep them but they might be null if not joined
    @SerialName("class_name")
    var className: String = "", 
    @SerialName("division_name")
    var divisionName: String = "",

    var subject: String = "",
    var shift: String = "General",
    
    @SerialName("is_active")
    var isActive: Boolean = true,

    // 🔹 FeesModel-related (for Students)
    // SQL uses double precision, we use Double
    @SerialName("total_fees")
    var totalFees: Double = 0.0,
    
    // Derived/Snapshot fields (SQL table has total_fees, salary)
    // paidFees and dueFees might be calculated clientside or joined
    @SerialName("paid_fees")
    var paidFees: Double = 0.0,
    @SerialName("due_fees")
    var dueFees: Double = 0.0,

    // 🔹 Salary-related (for Teachers)
    var salary: Double = 0.0,
    @SerialName("paid_salary")
    var paidSalary: Double = 0.0,
    @SerialName("due_salary")
    var dueSalary: Double = 0.0,

    @SerialName("updated_at")
    override var updatedAt: Long = System.currentTimeMillis(),
    
    @SerialName("is_synced")

    override var isSynced: Boolean = true
) : Parcelable, Syncable

