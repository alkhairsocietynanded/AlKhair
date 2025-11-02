package com.zabibtech.alkhair.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.ActivityStudentDashboardBinding
import com.zabibtech.alkhair.ui.user.UserDetailActivity
import com.zabibtech.alkhair.ui.user.UserFormActivity
import com.zabibtech.alkhair.utils.LogoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StudentDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentDashboardBinding

    @Inject
    lateinit var logoutManager: LogoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnViewAttendance.setOnClickListener {
            // TODO: show attendance details
        }

        binding.btnViewFees.setOnClickListener {
            // TODO: show fees details
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, UserDetailActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            logoutManager.logout(this)
        }

    }
}