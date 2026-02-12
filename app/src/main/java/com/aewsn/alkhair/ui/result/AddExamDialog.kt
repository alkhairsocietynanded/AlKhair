package com.aewsn.alkhair.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.DialogAddExamBinding
import com.aewsn.alkhair.ui.user.UserViewModel
import com.aewsn.alkhair.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class AddExamDialog : BottomSheetDialogFragment() {

    private val viewModel: ResultViewModel by activityViewModels()
    private val userViewModel: UserViewModel by viewModels()

    private var _binding: DialogAddExamBinding? = null
    private val binding get() = _binding!!

    private var isEditMode = false
    private var existingExam: Exam? = null
    private var selectedClassId: String? = null

    private var classList: List<ClassModel> = emptyList()
    private var divisionList: List<DivisionModel> = emptyList()

    private var currentUser: User? = null

    private var selectedStartDate = Calendar.getInstance()
    private var selectedEndDate = Calendar.getInstance()

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        existingExam = arguments?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("exam_to_edit", Exam::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable("exam_to_edit")
            }
        }
        isEditMode = existingExam != null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddExamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchCurrentUser()
        setupDatePickers()
        observeMutationState()

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSubmitExam.setOnClickListener {
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
            // 🔒 TEACHER: Class/Division hidden — auto-filled from profile
            binding.tilClass.isVisible = false
            binding.tilDivision.isVisible = false
            selectedClassId = user.classId
        } else {
            // 🔓 ADMIN: Show dropdowns
            binding.tilClass.isVisible = true
            binding.tilDivision.isVisible = true
            setupClassDivisionDropdowns()
        }

        // Apply Edit Mode Prefills
        prefillDataForEdit()
    }

    private fun setupDatePickers() {
        if (!isEditMode) {
            binding.etStartDate.setText(dateFormat.format(selectedStartDate.time))
            selectedEndDate.add(Calendar.DAY_OF_MONTH, 7)
            binding.etEndDate.setText(dateFormat.format(selectedEndDate.time))
        }

        binding.etStartDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(childFragmentManager, selectedStartDate) { newDate ->
                selectedStartDate = newDate
                binding.etStartDate.setText(dateFormat.format(selectedStartDate.time))
            }
        }

        binding.etEndDate.setOnClickListener {
            DateUtils.showMaterialDatePicker(childFragmentManager, selectedEndDate) { newDate ->
                selectedEndDate = newDate
                binding.etEndDate.setText(dateFormat.format(selectedEndDate.time))
            }
        }
    }

    private fun prefillDataForEdit() {
        if (isEditMode) {
            existingExam?.let { exam ->
                selectedClassId = exam.classId

                binding.tvDialogTitle.text = "Edit Exam"
                binding.etTitle.setText(exam.title)
                binding.etSession.setText(exam.session)
                binding.switchPublished.isChecked = exam.isPublished

                // Set dates
                if (exam.startDate > 0) {
                    selectedStartDate.timeInMillis = exam.startDate
                    binding.etStartDate.setText(dateFormat.format(selectedStartDate.time))
                }
                if (exam.endDate > 0) {
                    selectedEndDate.timeInMillis = exam.endDate
                    binding.etEndDate.setText(dateFormat.format(selectedEndDate.time))
                }

                binding.btnSubmitExam.text = "Update Exam"
            }
            
            binding.btnDelete.isVisible = true
            binding.btnDelete.setOnClickListener {
                showDeleteConfirmation()
            }
        }
    }

    private fun showDeleteConfirmation() {
        com.aewsn.alkhair.utils.DialogUtils.showConfirmation(
            context = requireContext(),
            title = "Delete Exam",
            message = "Are you sure you want to delete this exam? This action cannot be undone.",
            positiveText = "Delete",
            onConfirmed = {
                existingExam?.let { viewModel.deleteExam(it.id) }
            }
        )
    }

    private fun submitDataToViewModel() {
        // If Admin, resolve class ID from dropdown
        if (binding.tilClass.isVisible && selectedClassId.isNullOrBlank()) {
            val name = binding.etClass.text.toString()
            selectedClassId = classList.find { it.className == name }?.id
        }

        viewModel.createOrUpdateExam(
            isEditMode = isEditMode,
            existingExam = existingExam,
            title = binding.etTitle.text.toString().trim(),
            startDate = selectedStartDate.timeInMillis,
            endDate = selectedEndDate.timeInMillis,
            session = binding.etSession.text.toString().trim().ifEmpty { null },
            classId = selectedClassId,
            isPublished = binding.switchPublished.isChecked
        )
    }

    private fun observeMutationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.examMutationState.collect { state ->
                    when (state) {
                        is UiState.Loading -> binding.btnSubmitExam.isEnabled = false
                        is UiState.Success -> {
                            binding.btnSubmitExam.isEnabled = true
                            Toast.makeText(
                                requireContext(),
                                if (isEditMode) "Exam Updated" else "Exam Added",
                                Toast.LENGTH_SHORT
                            ).show()
                            dismiss()
                            viewModel.resetExamMutationState()
                        }
                        is UiState.Error -> {
                            binding.btnSubmitExam.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetExamMutationState()
                        }
                        else -> binding.btnSubmitExam.isEnabled = true
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
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            divisionList
                        )
                        binding.etDivision.setAdapter(adapter)
                    }
                }
            }
        }

        // Division selection → filter classes
        binding.etDivision.setOnItemClickListener { parent, _, position, _ ->
            val selectedDiv = parent.getItemAtPosition(position) as DivisionModel
            updateClassDropdown(selectedDiv)
            binding.etClass.text = null
            selectedClassId = null
        }
    }

    private fun updateClassDropdown(selectedDivision: DivisionModel? = null) {
        if (classList.isEmpty()) return

        val filteredClasses = if (selectedDivision != null) {
            classList.filter { it.divisionId == selectedDivision.id }
        } else {
            val currentText = binding.etDivision.text.toString()
            if (currentText.isBlank()) classList
            else {
                val div = divisionList.find { it.name == currentText }
                if (div != null) classList.filter { it.divisionId == div.id } else classList
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            filteredClasses
        )
        binding.etClass.setAdapter(adapter)

        binding.etClass.setOnItemClickListener { parent, _, position, _ ->
            if (adapter.count > position) {
                val selectedObj = parent.getItemAtPosition(position) as ClassModel
                selectedClassId = selectedObj.id
            }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (binding.etTitle.text.isNullOrBlank()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        } else binding.tilTitle.error = null

        if (binding.etStartDate.text.isNullOrBlank()) {
            binding.tilStartDate.error = "Start date is required"
            isValid = false
        } else binding.tilStartDate.error = null

        if (binding.etEndDate.text.isNullOrBlank()) {
            binding.tilEndDate.error = "End date is required"
            isValid = false
        } else binding.tilEndDate.error = null

        // Admin must select class
        if (binding.tilClass.isVisible && binding.etClass.text.isNullOrBlank()) {
            binding.tilClass.error = "Class is required"
            isValid = false
        } else binding.tilClass.error = null

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(exam: Exam? = null): AddExamDialog {
            return AddExamDialog().apply {
                arguments = Bundle().apply {
                    putParcelable("exam_to_edit", exam)
                }
            }
        }
    }
}
