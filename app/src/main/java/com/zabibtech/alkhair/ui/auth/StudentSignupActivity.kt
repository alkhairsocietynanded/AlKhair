package com.zabibtech.alkhair.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityStudentSignupBinding
import com.zabibtech.alkhair.ui.dashboard.AdminDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.StudentDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.TeacherDashboardActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudentSignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentSignupBinding
    private val vm: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStudentSignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.tvLoginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnSignup.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val className = binding.etClass.text.toString().trim()
            val section = binding.etSection.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || className.isEmpty() || section.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val student = User(
                name = name,
                email = email,
                role = "student",
                className = className,
                divisionName = section
            )

            vm.signup(email, password, student)
        }

        vm.state.observe(this, Observer { state ->
            when (state) {
                is UiState.Loading -> {
                    Toast.makeText(this, "Signing up student...", Toast.LENGTH_SHORT).show()
                }

                is UiState.Success -> {
                    Toast.makeText(this, "Student created successfully", Toast.LENGTH_SHORT).show()
                    val user = state.data
                    routeToDashboard(user)
                    finish()
                }

                is UiState.Error -> {
                    DialogUtils.showAlert(this, "Error", state.message)
                }

                else -> {}
            }
        })
    }

    private fun routeToDashboard(user: User) {
        when (user.role) {
            "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
            "teacher" -> startActivity(Intent(this, TeacherDashboardActivity::class.java))
            "student" -> startActivity(Intent(this, StudentDashboardActivity::class.java))
        }
        finish()
    }
}