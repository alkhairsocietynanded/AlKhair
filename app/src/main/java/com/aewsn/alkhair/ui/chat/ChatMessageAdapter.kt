package com.aewsn.alkhair.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.ChatMessage
import com.aewsn.alkhair.databinding.ItemChatReceivedBinding
import com.aewsn.alkhair.databinding.ItemChatSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter(
    private val currentUserId: String,
    private val isAdmin: Boolean = false,
    private val onMessageLongPressed: (message: ChatMessage) -> Unit
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

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(
                ItemChatSentBinding.inflate(inflater, parent, false)
            )
            else -> ReceivedViewHolder(
                ItemChatReceivedBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    inner class SentViewHolder(
        private val binding: ItemChatSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvMessageText.text = message.messageText
            binding.tvTimestamp.text = timeFormat.format(Date(message.updatedAt))

            // ✅ chatBubble directly set karo — root FrameLayout touch nahi pakadta
            binding.chatBubble.isLongClickable = true
            binding.chatBubble.setOnLongClickListener {
                onMessageLongPressed(message)
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
            binding.tvTimestamp.text = timeFormat.format(Date(message.updatedAt))

            // ✅ chatBubble LinearLayout directly set karo
            if (isAdmin) {
                binding.chatBubble.isLongClickable = true
                binding.chatBubble.setOnLongClickListener {
                    onMessageLongPressed(message)
                    true
                }
            } else {
                binding.chatBubble.setOnLongClickListener(null)
                binding.chatBubble.isLongClickable = false
            }
        }
    }
}
