package com.zabibtech.alkhair.ui.user

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayoutMediator
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserDetailBinding
import com.zabibtech.alkhair.ui.user.adapters.UserDetailPagerAdapter
import com.zabibtech.alkhair.ui.user.fragments.AttendanceFragment
import com.zabibtech.alkhair.ui.user.fragments.FeesFragment
import com.zabibtech.alkhair.ui.user.fragments.ProfileFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDetailBinding
    private lateinit var pagerAdapter: UserDetailPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Insets handle karo
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        @Suppress("DEPRECATION")
        val user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("user", User::class.java)
        } else {
            intent.getParcelableExtra("user")
        }
        // Get user details from intent

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = user?.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fragments = listOf(
            "Profile" to ProfileFragment.newInstance(user!!),
            "Attendance" to AttendanceFragment.newInstance(user!!),
            "Fees" to FeesFragment.newInstance(user!!)
        )

        pagerAdapter = UserDetailPagerAdapter(this, fragments)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getTitle(position)
        }.attach()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
