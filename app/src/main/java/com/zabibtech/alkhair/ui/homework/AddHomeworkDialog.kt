package com.zabibtech.alkhair.ui.homework

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.databinding.DialogAddHomeworkBinding
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Refactored to align with modern architecture
@AndroidEntryPoint
class AddHomeworkDialog : BottomSheetDialogFragment() {

    private val viewModel: HomeworkViewModel by activityViewModels()
    private var attachmentUri: Uri? = null
    private var _binding: DialogAddHomeworkBinding? = null
    private val binding get() = _binding!!

    private var isEditMode = false
    private var existingHomework: Homework? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachmentUri = uri
            binding.btnUploadAttachment.text = uri.path?.substringAfterLast('/') ?: "File Selected"
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

        setupDropdowns()
        observeMutationState()
        prefillDataForEdit()

        binding.btnUploadAttachment.setOnClickListener { filePickerLauncher.launch("*/*") }
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSubmitHomework.setOnClickListener {
            if (validateInput()) {
                // UI only gathers data and tells ViewModel to act
                submitDataToViewModel()
            }
        }
    }

    private fun prefillDataForEdit() {
        if (isEditMode) {
            binding.etSubject.setText(existingHomework?.subject)
            binding.etTitle.setText(existingHomework?.title)
            binding.etDescription.setText(existingHomework?.description)
            binding.etClass.setText(existingHomework?.className, false)
            binding.etDivision.setText(existingHomework?.division, false)
            binding.etShift.setText(existingHomework?.shift, false)
            binding.btnSubmitHomework.text = "Update Homework"
        }
    }

    private fun submitDataToViewModel() {
        // All business logic is now moved to the ViewModel.
        viewModel.createOrUpdateHomework(
            isEditMode = isEditMode,
            existingHomework = existingHomework,
            className = binding.etClass.text.toString().trim(),
            division = binding.etDivision.text.toString().trim(),
            shift = binding.etShift.text.toString().trim(),
            subject = binding.etSubject.text.toString().trim(),
            title = binding.etTitle.text.toString().trim(),
            description = binding.etDescription.text.toString().trim(),
            newAttachmentUri = attachmentUri
        )
    }

    private fun observeMutationState() {
        // Using lifecycle-aware coroutine scope to observe the result
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mutationState.collect { state ->
                    when (state) {
                        is UiState.Loading -> { /* Optionally show a loading indicator */ }
                        is UiState.Success -> {
                            Toast.makeText(requireContext(), if(isEditMode) "Homework Updated" else "Homework Added", Toast.LENGTH_SHORT).show()
                            viewModel.resetMutationState() // Reset state after consumption
                            dismiss()
                        }
                        is UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetMutationState() // Reset state after consumption
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    private fun setupDropdowns() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.classesState.collect { state ->
                    if (state is UiState.Success) {
                        val classes = state.data.map { it.className }.distinct()
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, classes)
                        binding.etClass.setAdapter(adapter)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.divisionsState.collect { state ->
                    if (state is UiState.Success) {
                        val divisions = state.data.map { it.name }.distinct()
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, divisions)
                        binding.etDivision.setAdapter(adapter)
                    }
                }
            }
        }

        val shifts = listOf("Subah", "Dopahar", "Shaam")
        val shiftAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, shifts)
        binding.etShift.setAdapter(shiftAdapter)
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (binding.etSubject.text.isNullOrBlank()) {
            binding.tilSubject.error = "Subject is required"
            isValid = false
        } else {
            binding.tilSubject.error = null
        }

        if (binding.etTitle.text.isNullOrBlank()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        } else {
            binding.tilTitle.error = null
        }

        if (binding.etClass.text.isNullOrBlank()) {
            binding.tilClass.error = "Class is required"
            isValid = false
        } else {
            binding.tilClass.error = null
        }

        if (binding.etDivision.text.isNullOrBlank()) {
            binding.tilDivision.error = "Division is required"
            isValid = false
        } else {
            binding.tilDivision.error = null
        }
        
        if (binding.etShift.text.isNullOrBlank()) {
            binding.tilShift.error = "Shift is required"
            isValid = false
        } else {
            binding.tilShift.error = null
        }

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
