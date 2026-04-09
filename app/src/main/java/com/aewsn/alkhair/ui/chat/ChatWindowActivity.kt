package com.aewsn.alkhair.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.databinding.ActivityChatWindowBinding
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ChatWindowActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_GROUP_TYPE = "group_type"
        const val EXTRA_GROUP_NAME = "group_name"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_USER_ROLE = "user_role"
        private const val TAG = "ChatWindowActivity"
    }

    private lateinit var binding: ActivityChatWindowBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatMessageAdapter

    private var groupId: String = ""
    private var groupType: String = ""
    private var groupName: String = ""
    private var senderName: String = ""
    private var userRole: String = ""

    // ─── Pending media (selected but not yet sent) ───────────────────────────
    private var pendingMediaUri: Uri? = null
    private var pendingMimeType: String? = null
    private var pendingFileName: String? = null

    // ─── Back press callback for selection mode ───────────────────────────────
    private val backCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (adapter.getSelectedIds().isNotEmpty()) adapter.clearSelection()
        }
    }

    // ─── Gallery picker launcher ─────────────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val fileName = getFileName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
            setPendingMedia(uri, mimeType, fileName)
        }
    }

    // ─── Document picker launcher ────────────────────────────────────────────
    private val documentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            setPendingMedia(uri, mimeType, fileName)
        }
    }

    // ─── Permission launcher ─────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery() else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatWindowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: ""
        groupType = intent.getStringExtra(EXTRA_GROUP_TYPE) ?: ""
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME) ?: "Chat"
        senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: ""

        if (groupId.isEmpty()) {
            Toast.makeText(this, "Invalid chat group", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        setupAttachButton()
        observeMessages()
        observeSendState()
        observeDeleteState()
        observeDownloadingIds()

        viewModel.observeMessages(groupId, groupType)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Top padding for status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Bottom inset: IME (keyboard) when visible, else system nav bar
            val bottomInset = if (ime.bottom > 0) ime.bottom else systemBars.bottom

            // Apply to unified bottom container — preview + input bar move together
            binding.bottomContainer.setPadding(0, 0, 0, bottomInset)

            // RecyclerView bottom padding accounts for bottomContainer height (~80dp) + nav bar
            val density = resources.displayMetrics.density
            val containerHeight = (80 * density).toInt()
            binding.rvMessages.setPadding(
                0, (8 * density).toInt(), 0, bottomInset + containerHeight
            )

            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = groupName
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        val isAdmin = userRole.equals(Roles.ADMIN, ignoreCase = true)
        adapter = ChatMessageAdapter(
            currentUserId = viewModel.currentUserId,
            isAdmin = isAdmin,
            onSelectionChanged = { count -> updateSelectionMode(count) },
            onDownloadMedia = { message -> viewModel.downloadMedia(message) },
            onOpenMedia = { message -> openDownloadedMedia(message) }
        )
        val layoutManager = LinearLayoutManager(this).apply { reverseLayout = true }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    // ─── Selection mode toolbar ───────────────────────────────────────────────

    private fun updateSelectionMode(selectedCount: Int) {
        backCallback.isEnabled = selectedCount > 0
        if (selectedCount > 0) {
            binding.toolbar.menu.clear()
            menuInflater.inflate(R.menu.menu_chat_selection, binding.toolbar.menu)
            binding.toolbar.title = "$selectedCount selected"
            binding.toolbar.setNavigationOnClickListener { adapter.clearSelection() }
            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> { showDeleteConfirmation(); true }
                    else -> false
                }
            }
        } else {
            binding.toolbar.menu.clear()
            binding.toolbar.title = groupName
            binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            binding.toolbar.setOnMenuItemClickListener(null)
        }
    }

    private fun showDeleteConfirmation() {
        val selectedIds = adapter.getSelectedIds()
        val count = selectedIds.size
        MaterialAlertDialogBuilder(this)
            .setTitle(if (count > 1) "Delete Messages" else "Delete Message")
            .setMessage("Are you sure you want to delete ${if (count > 1) "$count messages" else "this message"}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMessages(selectedIds)
                adapter.clearSelection()
            }
            .show()
    }

    // ─── Attach button ────────────────────────────────────────────────────────

    private fun setupAttachButton() {
        binding.btnAttach.isVisible = true
        binding.btnAttach.setOnClickListener { showAttachBottomSheet() }
    }

    private fun showAttachBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)
        sheet.setContentView(view)

        view.findViewById<android.view.View>(R.id.optionGallery).setOnClickListener {
            sheet.dismiss()
            checkGalleryPermissionAndOpen()
        }
        view.findViewById<android.view.View>(R.id.optionDocument).setOnClickListener {
            sheet.dismiss()
            documentLauncher.launch("application/*")
        }

        sheet.show()
    }

    private fun checkGalleryPermissionAndOpen() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> openGallery()
            else -> permissionLauncher.launch(permission)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    // ─── Pending media preview ────────────────────────────────────────────────

    private fun setPendingMedia(uri: Uri, mimeType: String, fileName: String) {
        pendingMediaUri = uri
        pendingMimeType = mimeType
        pendingFileName = fileName

        // Show preview bar
        binding.mediaPreviewContainer.isVisible = true

        // Always show filename
        binding.tvMediaPreviewName.isVisible = true
        binding.tvMediaPreviewName.text = fileName

        // Always show image view — thumbnail for images, icon for documents
        binding.ivMediaPreview.isVisible = true

        if (mimeType.startsWith("image/")) {
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivMediaPreview)
        } else {
            binding.ivMediaPreview.setImageResource(R.drawable.ic_attachment)
            binding.ivMediaPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            binding.ivMediaPreview.setPadding(8, 8, 8, 8)
        }
    }

    private fun clearPendingMedia() {
        pendingMediaUri = null
        pendingMimeType = null
        pendingFileName = null
        binding.mediaPreviewContainer.isVisible = false
    }

    // ─── Send button ──────────────────────────────────────────────────────────

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim() ?: ""
            if (text.isEmpty() && pendingMediaUri == null) return@setOnClickListener

            val uri      = pendingMediaUri
            val mimeType = pendingMimeType
            val fileName = pendingFileName

            // Bytes read in Activity — URI permission is valid here
            val bytes: ByteArray? = if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    Log.e(TAG, "Media bytes read failed: ${e.message}")
                    null
                }
            } else null

            viewModel.sendMessage(
                text = text,
                senderName = senderName,
                mediaBytes = bytes,
                mimeType = mimeType,
                mediaFileName = fileName
            )

            binding.etMessage.text?.clear()
            clearPendingMedia()
        }

        binding.btnCancelMediaPreview.setOnClickListener { clearPendingMedia() }
    }

    // ─── Open downloaded media ────────────────────────────────────────────────

    /**
     * Opens a downloaded media file using Android's ACTION_VIEW intent via FileProvider.
     * This is the WhatsApp behaviour: tap downloaded file → system opens appropriate viewer.
     */
    private fun openDownloadedMedia(message: ChatMessage) {
        val localUri = message.localUri ?: return
        val file = File(localUri)
        if (!file.exists()) {
            Toast.makeText(this, "File not found, please re-download", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = when (message.mediaType) {
            "image" -> "image/*"
            "document" -> {
                val ext = file.extension.lowercase()
                when (ext) {
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/msword"
                    "xls", "xlsx" -> "application/vnd.ms-excel"
                    "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                    "txt" -> "text/plain"
                    else -> "*/*"
                }
            }
            else -> "*/*"
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open file: ${e.message}")
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            val messages = state.data
                            adapter.submitList(messages) {
                                if (messages.isNotEmpty()) binding.rvMessages.scrollToPosition(0)
                            }
                            binding.rvMessages.isVisible = messages.isNotEmpty()
                            binding.emptyState.isVisible = messages.isEmpty()
                        }
                        is UiState.Error -> { binding.emptyState.isVisible = true }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeSendState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sendState.collectLatest { state ->
                    when (state) {
                        is UiState.Error -> {
                            Toast.makeText(this@ChatWindowActivity, state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetSendState()
                        }
                        is UiState.Success -> viewModel.resetSendState()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeDeleteState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteState.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            Toast.makeText(this@ChatWindowActivity, "Deleted", Toast.LENGTH_SHORT).show()
                            viewModel.resetDeleteState()
                        }
                        is UiState.Error -> {
                            Toast.makeText(this@ChatWindowActivity, "Failed: ${state.message}", Toast.LENGTH_SHORT).show()
                            viewModel.resetDeleteState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * Observe which message IDs are currently being downloaded.
     * Forward the set to the adapter so it can show/hide ProgressBars.
     */
    private fun observeDownloadingIds() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadingIds.collectLatest { ids ->
                    adapter.updateDownloadingIds(ids)
                }
            }
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }
}
