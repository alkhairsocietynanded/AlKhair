package com.aewsn.alkhair.ui.timetable

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.ActivityTimetableManagementBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TimetableManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimetableManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimetableManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            val isAdmin = intent.getBooleanExtra("IS_ADMIN", false)
            val fragment = TimetableFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("IS_ADMIN", isAdmin)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }
}
