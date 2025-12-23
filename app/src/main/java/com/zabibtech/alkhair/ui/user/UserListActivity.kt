package com.zabibtech.alkhair.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
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
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.datastore.ShiftDataStore
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserListBinding
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.Shift
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private val userViewModel: UserViewModel by viewModels()

    @Inject
    lateinit var shiftDataStore: ShiftDataStore

    private lateinit var adapter: UserAdapter

    // Intent Data
    private lateinit var mode: String
    private var role: String = Roles.STUDENT
    private var classId: String? = null
    private var division: String? = null
    private var className: String? = null

    // State for FAB
    private var currentShift: String = Shift.ALL

    private val userFormLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // No action needed; DB updates -> Flow updates -> UI updates
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        extractIntentData()
        setupRecyclerView()
        setupListeners()
        setupObservers()

        // Initialize Filter State
        userViewModel.setInitialFilters(role, classId, Shift.ALL)
        restoreShiftState()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun extractIntentData() {
        mode = intent.getStringExtra("mode") ?: Modes.CREATE
        role = intent.getStringExtra("role") ?: Roles.STUDENT
        classId = intent.getStringExtra("classId")
        className = intent.getStringExtra("className")
        division = intent.getStringExtra("division")

        supportActionBar?.apply {
            title = buildString {
                if (!className.isNullOrEmpty() && role == Roles.STUDENT) {
                    append(className)
                } else if (role == Roles.TEACHER) {
                    append("Teachers")
                } else {
                    append("Students")
                }
            }
            subtitle = division ?: ""
        }
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            onEdit = { user ->
                val intent = Intent(this, UserFormActivity::class.java).apply {
                    putExtra("role", user.role)
                    putExtra("mode", Modes.UPDATE)
                    putExtra("user", user)
                }
                userFormLauncher.launch(intent)
            },
            onDelete = { user ->
                confirmDelete(user)
            },
            onClick = { user ->
                val intent = Intent(this, UserDetailActivity::class.java).apply {
                    putExtra("userId", user.uid)
                    putExtra("user", user)
                }
                startActivity(intent)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@UserListActivity)
            adapter = this@UserListActivity.adapter
        }
    }

    private fun setupListeners() {
        // FAB Click
        binding.fabAddUser.setOnClickListener {
            val intent = Intent(this, UserFormActivity::class.java).apply {
                putExtra("role", role)
                putExtra("mode", Modes.CREATE)
                putExtra("classId", classId)
                putExtra("division", division)
                putExtra("currentShift", currentShift)
            }
            userFormLauncher.launch(intent)
        }

        // Swipe Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            // Flow is live, no need to manually reload.
            // If you had a sync mechanism, you would trigger it here.
            binding.swipeRefreshLayout.isRefreshing = false
        }

        // Shift Chips
        binding.chipGroupShift.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val selectedShift = when (checkedIds.first()) {
                R.id.chipSubah -> Shift.SUBAH
                R.id.chipDopahar -> Shift.DOPAHAR
                R.id.chipShaam -> Shift.SHAAM
                else -> Shift.ALL
            }

            // Update local state for FAB
            currentShift = selectedShift

            // Save preference
            lifecycleScope.launch {
                shiftDataStore.saveShift(selectedShift)
            }

            // Update VM Filter
            userViewModel.setShiftFilter(selectedShift)
        }
    }

    private fun restoreShiftState() {
        lifecycleScope.launch {
            val savedShift = shiftDataStore.getShift()
            val chipId = when (savedShift) {
                Shift.SUBAH -> R.id.chipSubah
                Shift.DOPAHAR -> R.id.chipDopahar
                Shift.SHAAM -> R.id.chipShaam
                else -> R.id.chipAll
            }
            binding.chipGroupShift.check(chipId)

            // Sync state
            currentShift = savedShift
            userViewModel.setShiftFilter(savedShift)
        }
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun setupObservers() {
        // 1. List Data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Optional: binding.swipeRefreshLayout.isRefreshing = true
                        }
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            val list = state.data
                            adapter.submitList(list) // Adapter handles diff/updates

                            binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        }
                        is UiState.Error -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            DialogUtils.showAlert(this@UserListActivity, "Error", state.message)
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 2. Mutation (Save/Delete) Results
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> binding.swipeRefreshLayout.isRefreshing = true
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            // DialogUtils.showAlert(this@UserListActivity, "Success", "Operation successful") // Optional toast
                            userViewModel.resetMutationState()
                        }
                        is UiState.Error -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            DialogUtils.showAlert(this@UserListActivity, "Error", state.message)
                            userViewModel.resetMutationState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /* ============================================================
       🛠 ACTIONS
       ============================================================ */

    private fun confirmDelete(user: User) {
        DialogUtils.showConfirmation(
            this,
            title = "Confirm Deletion",
            message = "Are you sure you want to delete ${user.name}?",
            onConfirmed = { userViewModel.deleteUser(user.uid) }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.user_list_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search by name..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                userViewModel.setSearchQuery(query ?: "")
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                userViewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
        return true
    }
}