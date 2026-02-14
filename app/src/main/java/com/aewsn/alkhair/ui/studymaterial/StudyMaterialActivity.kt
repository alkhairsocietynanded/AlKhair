package com.aewsn.alkhair.ui.studymaterial

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
import com.aewsn.alkhair.databinding.ActivityStudyMaterialBinding
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudyMaterialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudyMaterialBinding
    private val viewModel: StudyMaterialViewModel by viewModels()
    private lateinit var adapter: StudyMaterialAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudyMaterialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        binding.swipeRefreshLayout.setOnRefreshListener {
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
    private var pendingDownloadTitle: String = ""
    private var pendingDownloadSubject: String = ""

    private fun setupRecyclerView() {
        adapter = StudyMaterialAdapter { material, action ->
            when (action) {
                "download" -> {
                    material.attachmentUrl?.let { url ->
                        pendingDownloadUrl = url
                        pendingDownloadTitle = material.title
                        pendingDownloadSubject = material.subject
                        checkPermissionAndDownload(url)
                    }
                }
                "open_link" -> {
                    material.attachmentUrl?.let { url ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "edit" -> {
                    val dialog = AddStudyMaterialDialog.newInstance(material)
                    dialog.show(supportFragmentManager, "AddStudyMaterialDialog")
                }
                "delete" -> {
                    showDeleteConfirmation(material)
                }
            }
        }
        binding.rvStudyMaterial.layoutManager = LinearLayoutManager(this)
        binding.rvStudyMaterial.adapter = adapter
    }

    private fun showDeleteConfirmation(material: com.aewsn.alkhair.data.models.StudyMaterial) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Study Material")
            .setMessage("Are you sure you want to delete '${material.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMaterial(material.id)
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

            request.setTitle("$pendingDownloadSubject - $pendingDownloadTitle")
            request.setDescription("Downloading study material...")

            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                "${pendingDownloadSubject}_${pendingDownloadTitle}.pdf"
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
                    viewModel.materialListState.collectLatest { state ->
                        binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading

                        when (state) {
                            is UiState.Success -> {
                                val list = state.data
                                adapter.submitList(list)
                                binding.tvEmptyState.isVisible = list.isEmpty()
                            }
                            is UiState.Error -> {
                                Toast.makeText(this@StudyMaterialActivity, state.message, Toast.LENGTH_LONG).show()
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
                            binding.fabAddMaterial.isVisible = true
                            binding.fabAddMaterial.setOnClickListener {
                                AddStudyMaterialDialog.newInstance(null).show(supportFragmentManager, "AddStudyMaterialDialog")
                            }
                        } else {
                            binding.fabAddMaterial.isVisible = false
                        }
                    }
                }
            }
        }
    }
}
