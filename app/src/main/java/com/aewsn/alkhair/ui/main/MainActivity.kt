package com.aewsn.alkhair.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.aewsn.alkhair.databinding.ActivityMainBinding
import com.aewsn.alkhair.ui.auth.LoginActivity
import com.aewsn.alkhair.ui.dashboard.AdminDashboardActivity
import com.aewsn.alkhair.ui.student.StudentActivity
import com.aewsn.alkhair.ui.dashboard.TeacherDashboardActivity
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        observeUserSession()

        viewModel.checkUserSession()
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
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.userSessionState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val user = state.data
                            if (user == null) {
                                goToLogin()
                            } else {
                                routeToDashboard(user)
                            }
                            finish()
                        }

                        is UiState.Error -> {
                            binding.progressBar.visibility = View.GONE
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
            "student" -> StudentActivity::class.java
            else -> {
                Log.e("MainActivity", "Unknown role: $cleanRole")
                LoginActivity::class.java
            }
        }
        
        // Start dashboard as base
        val dashboardIntent = Intent(this, destination)
        startActivity(dashboardIntent)

        // Handle push notification routing
        val notificationType = intent.getStringExtra("type")
        if (notificationType != null) {
            Log.d("MainActivity", "Handling FCM deep link for type: $notificationType")
            when (notificationType) {
                "CHAT" -> {
                    val groupType = intent.getStringExtra("group_type")
                    val chatIntent = Intent(this, com.aewsn.alkhair.ui.chat.ChatWindowActivity::class.java).apply {
                        putExtras(intent.extras ?: Bundle())
                        putExtra(com.aewsn.alkhair.ui.chat.ChatWindowActivity.EXTRA_GROUP_NAME, if (groupType == "teachers") "Teachers Chat" else "Class Chat")
                        putExtra(com.aewsn.alkhair.ui.chat.ChatWindowActivity.EXTRA_SENDER_NAME, user.name)
                        putExtra(com.aewsn.alkhair.ui.chat.ChatWindowActivity.EXTRA_USER_ROLE, user.role)
                    }
                    startActivity(chatIntent)
                }
                "HOMEWORK" -> {
                    val hwIntent = Intent(this, com.aewsn.alkhair.ui.homework.HomeworkActivity::class.java).apply {
                        putExtras(intent.extras ?: Bundle())
                    }
                    startActivity(hwIntent)
                }
                "FEES" -> {
                    val feesIntent = Intent(this, com.aewsn.alkhair.ui.fees.FeesActivity::class.java).apply {
                        putExtras(intent.extras ?: Bundle())
                    }
                    startActivity(feesIntent)
                }
                // Announcement is already handled by Dashboard's native flow
            }
        }
    }
}
