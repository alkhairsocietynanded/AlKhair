package com.zabibtech.alkhair.ui.user.helper

import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserFormBinding
import com.zabibtech.alkhair.ui.user.helper.UserFormValidator
import com.zabibtech.alkhair.utils.Roles

object UserBuilder {
    fun build(
        binding: ActivityUserFormBinding,
        role: String,
        mode: String,
        selectedClassId: String?,
        userToEdit: User?
    ): User? {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val phone = binding.etPhone.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()
        val division = binding.etDivision.text.toString().trim()
        val className = binding.etClass.text.toString().trim()
        val shift = binding.etShift.text.toString().trim()
        val subject = binding.etSubject.text.toString().trim()

        if (!UserFormValidator.validate(binding, role, mode)) return null

        return when (role) {
            Roles.TEACHER -> User(
                uid = userToEdit?.uid.orEmpty(),
                name = name,
                email = email,
                role = Roles.TEACHER,
                divisionName = division,
                className = className,
                classId = selectedClassId.orEmpty(),
                subject = subject,
                phone = phone,
                password = password,
                address = address,
                shift = shift
            )

            Roles.STUDENT -> User(
                uid = userToEdit?.uid.orEmpty(),
                name = name,
                email = email,
                role = Roles.STUDENT,
                divisionName = division,
                className = className,
                classId = selectedClassId.orEmpty(),
                phone = phone,
                password = password,
                address = address,
                shift = shift
            )

            else -> User(
                uid = userToEdit?.uid.orEmpty(),
                name = name,
                email = email,
                role = Roles.ADMIN,
                phone = phone,
                password = password
            )
        }
    }
}