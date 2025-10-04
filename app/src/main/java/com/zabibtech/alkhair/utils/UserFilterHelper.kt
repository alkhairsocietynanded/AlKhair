package com.zabibtech.alkhair.utils

import com.zabibtech.alkhair.data.models.User

object UserFilterHelper {
    fun filterUsers(
        users: List<User>,
        role: String = Roles.STUDENT,
        classId: String? = null,
        shift: String = "All"
    ): List<User> {
        return users.filter { user ->
            val matchesRole = user.role == role
            val matchesClass = classId.isNullOrEmpty() || user.classId == classId
            val matchesShift = shift == "All" ||
                    user.shift.equals(shift, ignoreCase = true)

            matchesRole && matchesClass && matchesShift
        }
    }
}
