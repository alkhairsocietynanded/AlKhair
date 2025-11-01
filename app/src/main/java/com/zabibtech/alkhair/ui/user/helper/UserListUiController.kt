package com.zabibtech.alkhair.ui.user.helper

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserListBinding
import com.zabibtech.alkhair.ui.user.UserAdapter
import com.zabibtech.alkhair.ui.user.UserFormActivity
import com.zabibtech.alkhair.ui.user.UserViewModel
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.UserFilterHelper.filterUsers

class UserListUiController(
    private val activity: AppCompatActivity,
    private val binding: ActivityUserListBinding,
    private val adapter: UserAdapter,
    private val viewModel: UserViewModel,
    private val userFormLauncher: (Intent) -> Unit
) {

    fun setupListeners(role: String, classId: String?) {
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadUsers(role) }

        binding.fabAddUser.setOnClickListener {
            val intent = Intent(activity, UserFormActivity::class.java).apply {
                putExtra("role", role)
                putExtra("mode", Modes.CREATE)
                putExtra("classId", classId)
            }
            userFormLauncher(intent)
        }
    }

    fun handleListState(
        state: UiState<List<User>>,
        role: String,
        classId: String?,
        currentShift: String?
    ) {
        when (state) {
            is UiState.Loading -> if (!binding.swipeRefreshLayout.isRefreshing)
                DialogUtils.showLoading(activity.supportFragmentManager)

            is UiState.Success -> {
                DialogUtils.hideLoading(activity.supportFragmentManager)
                binding.swipeRefreshLayout.isRefreshing = false

                // Primary filtering (role, class, shift)
                val filteredList = filterUsers(
                    users = state.data,
                    role = role,
                    classId = classId,
                    shift = currentShift ?: "All"
                )

                // Provide the full, pre-filtered list to the adapter.
                // The adapter will then handle the search filtering internally.
                adapter.setFullList(filteredList)

                binding.recyclerView.visibility =
                    if (filteredList.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                binding.emptyView.visibility =
                    if (filteredList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }

            is UiState.Error -> {
                DialogUtils.hideLoading(activity.supportFragmentManager)
                binding.swipeRefreshLayout.isRefreshing = false
                DialogUtils.showAlert(activity, "Error", state.message)
            }

            else -> DialogUtils.hideLoading(activity.supportFragmentManager)
        }
    }

    fun handleUserState(state: UiState<User>, role: String) {
        when (state) {
            is UiState.Loading -> DialogUtils.showLoading(activity.supportFragmentManager)
            is UiState.Success -> {
                DialogUtils.hideLoading(activity.supportFragmentManager)
                viewModel.loadUsers(role)
            }

            is UiState.Error -> {
                DialogUtils.hideLoading(activity.supportFragmentManager)
                DialogUtils.showAlert(activity, "Error", state.message)
            }

            else -> {}
        }
    }
}
