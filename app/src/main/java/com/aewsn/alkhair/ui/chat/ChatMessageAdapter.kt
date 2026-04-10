package com.aewsn.alkhair.ui.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.databinding.ItemChatReceivedBinding
import com.aewsn.alkhair.databinding.ItemChatSentBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WhatsApp-style chat adapter.
 *
 * Media states:
 *  • NOT_DOWNLOADED  → dark overlay + ic_download — click triggers download
 *  • DOWNLOADING     → dark overlay + ProgressBar (spinner)
 *  • DOWNLOADED      → no overlay, localUri loaded via Glide (image) or attachment icon (doc)
 *
 * Clicking a DOWNLOADED file calls [onOpenMedia].
 */
class ChatMessageAdapter(
    var currentUserId: String,
    private val isAdmin: Boolean = false,
    private val onSelectionChanged: (selectedCount: Int) -> Unit,
    private val onDownloadMedia: (ChatMessage) -> Unit,
    private val onOpenMedia: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem && oldItem.localUri == newItem.localUri
        }
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val selectedMessageIds = mutableSetOf<String>()

    private fun getFormattedTime(timeMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        val today = java.util.Calendar.getInstance()
        
        val timeStr = timeFormat.format(cal.time)
        
        return if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                   cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) {
            timeStr
        } else if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                   cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) - 1) {
            "Yesterday, $timeStr"
        } else {
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            "${dateFormat.format(cal.time)}, $timeStr"
        }
    }

    // In-memory set of IDs currently being downloaded — updated by Activity from ViewModel
    private var downloadingIds: Set<String> = emptySet()

    /** Called by Activity when ViewModel's downloadingIds state changes */
    fun updateDownloadingIds(ids: Set<String>) {
        if (downloadingIds != ids) {
            downloadingIds = ids
            notifyDataSetChanged()
        }
    }

    // ─── Selection helpers ────────────────────────────────────────────────────

    fun toggleSelection(messageId: String) {
        if (selectedMessageIds.contains(messageId)) selectedMessageIds.remove(messageId)
        else selectedMessageIds.add(messageId)
        notifyDataSetChanged()
        onSelectionChanged(selectedMessageIds.size)
    }

    fun clearSelection() {
        selectedMessageIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedIds(): List<String> = selectedMessageIds.toList()

    private fun getSelectionColor(context: android.content.Context): Int {
        val color = androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_secondary)
        return Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ─── ViewHolder factory ───────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(ItemChatSentBinding.inflate(inflater, parent, false))
            else -> ReceivedViewHolder(ItemChatReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    // ─── Shared media binding logic ───────────────────────────────────────────

    /**
     * Binds media views for both Sent and Received items.
     *
     * @param flImageContainer  FrameLayout wrapping ivMedia + overlay
     * @param ivMedia           ImageView for actual image
     * @param ivMediaOverlay    Dark overlay FrameLayout
     * @param ivDownloadIcon    Download icon inside overlay
     * @param pbImageDownload   Spinner inside overlay
     * @param llDocument        Document row LinearLayout
     * @param ivDocIcon         Document icon or download icon
     * @param pbDocDownload     Document download spinner
     * @param tvDocumentName    Document filename label
     */
    private fun bindMedia(
        flImageContainer: android.widget.FrameLayout,
        ivMedia: android.widget.ImageView,
        ivMediaOverlay: android.view.View,
        ivDownloadIcon: android.widget.ImageView,
        pbImageDownload: android.widget.ProgressBar,
        llDocument: android.view.View,
        ivDocIcon: android.widget.ImageView,
        pbDocDownload: android.widget.ProgressBar,
        tvDocumentName: android.widget.TextView,
        message: ChatMessage
    ) {
        val isDownloading = downloadingIds.contains(message.id)
        val localUri = message.localUri
        val hasLocalFile = !localUri.isNullOrBlank() && File(localUri).exists()

        when (message.mediaType) {

            // ════════════════════════════════════════
            //  IMAGE
            // ════════════════════════════════════════
            "image" -> {
                flImageContainer.isVisible = true
                llDocument.isVisible = false

                when {
                    // STATE 1 — Downloading: show spinner only
                    isDownloading -> {
                        ivMediaOverlay.isVisible = true
                        ivDownloadIcon.isVisible = false
                        pbImageDownload.isVisible = true
                        // Blur placeholder using a low-res version
                        Glide.with(ivMedia.context)
                            .load(R.drawable.ic_image)
                            .into(ivMedia)
                    }

                    // STATE 2 — Downloaded: show actual image, no overlay
                    hasLocalFile -> {
                        ivMediaOverlay.isVisible = false
                        ivDownloadIcon.isVisible = false
                        pbImageDownload.isVisible = false
                        Glide.with(ivMedia.context)
                            .load(File(localUri!!))
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .placeholder(R.drawable.ic_image)
                            .into(ivMedia)
                        // Open on tap
                        flImageContainer.setOnClickListener {
                            if (selectedMessageIds.isEmpty()) onOpenMedia(message)
                        }
                    }

                    // STATE 3 — Not downloaded: placeholder + download icon overlay
                    else -> {
                        ivMediaOverlay.isVisible = true
                        ivDownloadIcon.isVisible = true
                        pbImageDownload.isVisible = false
                        // Grey placeholder bg
                        ivMedia.setImageResource(R.drawable.ic_image)
                        ivMedia.scaleType = android.widget.ImageView.ScaleType.CENTER
                        // Trigger download on overlay tap
                        ivMediaOverlay.setOnClickListener {
                            if (selectedMessageIds.isEmpty()) onDownloadMedia(message)
                        }
                        flImageContainer.setOnClickListener {
                            if (selectedMessageIds.isEmpty()) onDownloadMedia(message)
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            //  DOCUMENT
            // ════════════════════════════════════════
            "document" -> {
                flImageContainer.isVisible = false
                llDocument.isVisible = true

                val fileName = message.mediaUrl
                    ?.substringAfterLast("/")
                    ?.substringAfter("_")
                    ?.ifEmpty { "document" } ?: "document"
                tvDocumentName.text = fileName

                when {
                    // STATE 1 — Downloading
                    isDownloading -> {
                        ivDocIcon.isVisible = false
                        pbDocDownload.isVisible = true
                        llDocument.setOnClickListener(null)
                    }

                    // STATE 2 — Downloaded
                    hasLocalFile -> {
                        ivDocIcon.isVisible = true
                        ivDocIcon.setImageResource(R.drawable.ic_attachment)
                        pbDocDownload.isVisible = false
                        llDocument.setOnClickListener {
                            if (selectedMessageIds.isEmpty()) onOpenMedia(message)
                        }
                    }

                    // STATE 3 — Not downloaded
                    else -> {
                        ivDocIcon.isVisible = true
                        ivDocIcon.setImageResource(R.drawable.ic_download)
                        pbDocDownload.isVisible = false
                        llDocument.setOnClickListener {
                            if (selectedMessageIds.isEmpty()) onDownloadMedia(message)
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            //  No media
            // ════════════════════════════════════════
            else -> {
                flImageContainer.isVisible = false
                llDocument.isVisible = false
            }
        }
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class SentViewHolder(
        private val binding: ItemChatSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvMessageText.text = message.messageText
            binding.tvMessageText.isVisible = message.messageText.isNotEmpty()
            binding.tvTimestamp.text = getFormattedTime(message.updatedAt)

            // Sync status tick
            if (message.isSynced) {
                binding.ivSyncStatus.setImageResource(R.drawable.ic_check)
            } else {
                binding.ivSyncStatus.setImageResource(R.drawable.ic_pending)
            }

            bindMedia(
                flImageContainer = binding.flImageContainer,
                ivMedia = binding.ivMedia,
                ivMediaOverlay = binding.ivMediaOverlay,
                ivDownloadIcon = binding.ivDownloadIcon,
                pbImageDownload = binding.pbImageDownload,
                llDocument = binding.llDocument,
                ivDocIcon = binding.ivDocIcon,
                pbDocDownload = binding.pbDocDownload,
                tvDocumentName = binding.tvDocumentName,
                message = message
            )

            val isSelected = selectedMessageIds.contains(message.id)
            binding.root.setBackgroundColor(
                if (isSelected) getSelectionColor(binding.root.context) else Color.TRANSPARENT
            )

            binding.root.setOnClickListener {
                if (selectedMessageIds.isNotEmpty()) toggleSelection(message.id)
            }
            binding.root.setOnLongClickListener {
                if (selectedMessageIds.isEmpty()) toggleSelection(message.id)
                true
            }
        }
    }

    inner class ReceivedViewHolder(
        private val binding: ItemChatReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvSenderName.text = message.senderName.ifEmpty { "Unknown" }
            binding.tvMessageText.text = message.messageText
            binding.tvMessageText.isVisible = message.messageText.isNotEmpty()
            binding.tvTimestamp.text = getFormattedTime(message.updatedAt)

            bindMedia(
                flImageContainer = binding.flImageContainer,
                ivMedia = binding.ivMedia,
                ivMediaOverlay = binding.ivMediaOverlay,
                ivDownloadIcon = binding.ivDownloadIcon,
                pbImageDownload = binding.pbImageDownload,
                llDocument = binding.llDocument,
                ivDocIcon = binding.ivDocIcon,
                pbDocDownload = binding.pbDocDownload,
                tvDocumentName = binding.tvDocumentName,
                message = message
            )

            val isSelected = selectedMessageIds.contains(message.id)
            binding.root.setBackgroundColor(
                if (isSelected) getSelectionColor(binding.root.context) else Color.TRANSPARENT
            )

            if (isAdmin) {
                binding.root.setOnClickListener {
                    if (selectedMessageIds.isNotEmpty()) toggleSelection(message.id)
                }
                binding.root.setOnLongClickListener {
                    if (selectedMessageIds.isEmpty()) toggleSelection(message.id)
                    true
                }
            } else {
                binding.root.setOnClickListener(null)
                binding.root.setOnLongClickListener(null)
            }
        }
    }
}
