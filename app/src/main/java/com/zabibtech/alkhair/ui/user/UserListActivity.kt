package com.zabibtech.alkhair.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.zabibtech.alkhair.R
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

        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        role = intent.getStringExtra("role") ?: Roles.STUDENT
        classId = intent.getStringExtra("classId")
        mode = intent.getStringExtra("mode") ?: Modes.CREATE

        setupRecyclerView()

        uiController = UserListUiController(
            this,
            binding,
            adapter,
            userViewModel
        ) { intent -> userFormLauncher.launch(intent) }

        uiController.setupListeners(role, classId)
        setupObservers()
        setupChipFilterListeners()
        binding.chipGroupShift.check(R.id.chipAll)

        userViewModel.loadUsers(role)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.user_list_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search by name..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // The adapter's filter will handle the search
                adapter.filter.filter(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // The adapter's filter will handle the search
                adapter.filter.filter(newText)
                return true
            }
        })
        return true
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
                    DialogUtils.showConfirmation(
                        this,
                        title = "Confirm Deletion",
                        message = "Are you sure you want to delete ${user.name}?",
                        onConfirmed = { userViewModel.deleteUser(user.uid) }
                    )
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

    private fun setupChipFilterListeners() {
        binding.chipGroupShift.setOnCheckedStateChangeListener { group, checkedIds ->
            // Since singleSelection is true, we can safely take the first ID.
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentShift = when (checkedIds.first()) {
                R.id.chipSubah -> "Subah"
                R.id.chipDopahar -> "Dopahar"
                R.id.chipShaam -> "Shaam"
                else -> "All"
            }
            // Trigger a reload of the user list with the new filter
            userViewModel.loadUsers(role)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    // The UI controller will now correctly handle the state
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
    }
}
