package com.zabibtech.alkhair.ui.user

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.ActivityUserFormBinding
import com.zabibtech.alkhair.ui.classmanager.ClassManagerViewModel
import com.zabibtech.alkhair.ui.user.helper.DropdownHelper
import com.zabibtech.alkhair.ui.user.helper.UserBuilder
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.Modes
import com.zabibtech.alkhair.utils.Roles
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private val userVm: UserViewModel by viewModels()
    private val classVm: ClassManagerViewModel by viewModels()
    private lateinit var dropdownHelper: DropdownHelper

    private var role = Roles.STUDENT
    private var mode = Modes.CREATE
    private var userToEdit: User? = null
    private var selectedClassId: String? = null

    private var allDivisions: List<DivisionModel> = emptyList()
    private var allClasses: List<ClassModel> = emptyList()

    private var dataLoadedCount = 0
    private val totalDataToLoad = 2 // divisions + classes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dropdownHelper = DropdownHelper(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        extractIntentData()
        setupUi()
        observeViewModels()

        // 🔹 Load divisions & classes
        showLoading("Loading data...")
        classVm.loadDivisions()
        classVm.loadClasses()

        binding.btnSave.setOnClickListener { handleSave() }
    }

    private fun extractIntentData() {
        role = intent.getStringExtra("role") ?: Roles.STUDENT
        mode = intent.getStringExtra("mode") ?: Modes.CREATE

        @Suppress("DEPRECATION")
        userToEdit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("user", User::class.java)
        } else {
            intent.getParcelableExtra("user")
        }
    }

    private fun setupUi() = with(binding) {
        // set date picker to dob
        var selectedDate = Calendar.getInstance()
        etDob.setOnClickListener {
            DateUtils.showDatePicker(this@UserFormActivity, selectedDate) { cal ->
                selectedDate = cal
                binding.etDob.setText(DateUtils.formatDate(selectedDate))
            }
        }
        // Password handling
        layoutPassword.apply {
            visibility = when {
                mode == Modes.CREATE -> View.VISIBLE
                mode == Modes.UPDATE && role == Roles.ADMIN -> View.VISIBLE
                else -> View.GONE
            }
            isEnabled = mode == Modes.CREATE || (mode == Modes.UPDATE && role == Roles.ADMIN)
        }

        // Role-specific UI
        layoutTeacherFields.visibility = if (role == Roles.TEACHER) View.VISIBLE else View.GONE
        etTotalFees.visibility = if (role == Roles.STUDENT) View.VISIBLE else View.GONE

        // Title & button
        btnSave.text = if (mode == Modes.UPDATE) "Update" else "Create"
        tvTitle.text = when (role) {
            Roles.STUDENT -> if (mode == Modes.UPDATE) "Update Student" else "Add Student"
            Roles.TEACHER -> if (mode == Modes.UPDATE) "Update Teacher" else "Add Teacher"
            else -> if (mode == Modes.UPDATE) "Update Admin" else "Add Admin"
        }

        // Prefill if update
        userToEdit?.let { user ->
            etName.setText(user.name)
            etParentName.setText(user.parentName)
            etEmail.setText(user.email)
            etPhone.setText(user.phone)
            etPassword.setText(user.password)
            etAddress.setText(user.address)
            etShift.setText(user.shift, false)
            etSubject.setText(user.subject)
            etSalary.setText(user.salary)
            etDivision.setText(user.divisionName, false)
            etClass.setText(user.className, false)
            etDob.setText(user.dateOfBirth)
            etTotalFees.setText(user.totalFees)
            selectedClassId = user.classId
        }

        // Shift dropdown
        val shifts = listOf("Subah", "Dopahar", "Shaam")
        etShift.setAdapter(
            android.widget.ArrayAdapter(
                this@UserFormActivity,
                android.R.layout.simple_dropdown_item_1line,
                shifts
            )
        )
    }

    private fun observeViewModels() {
        observeUserVm()
        observeClassVm()
    }

    private fun observeUserVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userVm.userState.collect { state ->
                    when (state) {
                        is UiState.Loading -> showLoading("Please wait...")
                        is UiState.Success -> {
                            hideLoading()
                            DialogUtils.showAlert(
                                this@UserFormActivity,
                                "Success",
                                "User saved successfully"
                            )
                            setResult(RESULT_OK)
                            finish()
                        }

                        is UiState.Error -> {
                            hideLoading()
                            DialogUtils.showAlert(this@UserFormActivity, "Error", state.message)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun observeClassVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                classVm.divisions.collectLatest { state ->
                    if (state is UiState.Success) {
                        allDivisions = state.data
                        markDataLoaded()
                        setupDropdowns()
                    } else if (state is UiState.Error) {
                        hideLoading()
                        DialogUtils.showAlert(this@UserFormActivity, "Error", state.message)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                classVm.classes.collectLatest { state ->
                    if (state is UiState.Success) {
                        allClasses = state.data
                        markDataLoaded()
                        setupDropdowns()
                    } else if (state is UiState.Error) {
                        hideLoading()
                        DialogUtils.showAlert(this@UserFormActivity, "Error", state.message)
                    }
                }
            }
        }
    }

    private fun setupDropdowns() {
        if (allDivisions.isEmpty() || allClasses.isEmpty()) return

        // Use DropdownHelper with pre-selected values
        dropdownHelper.setupDivisionDropdown(
            binding.etDivision,
            binding.etClass,
            allDivisions,
            allClasses,
            preSelectedDivision = userToEdit?.divisionName,
            preSelectedClass = userToEdit?.className
        ) { selectedClassId ->
            this.selectedClassId = selectedClassId
        }
    }

    private fun markDataLoaded() {
        if (++dataLoadedCount >= totalDataToLoad) hideLoading()
    }

    private fun handleSave() {
        val user = UserBuilder.build(
            binding = binding,
            role = role,
            mode = mode,
            selectedClassId = selectedClassId,
            userToEdit = userToEdit
        ) ?: return

        if (mode == Modes.CREATE) userVm.createUser(user)
        else userVm.updateUser(user)
    }

    private fun showLoading(message: String) =
        DialogUtils.showLoading(supportFragmentManager, message)

    private fun hideLoading() = DialogUtils.hideLoading(supportFragmentManager)
}
