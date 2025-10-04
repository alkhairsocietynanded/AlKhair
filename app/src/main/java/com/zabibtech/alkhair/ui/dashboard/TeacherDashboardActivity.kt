package com.zabibtech.alkhair.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.ActivityTeacherDashboardBinding
import com.zabibtech.alkhair.ui.user.UserFormActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.LogoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TeacherDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTeacherDashboardBinding

    @Inject
    lateinit var logoutManager: LogoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTeacherDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnAddStudent.setOnClickListener {
            startActivity(
                Intent(this, UserFormActivity::class.java)
                    .putExtra("role", "student")
                    .putExtra("mode", "create")
            )
        }
        binding.btnManageUsers.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }
        binding.btnMarkAttendance.setOnClickListener {
            // TODO: open Attendance Marking Activity
        }

        binding.btnViewStudents.setOnClickListener {
            // TODO: open Student List Activity
        }

        binding.btnLogout.setOnClickListener {
            logoutManager.logout(this)
        }

    }
}