package com.zabibtech.alkhair.ui.user.helper

import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserFormBinding
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
        val parentName = binding.etParentName.text.toString().trim()
        val dateOfBirth = binding.etDob.text.toString().trim()
        val totalFees = binding.etTotalFees.text.toString().trim()
        val salary = binding.etSalary.text.toString().trim()
//        val paidFees = binding.etPaidFees.text.toString().trim()
//        val paidSalary = binding.etPaidSalary.text.toString().trim()

        if (!UserFormValidator.validate(binding, role, mode)) return null

        return when (role) {
            // 👨‍🏫 TEACHER
            Roles.TEACHER -> {
//                val salaryValue = salary.ifEmpty { "0" }.toDoubleOrNull() ?: 0.0
//                val paidValue = paidSalary.ifEmpty { "0" }.toDoubleOrNull() ?: 0.0
//                val dueSalary = (salaryValue - paidValue).coerceAtLeast(0.0).toString()

                User(
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
                    shift = shift,
                    parentName = parentName,
                    dateOfBirth = dateOfBirth,
                    salary = salary,
//                    paidSalary = paidSalary,
//                    dueSalary = dueSalary
                )
            }

            // 👨‍🎓 STUDENT
            Roles.STUDENT -> {
//                val totalValue = totalFees.ifEmpty { "0" }.toDoubleOrNull() ?: 0.0
//                val paidValue = paidFees.ifEmpty { "0" }.toDoubleOrNull() ?: 0.0
//                val dueValue = (totalValue - paidValue).coerceAtLeast(0.0).toString()

                User(
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
                    shift = shift,
                    parentName = parentName,
                    dateOfBirth = dateOfBirth,
                    totalFees = totalFees,
//                    paidFees = paidFees,
//                    dueFees = dueValue
                )
            }

            // 👨‍💼 ADMIN
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
