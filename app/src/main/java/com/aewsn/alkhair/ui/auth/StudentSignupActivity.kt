package com.aewsn.alkhair.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ActivityStudentSignupBinding
import com.aewsn.alkhair.ui.dashboard.AdminDashboardActivity
import com.aewsn.alkhair.ui.dashboard.StudentDashboardActivity
import com.aewsn.alkhair.ui.dashboard.TeacherDashboardActivity
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentSignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentSignupBinding
    private val viewModel: LoginViewModel by viewModels()

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

        setupListeners()
        observeSignupState()
    }

    private fun setupListeners() {
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
                // Password should be handled securely and not stored in the User model
                password = password, 
                role = "student",
                className = className,
                divisionName = section
            )

            viewModel.signup(student)
        }
    }

    private fun observeSignupState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Consider using DialogUtils.showLoading for consistency
                            Toast.makeText(this@StudentSignupActivity, "Signing up student...", Toast.LENGTH_SHORT).show()
                        }

                        is UiState.Success -> {
                            Toast.makeText(this@StudentSignupActivity, "Student created successfully", Toast.LENGTH_SHORT).show()
                            routeToDashboard(state.data)
                            finish()
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(this@StudentSignupActivity, "Error", state.message)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun routeToDashboard(user: User) {
        val destination = when (user.role) {
            "admin" -> AdminDashboardActivity::class.java
            "teacher" -> TeacherDashboardActivity::class.java
            "student" -> StudentDashboardActivity::class.java
            else -> LoginActivity::class.java // Fallback to login
        }
        startActivity(Intent(this, destination))
        finish()
    }
}
