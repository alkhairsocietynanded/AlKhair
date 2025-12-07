package com.zabibtech.alkhair.utils

import android.content.Context
import android.content.Intent
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.ui.auth.LoginActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutManager @Inject constructor(
    private val authRepoManager: AuthRepoManager
) {
    fun logout(context: Context) {
        // Call logout from the single source of truth for authentication
        authRepoManager.logout()

        // UI redirect
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
