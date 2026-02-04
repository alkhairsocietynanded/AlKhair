package com.aewsn.alkhair.ui.user

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayoutMediator
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ActivityUserDetailBinding
import com.aewsn.alkhair.ui.user.adapters.UserDetailPagerAdapter
import com.aewsn.alkhair.ui.user.fragments.AttendanceFragment
import com.aewsn.alkhair.ui.user.fragments.FeesFragment
import com.aewsn.alkhair.ui.user.fragments.ProfileFragment
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.getParcelableCompat
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

        // ✅ Insets handle karo - Apply top padding to AppBarLayout only
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        setupToolbar()

        val user = intent.extras?.getParcelableCompat("user", User::class.java)
        // Get user details from intent


        supportActionBar?.title = user?.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fragments = if (user?.role == Roles.STUDENT) {
            listOf(
                "Profile" to ProfileFragment.newInstance(user!!),
                "Attendance" to AttendanceFragment.newInstance(user!!),
                "Fees" to FeesFragment.newInstance(user!!)
            )
        } else {
            listOf(
                "Profile" to ProfileFragment.newInstance(user!!),
                "Attendance" to AttendanceFragment.newInstance(user!!),
//                "Salary" to FeesFragment.newInstance(user!!)
            )
        }

        pagerAdapter = UserDetailPagerAdapter(this, fragments)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getTitle(position)
        }.attach()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
