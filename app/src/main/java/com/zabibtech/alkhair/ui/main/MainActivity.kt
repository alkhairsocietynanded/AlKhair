package com.zabibtech.alkhair.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.ui.auth.LoginActivity
import com.zabibtech.alkhair.ui.dashboard.AdminDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.StudentDashboardActivity
import com.zabibtech.alkhair.ui.dashboard.TeacherDashboardActivity
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        progressBar = findViewById(R.id.progressBar)

        observeUserSession()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun observeUserSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userSessionState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            progressBar.visibility = View.GONE
                            val user = state.data
                            if (user == null) {
                                goToLogin()
                            } else {
                                routeToDashboard(user)
                            }
                            finish()
                        }

                        is UiState.Error -> {
                            progressBar.visibility = View.GONE
                            goToLogin()
                            finish()
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun routeToDashboard(user: User) {
        val cleanRole = user.role.trim().lowercase()
        Log.d("MainActivity", "Routing user: ${user.name} ($cleanRole)")

        val destination = when (cleanRole) {
            "admin" -> AdminDashboardActivity::class.java
            "teacher" -> TeacherDashboardActivity::class.java
            "student" -> StudentDashboardActivity::class.java
            else -> {
                Log.e("MainActivity", "Unknown role: $cleanRole")
                LoginActivity::class.java
            }
        }
        startActivity(Intent(this, destination))
    }
}