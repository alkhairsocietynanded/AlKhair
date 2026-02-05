package com.aewsn.alkhair.ui.approval

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LeaveApprovalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container_simple)

        if (savedInstanceState == null) {
            val user = intent.getParcelableExtra<User>("user")
            
            val fragment = if (user != null) {
                LeaveApprovalFragment.newInstance(user)
            } else {
                 // Fallback or Error
                 LeaveApprovalFragment()
            }
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
        }
        
        supportActionBar?.title = "Leave Approvals"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
