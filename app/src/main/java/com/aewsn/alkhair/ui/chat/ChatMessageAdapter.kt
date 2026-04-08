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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter(
    private val currentUserId: String,
    private val isAdmin: Boolean = false,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val selectedMessageIds = mutableSetOf<String>()

    fun toggleSelection(messageId: String) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId)
        } else {
            selectedMessageIds.add(messageId)
        }
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

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

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

    /** Bind media views (shared by Sent and Received) */
    private fun bindMedia(
        ivMedia: android.widget.ImageView,
        llDocument: android.view.View,
        tvDocumentName: android.widget.TextView?,
        message: ChatMessage
    ) {
        when (message.mediaType) {
            "image" -> {
                ivMedia.isVisible = true
                llDocument.isVisible = false
                Glide.with(ivMedia.context)
                    .load(message.mediaUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(ivMedia)
            }
            "document" -> {
                ivMedia.isVisible = false
                llDocument.isVisible = true
                // Extract filename from URL
                val fileName = message.mediaUrl?.substringAfterLast("/")?.let {
                    // Remove timestamp prefix (e.g. "1234567890_filename.pdf")
                    it.substringAfter("_").ifEmpty { it }
                } ?: "document"
                tvDocumentName?.text = fileName
            }
            else -> {
                ivMedia.isVisible = false
                llDocument.isVisible = false
            }
        }
    }

    inner class SentViewHolder(
        private val binding: ItemChatSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvMessageText.text = message.messageText
            binding.tvMessageText.isVisible = message.messageText.isNotEmpty()
            binding.tvTimestamp.text = timeFormat.format(Date(message.updatedAt))

            bindMedia(binding.ivMedia, binding.llDocument, binding.tvDocumentName, message)

            val isSelected = selectedMessageIds.contains(message.id)
            binding.root.setBackgroundColor(if (isSelected) getSelectionColor(binding.root.context) else Color.TRANSPARENT)

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
            binding.tvTimestamp.text = timeFormat.format(Date(message.updatedAt))

            bindMedia(binding.ivMedia, binding.llDocument, binding.tvDocumentName, message)

            val isSelected = selectedMessageIds.contains(message.id)
            binding.root.setBackgroundColor(if (isSelected) getSelectionColor(binding.root.context) else Color.TRANSPARENT)

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
