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
import com.zabibtech.alkhair.databinding.ActivityLoginBinding
import com.zabibtech.alkhair.ui.dashboard.AdminDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.StudentDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.TeacherDashboardActivity
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
            startActivity(Intent(this, StudentSignupActivity::class.java))
            finish()
        }

        viewModel.state.observe(this, Observer { state ->
            when (state) {
                is UiState.Loading -> {
                    Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()
                }

                is UiState.Success -> {
                    val user = state.data
                    routeToDashboard(user)
                }

                is UiState.Error -> {
//                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
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
