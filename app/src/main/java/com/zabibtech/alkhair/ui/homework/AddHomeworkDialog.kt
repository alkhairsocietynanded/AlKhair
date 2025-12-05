package com.zabibtech.alkhair.ui.homework

import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.storage.FirebaseStorage
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.databinding.DialogAddHomeworkBinding
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AddHomeworkDialog : BottomSheetDialogFragment() {

    private val viewModel: HomeworkViewModel by activityViewModels()
    private var attachmentUri: Uri? = null
    private var _binding: DialogAddHomeworkBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var userStore: UserStore

    // Remove ClassDivisionStore dependency
    // @Inject
    // lateinit var classDivisionStore: ClassDivisionStore

    // Determine if we are editing an existing homework item
    private var isEditMode = false
    private var existingHomework: Homework? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                attachmentUri = uri
                binding.btnUploadAttachment.text =
                    uri.path?.substringAfterLast('/') ?: "File Selected"
                Toast.makeText(requireContext(), "File Selected", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve the homework object if it was passed
        existingHomework = arguments?.getParcelableCompat("homework_to_edit", Homework::class.java)
        isEditMode = existingHomework != null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns() // This will now observe ViewModel states
        prefillDataForEdit()

        binding.btnUploadAttachment.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSubmitHomework.setOnClickListener {
            if (validateInput()) {
                createOrUpdateHomework()
            }
        }
    }

    private fun prefillDataForEdit() {
        if (isEditMode) {
            binding.etSubject.setText(existingHomework?.subject)
            binding.etTitle.setText(existingHomework?.title)
            binding.etDescription.setText(existingHomework?.description)
            binding.etClass.setText(
                existingHomework?.className,
                false
            ) // `false` to prevent filtering
            binding.etDivision.setText(existingHomework?.division, false)

            // Update button and title for edit mode
            binding.btnSubmitHomework.text = "Update Homework"
            // You can also add a title TextView to your dialog and change it here
        }
    }

    private fun createOrUpdateHomework() {
        val subject = binding.etSubject.text.toString().trim()
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val className = binding.etClass.text.toString().trim()
        val division = binding.etDivision.text.toString().trim()
        val shift = binding.etShift.text.toString().trim()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        CoroutineScope(Dispatchers.Main).launch {
            val fileUrl = if (attachmentUri != null) {
                uploadFile(attachmentUri!!)
            } else {
                existingHomework?.attachmentUrl // Retain the old file URL if not updated
            }

            val userId = userStore.getUser()?.uid
            Log.d("AddHomeworkDialog", "Retrieved teacherId from UserStore: $userId")
            val teacherId = userStore.getUser()?.uid ?: "default_teacher_id"

            val homework = Homework(
                id = existingHomework?.id ?: "", // Use existing ID if editing
                className = className,
                division = division,
                shift = shift,
                subject = subject,
                title = title,
                description = description,
                date = date,
                teacherId = existingHomework?.teacherId ?: teacherId, // Use existing or get current
                attachmentUrl = fileUrl
            )

            if (isEditMode) {
                viewModel.updateHomework(homework)
            } else {
                viewModel.addHomework(homework)
            }
            dismiss()
        }
    }

    private fun setupDropdowns() {
        // Observe classes and divisions from ViewModel
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Classes
                viewModel.classesState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val classes = state.data.map { it.className }.distinct()
                            setupClassAdapter(classes)
                        }
                        is UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> Unit // Idle or Loading states
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Divisions
                viewModel.divisionsState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val divisions = state.data.map { it.name }.distinct()
                            setupDivisionAdapter(divisions)
                        }
                        is UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> Unit // Idle or Loading states
                    }
                }
            }
        }
        
        // Shifts are static, so no need to observe
        val shift = listOf("Subah", "Dopahar", "Shaam")
        setupShiftAdapter(shift)
    }

    private fun setupClassAdapter(classes: List<String>) {
        val classAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            classes
        )
        binding.etClass.setAdapter(classAdapter)
    }

    private fun setupDivisionAdapter(divisions: List<String>) {
        val divisionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            divisions
        )
        binding.etDivision.setAdapter(divisionAdapter)
    }

    private fun setupShiftAdapter(shifts: List<String>) {
        val shiftAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            shifts
        )
        binding.etShift.setAdapter(shiftAdapter)
    }

    private fun validateInput(): Boolean {
        binding.tilSubject.error = null
        binding.tilTitle.error = null
        binding.tilClass.error = null
        binding.tilDivision.error = null
        binding.tilShift.error = null

        var isValid = true
        if (binding.etSubject.text.isNullOrBlank()) {
            binding.tilSubject.error = "Subject is required"
            isValid = false
        }
        if (binding.etTitle.text.isNullOrBlank()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        }
        if (binding.etClass.text.isNullOrBlank()) {
            binding.tilClass.error = "Class is required"
            isValid = false
        }
        if (binding.etDivision.text.isNullOrBlank()) {
            binding.tilDivision.error = "Division is required"
            isValid = false
        }
        if (binding.etShift.text.isNullOrBlank()) {
            binding.tilShift.error = "Shift is required"
            isValid = false
        }
        return isValid
    }

    private suspend fun uploadFile(uri: Uri): String? {
        return try {
            val storageRef = FirebaseStorage.getInstance()
                .getReference("homework_attachments/${UUID.randomUUID()}")
            storageRef.putFile(uri).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "File upload failed: ${e.message}", Toast.LENGTH_LONG)
                .show()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Companion object remains the same for creating new instances
    companion object {
        fun newInstance(homework: Homework? = null): AddHomeworkDialog {
            val dialog = AddHomeworkDialog()
            val args = Bundle()
            homework?.let {
                args.putParcelable("homework_to_edit", it)
            }
            dialog.arguments = args
            return dialog
        }
    }
}
