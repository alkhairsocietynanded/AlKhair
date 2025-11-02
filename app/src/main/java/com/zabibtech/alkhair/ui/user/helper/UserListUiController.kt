package com.zabibtech.alkhair.ui.user.helper

import android.content.Intent
import android.view.View
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

    fun setupListeners(role: String, classId: String?, division: String?, currentShift: String?) {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadUsers(role)
        }

        binding.fabAddUser.setOnClickListener {
            val intent = Intent(activity, UserFormActivity::class.java).apply {
                putExtra("role", role)
                putExtra("mode", Modes.CREATE)
                putExtra("classId", classId)
                putExtra("division", division)
                putExtra("currentShift", currentShift ?: "All")
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
            is UiState.Loading -> {
                // SwipeRefreshLayout will handle animation
            }

            is UiState.Success -> {
                val filteredList = filterUsers(
                    users = state.data,
                    role = role,
                    classId = classId,
                    shift = currentShift ?: "All"
                )
                adapter.setFullList(filteredList)

                binding.recyclerView.visibility =
                    if (filteredList.isEmpty()) View.GONE else View.VISIBLE
                binding.emptyView.visibility =
                    if (filteredList.isEmpty()) View.VISIBLE else View.GONE
            }

            is UiState.Error -> {
                binding.swipeRefreshLayout.isRefreshing = false
                DialogUtils.showAlert(activity, "Error", state.message)
            }

            else -> {}
        }
    }

    fun handleUserState(state: UiState<User>, role: String) {
        when (state) {
            is UiState.Loading -> binding.swipeRefreshLayout.isRefreshing = true
            is UiState.Success -> {
                binding.swipeRefreshLayout.isRefreshing = false
                viewModel.loadUsers(role)
            }

            is UiState.Error -> {
                binding.swipeRefreshLayout.isRefreshing = false
                DialogUtils.showAlert(activity, "Error", state.message)
            }

            else -> {}
        }
    }

    fun confirmDelete(user: User) {
        DialogUtils.showConfirmation(
            activity,
            title = "Confirm Deletion",
            message = "Are you sure you want to delete ${user.name}?",
            onConfirmed = { viewModel.deleteUser(user.uid) }
        )
    }
}