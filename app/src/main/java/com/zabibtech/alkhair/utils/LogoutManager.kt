package com.zabibtech.alkhair.utils

import android.content.Context
import android.content.Intent
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.repository.AuthRepository
import com.zabibtech.alkhair.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogoutManager @Inject constructor(
    private val authRepo: AuthRepository,
    private val sessionManager: UserStore
) {
    fun logout(context: Context) {
        // direct coroutine chalao background me
        CoroutineScope(Dispatchers.IO).launch {
            authRepo.logout()
            sessionManager.clearUser()
        }

        // UI redirect
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
