package com.aewsn.alkhair.ui.aichat

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.ActivityAiChatBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, AiChatFragment.newInstance())
                .commitNow()
        }

        // Fix Status Bar for Dark Header (White Icons)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            // Only apply bottom/horizontal insets to root. Top inset is handled by the Fragment's header.
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
