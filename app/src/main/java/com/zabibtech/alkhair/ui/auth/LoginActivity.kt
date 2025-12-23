package com.zabibtech.alkhair.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityLoginBinding
import com.zabibtech.alkhair.ui.dashboard.AdminDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.StudentDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.TeacherDashboardActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupListeners()
        observeLoginState()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                DialogUtils.showAlert(this, "Error", "Please fill all fields")
            } else {
                viewModel.login(email, password)
            }
        }

        binding.tvSignupRedirect.setOnClickListener {
            DialogUtils.showAlert(this, "Admission", "Please contact headmaster for admission")
        }
    }

    private fun observeLoginState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            DialogUtils.showLoading(supportFragmentManager, "Logging in...")
                        }

                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            routeToDashboard(state.data)
                            viewModel.resetState() // Prevent re-triggering on back press
                            finish()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@LoginActivity, "Login Failed", state.message)
                            viewModel.resetState()
                        }

                        else -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                        }
                    }
                }
            }
        }
    }

    private fun routeToDashboard(user: User) {
        val destination = when (user.role.lowercase()) { // Added lowercase for safety
            "admin" -> AdminDashboardActivity::class.java
            "teacher" -> TeacherDashboardActivity::class.java
            "student" -> StudentDashboardActivity::class.java
            else -> null
        }

        if (destination != null) {
            startActivity(Intent(this, destination))
        } else {
            DialogUtils.showAlert(this, "Access Denied", "Invalid user role assigned. Please contact support.")
        }
    }
}