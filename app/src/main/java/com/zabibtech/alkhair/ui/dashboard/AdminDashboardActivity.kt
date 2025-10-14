package com.zabibtech.alkhair.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.ActivityAdminDashboardBinding
import com.zabibtech.alkhair.ui.classmanager.ClassManagerActivity
import com.zabibtech.alkhair.ui.fees.FeesActivity
import com.zabibtech.alkhair.ui.user.UserListActivity
import com.zabibtech.alkhair.utils.LogoutManager
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminDashboardBinding

    @Inject
    lateinit var logoutManager: LogoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.cardTeachers.setOnClickListener {
            startActivity(
                Intent(this, UserListActivity::class.java)
                    .putExtra("role", "teacher")
                    .putExtra("mode", "create")
            )
        }

        binding.cardStudents.setOnClickListener {
            startActivity(
                Intent(this, UserListActivity::class.java)
                    .putExtra("role", "student")
                    .putExtra("mode", "create")
            )
        }
        /*  binding.cardUsers.setOnClickListener {
              startActivity(Intent(this, UserListActivity::class.java))
          }*/

        binding.cardClasses.setOnClickListener {
            startActivity(Intent(this, ClassManagerActivity::class.java))
        }
        binding.cardAttendance.setOnClickListener {
            // Attendance mode ke liye ClassManagerActivity khole
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.STUDENT)
            }
            startActivity(intent)
        }
        binding.cardTeacherAttendance.setOnClickListener {
            // Attendance mode ke liye ClassManagerActivity khole
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("mode", Modes.ATTENDANCE)
                putExtra("role", Roles.TEACHER)
            }
            startActivity(intent)
        }


        binding.cardFees.setOnClickListener {
            startActivity(Intent(this, FeesActivity::class.java))
        }

        binding.cardLogout.setOnClickListener {

            logoutManager.logout(this)
        }

    }
}