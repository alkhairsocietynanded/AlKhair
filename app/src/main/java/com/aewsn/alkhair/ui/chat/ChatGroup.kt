package com.aewsn.alkhair.ui.chat

/**
 * Data class representing a chat group item in ChatListActivity
 */
data class ChatGroup(
    val groupId: String,       // "teachers_group" or class UUID
    val groupType: String,     // "teachers" or "class"
    val groupName: String,     // Display name
    val subtitle: String = ""  // e.g. "Admin + Teachers" or "Class 5A"
)
