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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        progressBar = findViewById(R.id.progressBar)

        // StateFlow observe karna
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
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
                                // ✅ Fetch and cache divisions & classes
                                viewModel.loadDivisionsAndClasses()

                                // ✅ Route user
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

        viewModel.checkUser()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun routeToDashboard(user: User) {
        // 1. Role ko saaf karein
        val cleanRole = user.role.trim().lowercase()

        Log.d("com.zabibtech.alkhair", "Routing user. Role from ViewModel is: '${user.role}'")

        // 2. Pehle check karein ke role valid hai ya nahi
        if (cleanRole !in listOf("admin", "teacher", "student")) {
            // Agar role in teeno mein se koi nahi hai, to foran Login par bhejein
            goToLogin()
            finish() // MainActivity ko band karna na bhoolein
            return   // Function se bahar nikal jayein
        }

        // 3. Agar role valid hai, tab hi aage barhein
        val destination = when (cleanRole) {
            "admin" -> AdminDashboardActivity::class.java
            "teacher" -> TeacherDashboardActivity::class.java
            "student" -> StudentDashboardActivity::class.java
            else -> LoginActivity::class.java // Ye ab sirf ek fallback hai
        }
        startActivity(Intent(this, destination))
    }

}
