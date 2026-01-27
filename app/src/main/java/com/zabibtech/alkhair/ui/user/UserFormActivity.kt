package com.zabibtech.alkhair.ui.user

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
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
import com.zabibtech.alkhair.utils.Shift
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding

    // ViewModels
    private val userVm: UserViewModel by viewModels()
    private val classVm: ClassManagerViewModel by viewModels()

    // Helpers
    private lateinit var dropdownHelper: DropdownHelper

    // State
    private var role = Roles.STUDENT
    private var mode = Modes.CREATE
    private var userToEdit: User? = null
    private var loadedDivisions: List<DivisionModel> = emptyList() // ✅ Store divisions

    // Selection State
    private var selectedClassId: String? = null
    private var intentClassId: String? = null
    private var intentDivision: String? = null
    private var intentShift: String? = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dropdownHelper = DropdownHelper(this)

        setupWindowInsets()
        setupToolbar()
        extractIntentData()
        setupUi()

        // Observers
        observeUserMutation()
        observeClassData()

        binding.btnSave.setOnClickListener { handleSave() }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
        role = intent.getStringExtra("role") ?: Roles.STUDENT
        mode = intent.getStringExtra("mode") ?: Modes.CREATE
        intentClassId = intent.getStringExtra("classId")
        intentDivision = intent.getStringExtra("division")
        intentShift = intent.getStringExtra("currentShift") ?: "All"
        userToEdit = intent.extras?.getParcelableCompat("user", User::class.java)
    }

    private fun setupUi() = with(binding) {
        // 1. Date Pickers
        setupDatePicker(etDob)
        setupDatePicker(etDoj)

        // 2. Password Visibility (Admin or New User only)
        layoutPassword.apply {
            visibility = when (mode) {
                Modes.CREATE -> View.VISIBLE
                Modes.UPDATE if role == Roles.ADMIN -> View.VISIBLE
                else -> View.GONE
            }
            isEnabled = true
        }

        // 3. Role Specific Fields
        layoutTeacherFields.visibility = if (role == Roles.TEACHER) View.VISIBLE else View.GONE
        etTotalFees.visibility = if (role == Roles.STUDENT) View.VISIBLE else View.GONE

        // 4. Toolbar Title & Button
        btnSave.text = if (mode == Modes.UPDATE) "Update" else "Create"
        toolbar.title = "${if (mode == Modes.UPDATE) "Update" else "Add"} ${role.replaceFirstChar { it.uppercase() }}"

        // 5. Shift Dropdown
        val shifts = listOf(Shift.SUBAH, Shift.DOPAHAR, Shift.SHAAM)
        etShift.setAdapter(ArrayAdapter(this@UserFormActivity, android.R.layout.simple_dropdown_item_1line, shifts))

        // 6. Prefill Data
        prefillData(shifts)
    }

    private fun setupDatePicker(view: android.widget.EditText) {
        var cal = Calendar.getInstance()
        view.setOnClickListener {
            DateUtils.showMaterialDatePicker(supportFragmentManager, cal) { selected ->
                cal = selected
                view.setText(DateUtils.formatDate(cal))
            }
        }
    }

    private fun prefillData(shifts: List<String>) = with(binding) {
        // If Editing User
        userToEdit?.let { user ->
            etName.setText(user.name)
            etParentName.setText(user.parentName)
            etEmail.setText(user.email)
            etPhone.setText(user.phone)
            etPassword.setText(user.password)
            etAddress.setText(user.address)
            etSubject.setText(user.subject)
            etSalary.setText(user.salary.toString())
            etDob.setText(user.dateOfBirth)
            etTotalFees.setText(user.totalFees.toString())

            // Set IDs for logic
            selectedClassId = user.classId

            // Set Dropdown Text (Visual)
            etDivision.setText(user.divisionName, false)
            etClass.setText(user.className, false)

            // Set Shift
            if (user.shift.isNotEmpty()) {
                etShift.setText(user.shift, false)
            }
            return@with
        }

        // If Creating New (Prefill from Context/Intent)
        if (mode == Modes.CREATE) {
            // If shift provided from previous screen
            if (!intentShift.isNullOrEmpty() && intentShift != "All") {
                val match = shifts.find { it.equals(intentShift, true) }
                if (match != null) etShift.setText(match, false)
            }

            // Division/Class visual text will be set in setupDropdowns once data loads
        }
    }

    /* ============================================================
       👀 OBSERVERS
       ============================================================ */

    private fun observeUserMutation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userVm.mutationState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> DialogUtils.showLoading(supportFragmentManager, "Saving...")
                        is UiState.Success -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(
                                this@UserFormActivity,
                                "Success",
                                "User saved successfully",
                                onPositiveClick = {
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            )
                            userVm.resetMutationState()
                        }
                        is UiState.Error -> {
                            DialogUtils.hideLoading(supportFragmentManager)
                            DialogUtils.showAlert(this@UserFormActivity, "Error", state.message)
                            userVm.resetMutationState()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeClassData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Combined UI State from ClassManagerViewModel
                classVm.uiState.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Optional: Show loading for dropdowns
                        }
                        is UiState.Success -> {
                            val data = state.data
                            loadedDivisions = data.divisions // ✅ Cache list
                            setupDropdowns(data.divisions, data.classes)
                        }
                        is UiState.Error -> {
                            DialogUtils.showAlert(this@UserFormActivity, "Error", state.message)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /* ============================================================
       🔧 DROPDOWN LOGIC
       ============================================================ */

    private fun setupDropdowns(divisions: List<DivisionModel>, classes: List<ClassModel>) {
        if (divisions.isEmpty() || classes.isEmpty()) return

        // Decide initial values for DropdownHelper
        val initialDivision = if (mode == Modes.UPDATE) userToEdit?.divisionName else intentDivision
        val initialClass = if (mode == Modes.UPDATE) userToEdit?.className else {
            // Find class name by ID if passed via intent
            classes.find { it.id == intentClassId }?.className
        }

        dropdownHelper.setupDivisionDropdown(
            binding.etDivision,
            binding.etClass,
            divisions,
            classes,
            preSelectedDivision = initialDivision,
            preSelectedClass = initialClass
        ) { classId ->
            this.selectedClassId = classId
        }
    }

    private fun handleSave() {
        // Resolve Division ID from cached list based on name in EditText
        val divisionName = binding.etDivision.text.toString()
        val selectedDivisionId = loadedDivisions.find { it.name == divisionName }?.id

        // Use Builder to construct object from Views
        val user = UserBuilder.build(
            binding = binding,
            role = role,
            mode = mode,
            selectedClassId = selectedClassId,
            selectedDivisionId = selectedDivisionId, // ✅ Pass ID
            userToEdit = userToEdit
        ) ?: return

        if (mode == Modes.CREATE) userVm.createUser(user)
        else userVm.updateUser(user)
    }
}