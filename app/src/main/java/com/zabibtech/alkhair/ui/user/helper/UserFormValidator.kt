package com.zabibtech.alkhair.ui.user.helper

import android.util.Patterns
import com.zabibtech.alkhair.databinding.ActivityUserFormBinding
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles

object UserFormValidator {
    fun validate(binding: ActivityUserFormBinding, role: String, mode: String): Boolean =
        with(binding) {
            layoutName.error = null
            layoutEmail.error = null
            layoutPassword.error = null
            layoutPhone.error = null
            layoutAddress.error = null
            layoutDivision.error = null
            layoutClass.error = null
            layoutShift.error = null
            layoutSubject.error = null
            layoutDob.error = null
            layoutTotalFees.error = null
            layoutSalary.error = null

            val name = etName.text.toString().trim()
            val parentName = etParentName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val phone = etPhone.text.toString().trim()
            val address = etAddress.text.toString().trim()
            val division = etDivision.text.toString().trim()
            val className = etClass.text.toString().trim()
            val shift = etShift.text.toString().trim()
            val subject = etSubject.text.toString().trim()
            val dob = etDob.text.toString().trim()
            val totalFees = etTotalFees.text.toString().trim()
            val salary = etSalary.text.toString().trim()


            return when {
                name.isEmpty() -> {
                    layoutName.error = "Name is required"; etName.requestFocus(); false
                }

                parentName.isEmpty() -> {
                    layoutParentName.error =
                        "Parent Name is required"; etParentName.requestFocus(); false
                }

                email.isEmpty() -> {
                    layoutEmail.error = "Email is required"; etEmail.requestFocus(); false
                }

                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    layoutEmail.error = "Invalid email"; etEmail.requestFocus(); false
                }

                mode == Modes.CREATE && password.length < 6 -> {
                    layoutPassword.error =
                        "Password must be at least 6 characters"; etPassword.requestFocus(); false
                }

                phone.isEmpty() -> {
                    layoutPhone.error = "Phone is required"; etPhone.requestFocus(); false
                }

                phone.length < 10 -> {
                    layoutPhone.error =
                        "Phone must be at least 10 digits"; etPhone.requestFocus(); false
                }

                address.isEmpty() -> {
                    layoutAddress.error = "Address is required"; etAddress.requestFocus(); false
                }

                role == Roles.STUDENT && division.isEmpty() -> {
                    layoutDivision.error = "Division is required"; etDivision.requestFocus(); false
                }

                role == Roles.STUDENT && className.isEmpty() -> {
                    layoutClass.error = "Class is required"; etClass.requestFocus(); false
                }

                role == Roles.TEACHER && subject.isEmpty() -> {
                    layoutSubject.error = "Subject is required"; etSubject.requestFocus(); false
                }

                shift.isEmpty() -> {
                    layoutShift.error = "Shift is required"; etShift.requestFocus(); false
                }

                role == Roles.STUDENT && dob.isEmpty() -> {
                    layoutDob.error = "Date of Birth is required"; etDob.requestFocus(); false
                }

                role == Roles.STUDENT && totalFees.isEmpty() -> {
                    layoutTotalFees.error = "Total Fees is required"; etTotalFees.requestFocus(); false
                }

                role == Roles.TEACHER && salary.isEmpty() -> {
                    layoutSalary.error = "Salary is required"; etSalary.requestFocus(); false
                }


                else -> true
            }
        }
}