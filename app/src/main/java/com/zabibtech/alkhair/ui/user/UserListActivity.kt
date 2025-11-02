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
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.datastore.ShiftDataStore
import com.zabibtech.alkhair.databinding.ActivityUserListBinding
import com.zabibtech.alkhair.ui.user.helper.UserListUiController
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
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
    private lateinit var uiController: UserListUiController

    private lateinit var mode: String
    private var role: String = Roles.STUDENT
    private var classId: String? = null
    private var className: String? = null
    private var division: String? = null
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

        setupToolbar()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        extractIntentData()
        setupRecyclerView()

        uiController = UserListUiController(
            this,
            binding,
            adapter,
            userViewModel
        ) { intent -> userFormLauncher.launch(intent) }

        setupObservers()
        setupChipFilterListeners()

        lifecycleScope.launch {
            val savedShift = shiftDataStore.getShift()
            val chipId = when (savedShift) {
                "Subah" -> R.id.chipSubah
                "Dopahar" -> R.id.chipDopahar
                "Shaam" -> R.id.chipShaam
                else -> R.id.chipAll
            }
            binding.chipGroupShift.check(chipId)

            uiController.setupListeners(role, classId, division, currentShift)

            // Initialize SwipeRefreshLayout
            binding.swipeRefreshLayout.setColorSchemeResources(
                R.color.md_theme_primary,
                R.color.md_theme_onSurfaceVariant
            )

            // First-time load
            userViewModel.loadUsers(role)
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
                    append("User List")
                }
            }
            subtitle = division ?: ""
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.user_list_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search by name..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter.filter(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
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
                    uiController.confirmDelete(user)
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
        binding.chipGroupShift.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            currentShift = when (checkedIds.first()) {
                R.id.chipSubah -> "Subah"
                R.id.chipDopahar -> "Dopahar"
                R.id.chipShaam -> "Shaam"
                else -> "All"
            }

            lifecycleScope.launch {
                shiftDataStore.saveShift(currentShift ?: "All")
            }

            uiController.setupListeners(role, classId, division, currentShift)
            userViewModel.loadUsers(role)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userViewModel.userListState.collectLatest { state ->
                    binding.swipeRefreshLayout.isRefreshing = state is UiState.Loading
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