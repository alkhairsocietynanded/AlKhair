package com.aewsn.alkhair.ui.syllabus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.databinding.ActivitySyllabusScreenBinding
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SyllabusActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyllabusScreenBinding
    private val viewModel: SyllabusViewModel by viewModels()
    private lateinit var adapter: SyllabusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyllabusScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            // Room is SSOT, so just stop refreshing or trigger a sync if needed manually
            // For now, we rely on the background sync
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingDownloadUrl?.let { url ->
                startDownload(url)
            }
        } else {
            Toast.makeText(this, "Permission denied. Cannot download.", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingDownloadUrl: String? = null
    private var pendingDownloadSubject: String = ""
    private var pendingDownloadTopic: String = ""

    private fun setupRecyclerView() {
        adapter = SyllabusAdapter { syllabus, action ->
            when (action) {
                "download" -> {
                    syllabus.attachmentUrl?.let { url ->
                        pendingDownloadUrl = url
                        pendingDownloadSubject = syllabus.subject
                        pendingDownloadTopic = syllabus.topic
                        checkPermissionAndDownload(url)
                    }
                }
                "edit" -> {
                    val dialog = AddSyllabusDialog.newInstance(syllabus)
                    dialog.show(supportFragmentManager, "AddSyllabusDialog")
                }
                "delete" -> {
                    showDeleteConfirmation(syllabus)
                }
            }
        }
        binding.rvSyllabus.layoutManager = LinearLayoutManager(this)
        binding.rvSyllabus.adapter = adapter
    }

    private fun showDeleteConfirmation(syllabus: com.aewsn.alkhair.data.models.Syllabus) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Syllabus")
            .setMessage("Are you sure you want to delete '${syllabus.subject} - ${syllabus.topic}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSyllabus(syllabus.id)
                Toast.makeText(this, "Deleting...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissionAndDownload(url: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startDownload(url)
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            startDownload(url)
        }
    }

    private fun startDownload(url: String) {
        try {
            val downloadManager = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = Uri.parse(url)
            val request = android.app.DownloadManager.Request(uri)

            // Set title and description
            request.setTitle("$pendingDownloadSubject - $pendingDownloadTopic")
            request.setDescription("Downloading attachment...")

            // Set destination
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                "${pendingDownloadSubject}_${pendingDownloadTopic}.pdf"
            )

            downloadManager.enqueue(request)
            Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.syllabusListState.collectLatest { state ->
                        binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading
                        
                        when (state) {
                            is UiState.Success -> {
                                val list = state.data
                                adapter.submitList(list)
                                binding.tvEmptyState.isVisible = list.isEmpty()
                            }
                            is UiState.Error -> {
                                Toast.makeText(this@SyllabusActivity, state.message, Toast.LENGTH_LONG).show()
                                binding.tvEmptyState.isVisible = adapter.currentList.isEmpty()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.currentUser.collectLatest { user ->
                        val canEdit = user != null && (user.role == "admin" || user.role == "teacher")
                        adapter.isEditEnabled = canEdit
                        
                         if (canEdit) {
                            binding.fabAddSyllabus.isVisible = true
                            binding.fabAddSyllabus.setOnClickListener {
                                AddSyllabusDialog.newInstance(null).show(supportFragmentManager, "AddSyllabusDialog")
                            }
                        } else {
                            binding.fabAddSyllabus.isVisible = false
                        }
                    }
                }
            }
        }
    }
}
