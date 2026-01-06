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

@AndroidEntryPoint
class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private val userViewModel: UserViewModel by viewModels()
    private lateinit var adapter: UserAdapter

    // Intent Data (Target Data)
    private lateinit var mode: String
    private var listRole: String = Roles.STUDENT // Kiska data dikhana hai
    private var classId: String? = null
    private var division: String? = null
    private var className: String? = null

    // Default State
    private var currentShift: String = Shift.ALL

    private val userFormLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        extractIntentData()

        // 1. Check WHO is logged in (Admin vs Teacher)
        checkLoggedInUserAndSetupUI()

        setupRecyclerView()
        setupListeners()
        setupObservers()

        // 2. Initialize Data
        binding.chipGroupShift.check(R.id.chipAll)
        userViewModel.setInitialFilters(listRole, classId, Shift.ALL)
    }

    private fun checkLoggedInUserAndSetupUI() {
        lifecycleScope.launch {
            // ✅ Fetch Current User from Local DB
            val currentUser = userViewModel.getCurrentUser()
            val loggedInRole = currentUser?.role?.trim() ?: ""

            // ✅ Logic: Agar Teacher hai, to Shift Filter Chupao
            if (loggedInRole.equals(Roles.TEACHER, ignoreCase = true)) {
                binding.shiftSelectionCard.visibility = View.GONE

                // Teacher ke paas FAB ka access hona chahiye ya nahi, wo aap decide karein
                // Usually Teacher students add nahi karte, Admin karta hai.
                // binding.fabAddUser.visibility = View.GONE
            } else {
                // Admin: Show Filters
                binding.shiftSelectionCard.visibility = View.VISIBLE
            }
        }
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
        listRole = intent.getStringExtra("role") ?: Roles.STUDENT // Yeh list ke liye hai
        classId = intent.getStringExtra("classId")
        className = intent.getStringExtra("className")
        division = intent.getStringExtra("division")

        supportActionBar?.apply {
            title = buildString {
                if (!className.isNullOrEmpty() && listRole == Roles.STUDENT) {
                    append(className)
                } else if (listRole == Roles.TEACHER) {
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
                putExtra("role", listRole)
                putExtra("mode", Modes.CREATE)
                putExtra("classId", classId)
                putExtra("division", division)
                putExtra("currentShift", currentShift)
            }
            userFormLauncher.launch(intent)
        }

        // Swipe Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
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

            currentShift = selectedShift
            userViewModel.setShiftFilter(selectedShift)
        }
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> binding.swipeRefreshLayout.isRefreshing = true
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
                            val list = state.data
                            adapter.submitList(list)

                            if (list.isEmpty()) {
                                binding.recyclerView.visibility = View.GONE
                                binding.emptyView.visibility = View.VISIBLE
                            } else {
                                binding.recyclerView.visibility = View.VISIBLE
                                binding.emptyView.visibility = View.GONE
                            }
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

        // Mutation Results
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> binding.swipeRefreshLayout.isRefreshing = true
                        is UiState.Success -> {
                            binding.swipeRefreshLayout.isRefreshing = false
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