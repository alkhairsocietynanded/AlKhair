package com.aewsn.alkhair.ui.student

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.ActivityStudentBinding
import com.aewsn.alkhair.ui.auth.LoginActivity
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.get

@AndroidEntryPoint
class StudentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentBinding
    private val viewModel: StudentViewModel by viewModels()

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, StudentActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentUser.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            val user = state.data
                            setupNavigation(user)
                            setupAskAiFab(user)
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@StudentActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                        is UiState.Loading -> {
                            // Optionally show loading
                        }
                        else -> {
                            // Idle or other states
                        }
                    }
                }
            }
        }
    }

    private fun setupWindowInsets() {
        val navContainer = findViewById<android.view.View>(R.id.navContainer)
        val bottomNav = findViewById<android.view.View>(R.id.bottomNav)

        // Prevent BottomNavigationView from adding system bottom inset as padding
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val horizontalPadding = 16.dpToPx(this)
            v.setPadding(horizontalPadding, 0, horizontalPadding, 0)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            // Lift the floating bottom nav above the system navigation bar
            val params = navContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + 16.dpToPx(this)
            navContainer.layoutParams = params
            
            insets
        }
    }
    
    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private fun setupNavigation(user: User) {
        val adapter = StudentPagerAdapter(this, user)
        binding.viewPager.adapter = adapter
        
        // Disable swipe if desired, but user asked for it. 
        // We ensure off-screen limit to keep fragments alive
        binding.viewPager.offscreenPageLimit = 2

        // 1. Sync ViewPager -> BottomNav
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNav.menu[position].isChecked = true
            }
        })

        // 2. Sync BottomNav -> ViewPager
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.viewPager.currentItem = 0
                R.id.nav_homework -> binding.viewPager.currentItem = 1
                R.id.nav_fees -> binding.viewPager.currentItem = 2
                R.id.nav_attendance -> binding.viewPager.currentItem = 3
                R.id.nav_profile -> binding.viewPager.currentItem = 4
            }
            true
        }

        // Fix: Set Active Indicator Color programmatically to ensure it applies
        binding.bottomNav.itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, R.color.nav_highlighter)
        )

        // Custom Back Navigation: Any Fragment -> Dashboard -> Exit
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.viewPager.currentItem != 0) {
                    binding.viewPager.currentItem = 0
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupAskAiFab(user: User) {
        if (user.role == com.aewsn.alkhair.utils.Roles.ADMIN || user.role == com.aewsn.alkhair.utils.Roles.TEACHER) {
            binding.fabAskAi.visibility = android.view.View.VISIBLE
            binding.fabAskAi.setOnClickListener {
                startActivity(Intent(this, com.aewsn.alkhair.ui.chat.ChatActivity::class.java))
            }
        } else {
            binding.fabAskAi.visibility = android.view.View.GONE
        }
    }
}