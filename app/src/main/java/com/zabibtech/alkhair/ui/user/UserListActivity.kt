package com.zabibtech.alkhair.ui.user

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zabibtech.alkhair.databinding.ActivityUserListBinding
import com.zabibtech.alkhair.ui.user.helper.UserListUiController
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private val userViewModel: UserViewModel by viewModels()

    private lateinit var adapter: UserAdapter
    private lateinit var uiController: UserListUiController

    private lateinit var mode: String
    private var role: String = Roles.STUDENT
    private var classId: String? = null
    private var currentShift: String? = "All"

    private val userFormLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            userViewModel.loadUsers(role)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        role = intent.getStringExtra("role") ?: Roles.STUDENT
        classId = intent.getStringExtra("classId")
        mode = intent.getStringExtra("mode") ?: Modes.CREATE

        setupRecyclerView()

        // 🔹 Init UI Controller
        uiController = UserListUiController(
            this,
            binding,
            adapter,
            userViewModel
        ) { intent -> userFormLauncher.launch(intent) }

        uiController.setupListeners(role, classId)
        setupObservers()

        // Default shift = All
        binding.radioGroupShift.check(com.zabibtech.alkhair.R.id.radioAll)
        userViewModel.loadUsers(role)
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            onEdit = { user ->
                if (mode == Modes.CREATE) {
                    val intent = Intent(this, UserFormActivity::class.java).apply {
                        putExtra("role", user.role)
                        putExtra("mode", Modes.UPDATE)
                        putExtra("user", user)
                    }
                    userFormLauncher.launch(intent)
                }
            },
            onDelete = { user ->
                if (mode == Modes.CREATE) {
                    DialogUtils.showAlert(
                        this,
                        "Confirm",
                        "Delete ${user.name}?",
                        "Yes"
                    ) { userViewModel.deleteUser(user.uid) }
                }
            },
            onClick = { user ->
                val intent = Intent(this, UserDetailActivity::class.java).apply {
                    putExtra("userId", user.uid)
                    putExtra("user", user)
                }
                startActivity(intent)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    uiController.handleListState(state, role, classId, currentShift)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userState.collectLatest { state ->
                    uiController.handleUserState(state, role)
                }
            }
        }

        binding.radioGroupShift.setOnCheckedChangeListener { _, checkedId ->
            currentShift = when (checkedId) {
                com.zabibtech.alkhair.R.id.radioSubah -> "Subah"
                com.zabibtech.alkhair.R.id.radioDopahar -> "Dopahar"
                com.zabibtech.alkhair.R.id.radioShaam -> "Shaam"
                else -> "All"
            }
            adapter.submitList(emptyList())
            userViewModel.loadUsers(role)
        }
    }
}
