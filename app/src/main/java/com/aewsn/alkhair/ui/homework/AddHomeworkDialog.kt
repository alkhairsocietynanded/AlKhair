package com.aewsn.alkhair.ui.homework

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.data.models.Homework
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.DialogAddHomeworkBinding
import com.aewsn.alkhair.ui.user.UserViewModel
import com.aewsn.alkhair.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class AddHomeworkDialog : BottomSheetDialogFragment() {

    private val viewModel: HomeworkViewModel by activityViewModels()

    // We need UserViewModel to check if the current user is a Teacher
    private val userViewModel: UserViewModel by viewModels()

    private var attachmentUri: Uri? = null
    private var _binding: DialogAddHomeworkBinding? = null
    private val binding get() = _binding!!

    private var isEditMode = false
    private var existingHomework: Homework? = null
    private var selectedClassId: String? = null

    private var classList: List<ClassModel> = emptyList()
    private var divisionList: List<DivisionModel> = emptyList()

    // Current User (Teacher/Admin)
    private var currentUser: User? = null

    private var selectedDate = Calendar.getInstance()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachmentUri = uri
            binding.btnUploadAttachment.text = "Attachment Selected"
            Toast.makeText(requireContext(), "File Selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        existingHomework = arguments?.getParcelableCompat("homework_to_edit", Homework::class.java)
        isEditMode = existingHomework != null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Fetch Current User first to decide UI logic
        fetchCurrentUser()

        setupDatePicker()
        observeMutationState()

        binding.btnUploadAttachment.setOnClickListener { filePickerLauncher.launch("*/*") }
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSubmitHomework.setOnClickListener {
            if (validateInput()) {
                submitDataToViewModel()
            }
        }
    }

    private fun fetchCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userViewModel.getCurrentUser()
            currentUser = user

            if (user != null) {
                setupUIBasedOnRole(user)
            }
        }
    }

    private fun setupUIBasedOnRole(user: User) {
        val role = user.role.trim()

        if (role.equals(Roles.TEACHER, ignoreCase = true)) {
            // 🔒 TEACHER: Hide Class/Division/Shift fields
            // Because they are fixed to the teacher's profile
            binding.tilClass.isVisible = false
            binding.tilDivision.isVisible = false
            binding.tilShift.isVisible = false

            // Auto-fill values for validation logic (invisible but filled)
            binding.etClass.setText(user.className)
            binding.etDivision.setText(user.divisionName)
            binding.etShift.setText(user.shift)

            // Set ID directly
            selectedClassId = user.classId

        } else {
            // 🔓 ADMIN: Show everything
            binding.tilClass.isVisible = true
            binding.tilDivision.isVisible = true
            binding.tilShift.isVisible = true

            // Load dropdowns only if Admin
            setupClassDivisionDropdowns()
        }

        // Apply Edit Mode Prefills (Overrides defaults if editing)
        prefillDataForEdit()
    }

    private fun setupDatePicker() {
        if (!isEditMode) {
            binding.etDate.setText(DateUtils.formatDate(selectedDate))
        }

        binding.etDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(childFragmentManager, selectedDate) { newDate ->
                selectedDate = newDate
                binding.etDate.setText(DateUtils.formatDate(selectedDate))
            }
        }
    }

    private fun prefillDataForEdit() {
        if (isEditMode) {
            selectedClassId = existingHomework?.classId

            binding.etSubject.setText(existingHomework?.subject)
            binding.etTitle.setText(existingHomework?.title)
            binding.etDescription.setText(existingHomework?.description)
            binding.etDate.setText(existingHomework?.date)

            if (!existingHomework?.attachmentUrl.isNullOrBlank()) {
                binding.btnUploadAttachment.text = "Change Attachment"
            }

            // Only prefill these if visible (Admin)
            if (binding.tilClass.isVisible) {
                binding.etClass.setText(existingHomework?.className, false)
                binding.etDivision.setText(existingHomework?.divisionName, false)
                binding.etShift.setText(existingHomework?.shift, false)
            }

            binding.btnSubmitHomework.text = "Update Homework"
        }
    }

    private fun submitDataToViewModel() {
        // If Admin, ensure class ID is selected from list
        if (binding.tilClass.isVisible && selectedClassId.isNullOrBlank()) {
            val name = binding.etClass.text.toString()
            selectedClassId = classList.find { it.className == name }?.id
        }

        viewModel.createOrUpdateHomework(
            isEditMode = isEditMode,
            existingHomework = existingHomework,
            classId = selectedClassId ?: "", // Uses Teacher's ID or Admin's selection
            className = binding.etClass.text.toString().trim(),
            divisionName = binding.etDivision.text.toString().trim(),
            shift = binding.etShift.text.toString().trim(),
            subject = binding.etSubject.text.toString().trim(),
            title = binding.etTitle.text.toString().trim(),
            description = binding.etDescription.text.toString().trim(),
            date = binding.etDate.text.toString().trim(),
            teacherId = if(isEditMode) existingHomework?.teacherId ?: "" else currentUser?.uid ?: "", // Preserve creator ID
            newAttachmentUri = attachmentUri
        )
    }

    private fun observeMutationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeworkMutationState.collect { state ->
                    when (state) {
                        is UiState.Loading -> binding.btnSubmitHomework.isEnabled = false
                        is UiState.Success -> {
                            binding.btnSubmitHomework.isEnabled = true
                            Toast.makeText(requireContext(), if (isEditMode) "Updated" else "Added", Toast.LENGTH_SHORT).show()
                            dismiss() // Close first
                            viewModel.resetHomeworkMutationState()
                        }
                        is UiState.Error -> {
                            binding.btnSubmitHomework.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetHomeworkMutationState()
                        }
                        else -> binding.btnSubmitHomework.isEnabled = true
                    }
                }
            }
        }
    }

    private fun setupClassDivisionDropdowns() {
        // Only called for Admin
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.classesState.collect { state ->
                    if (state is UiState.Success) {
                        classList = state.data
                        updateClassDropdown()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.divisionsState.collect { state ->
                    if (state is UiState.Success) {
                        divisionList = state.data
                        // ✅ Pass Objects directly
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, divisionList)
                        binding.etDivision.setAdapter(adapter)
                    }
                }
            }
        }

        // Listener for Division Selection
        binding.etDivision.setOnItemClickListener { parent, _, position, _ ->
             // ✅ Cast directly to Object
             val selectedDiv = parent.getItemAtPosition(position) as DivisionModel
             updateClassDropdown(selectedDiv)
             
             binding.etClass.text = null // Reset class selection
             selectedClassId = null
        }

        val shifts = listOf(Shift.SUBAH, Shift.DOPAHAR, Shift.SHAAM)
        val shiftAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, shifts)
        binding.etShift.setAdapter(shiftAdapter)
    }

    private fun updateClassDropdown(selectedDivision: DivisionModel? = null) {
        if (classList.isEmpty()) return

        // Optimize: Do filtering in background if list is very large, but for now just ensure efficiency
        val filteredClasses = if (selectedDivision != null) {
             classList.filter { it.divisionId == selectedDivision.id }
        } else {
             // If text is empty, show all. If text is present but no object passed, try to find.
             val currentText = binding.etDivision.text.toString()
             if (currentText.isBlank()) classList 
             else {
                 val div = divisionList.find { it.name == currentText }
                 if (div != null) classList.filter { it.divisionId == div.id } else classList
             }
        }

        // ✅ Pass Objects directly
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, filteredClasses)
        binding.etClass.setAdapter(adapter)

        binding.etClass.setOnItemClickListener { parent, _, position, _ ->
             // ✅ Cast directly to Object
             if (adapter.count > position) { // Safety check
                 val selectedObj = parent.getItemAtPosition(position) as ClassModel
                 selectedClassId = selectedObj.id
             }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (binding.etSubject.text.isNullOrBlank()) {
            binding.tilSubject.error = "Subject is required"
            isValid = false
        } else binding.tilSubject.error = null

        if (binding.etTitle.text.isNullOrBlank()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        } else binding.tilTitle.error = null

        if (binding.etClass.text.isNullOrBlank()) {
            binding.tilClass.error = "Class is required"
            isValid = false
        } else binding.tilClass.error = null

        if (binding.etDivision.text.isNullOrBlank()) {
            binding.tilDivision.error = "Division is required"
            isValid = false
        } else binding.tilDivision.error = null

        if (binding.etShift.text.isNullOrBlank()) {
            binding.tilShift.error = "Shift is required"
            isValid = false
        } else binding.tilShift.error = null

        if (binding.etDate.text.isNullOrBlank()) {
            binding.tilDate.error = "Date is required"
            isValid = false
        } else binding.tilDate.error = null

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(homework: Homework? = null): AddHomeworkDialog {
            return AddHomeworkDialog().apply {
                arguments = Bundle().apply {
                    putParcelable("homework_to_edit", homework)
                }
            }
        }
    }
}