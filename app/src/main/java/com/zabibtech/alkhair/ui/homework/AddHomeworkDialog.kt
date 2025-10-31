package com.zabibtech.alkhair.ui.homework

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.storage.FirebaseStorage
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.databinding.DialogAddHomeworkBinding
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
class AddHomeworkDialog : DialogFragment() {

    private val viewModel: HomeworkViewModel by viewModels()
    private var attachmentUri: Uri? = null
    private var _binding: DialogAddHomeworkBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var userStore: UserStore

    @Inject
    lateinit var classDivisionStore: ClassDivisionStore

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddHomeworkBinding.inflate(layoutInflater)

        setupDropdowns()
        prefillDataForEdit()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

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

        return dialog
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

        // You could show a loading indicator here by adding a progress bar to your layout

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
            // The activity will observe the mutation state and handle the result
            dismiss()
        }
    }

    private fun setupDropdowns() {
        lifecycleScope.launch {
            val divisions = classDivisionStore.getOrFetchClassList().map { it.division }.distinct()
            val classes = classDivisionStore.getOrFetchClassList().map { it.className }.distinct()
            val shift = listOf("Subah", "Dopahar", "Shaam")

            val divisionAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    divisions
                )
            binding.etDivision.setAdapter(divisionAdapter)
            val classAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    classes
                )
            binding.etClass.setAdapter(classAdapter)

            val shiftAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    shift
                )
            binding.etShift.setAdapter(shiftAdapter)


        }
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