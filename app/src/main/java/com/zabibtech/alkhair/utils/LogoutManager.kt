package com.zabibtech.alkhair.utils

import android.content.Context
import android.content.Intent
import com.zabibtech.alkhair.data.manager.AppDataSyncManager
import com.zabibtech.alkhair.data.manager.AuthRepoManager
import com.zabibtech.alkhair.di.ApplicationScope
import com.zabibtech.alkhair.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutManager @Inject constructor(
    private val authRepoManager: AuthRepoManager,
    private val appDataSyncManager: AppDataSyncManager,
    @param:ApplicationScope private val externalScope: CoroutineScope
) {
    fun logout(context: Context) {
        externalScope.launch {
            // 1. Clear Local Data (Room + Prefs)
            appDataSyncManager.clearAllLocalData()

            // 2. Sign out from Supabase (Handled by AuthRepoManager)
            authRepoManager.logout()

            // 3. Navigate to Login
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
    }
}
