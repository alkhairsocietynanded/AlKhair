package com.aewsn.alkhair.ui.syllabus

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.databinding.DialogAddSyllabusBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.aewsn.alkhair.utils.getParcelableCompat

class AddSyllabusDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddSyllabusBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SyllabusViewModel by activityViewModels()
    
    private var selectedAttachmentUri: Uri? = null
    private var selectedClassId: String? = null
    private val classList = mutableListOf<ClassModel>()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedAttachmentUri = uri
            binding.tvSelectedFile.text = "Selected: ${getFileName(uri)}"
            binding.tvSelectedFile.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddSyllabusBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var editSyllabus: com.aewsn.alkhair.data.models.Syllabus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            editSyllabus = it.getParcelableCompat("syllabus", com.aewsn.alkhair.data.models.Syllabus::class.java)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup Views
        setupFilePicker()
        setupButtons()
        setupClassSelection()
        
        editSyllabus?.let { syllabus ->
            binding.tvTitle.text = "Edit Syllabus" // Assuming there is a title TextView, or update toolbar/header if exists
            binding.etSubject.setText(syllabus.subject)
            binding.etTopic.setText(syllabus.topic)
            binding.etDescription.setText(syllabus.description)
            binding.btnSave.text = "Update"
            
            if (syllabus.attachmentUrl != null) {
                binding.tvSelectedFile.text = "Current: Attachment exists"
                binding.tvSelectedFile.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
            // Pre-select class logic handled in observeClasses or setupClassSelection
        }
    }

    private fun setupFilePicker() {
        binding.btnPickFile.setOnClickListener {
            filePickerLauncher.launch("*/*") 
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            validateAndSave()
        }
    }

    private fun setupClassSelection() {
        lifecycleScope.launch {
            viewModel.currentUser.collectLatest { user ->
                if (user == null) return@collectLatest
                
                if (user.role == "admin") {
                    binding.tilClass.visibility = View.VISIBLE
                    observeClasses()
                } else if (user.role == "teacher") {
                    binding.tilClass.visibility = View.GONE
                    selectedClassId = user.classId
                    if (selectedClassId == null) {
                        Toast.makeText(requireContext(), "Error: Teacher has no class assigned", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                } else {
                    // Student - should not see this dialog ideally
                    dismiss()
                }
            }
        }
    }

    private fun observeClasses() {
        lifecycleScope.launch {
            viewModel.allClasses.collectLatest { classes ->
                classList.clear()
                classList.addAll(classes)
                
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    classes.map { it.className }
                )
                binding.actvClass.setAdapter(adapter)
                
                binding.actvClass.setOnItemClickListener { _, _, position, _ ->
                    selectedClassId = classes[position].id
                }
                
                // Pre-select class for edit mode if Admin
                if (editSyllabus != null) {
                    val index = classes.indexOfFirst { it.id == editSyllabus!!.classId }
                    if (index != -1) {
                        binding.actvClass.setText(classes[index].className, false)
                        selectedClassId = classes[index].id
                    }
                }
            }
        }
    }

    private fun validateAndSave() {
        val subject = binding.etSubject.text.toString().trim()
        val topic = binding.etTopic.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (selectedClassId == null) {
            binding.tilClass.error = "Select Class"
            return
        }
        if (subject.isEmpty()) {
            binding.etSubject.error = "Required"
            return
        }
        if (topic.isEmpty()) {
            binding.etTopic.error = "Required"
            return
        }
        
        if (selectedAttachmentUri == null && editSyllabus == null) {
             Toast.makeText(requireContext(), "Please select a file to upload", Toast.LENGTH_SHORT).show()
             return
        }

        if (editSyllabus != null) {
            viewModel.updateSyllabus(
                id = editSyllabus!!.id,
                subject = subject,
                topic = topic,
                description = description,
                classId = selectedClassId!!,
                currentAttachmentUrl = editSyllabus!!.attachmentUrl,
                newAttachmentUri = selectedAttachmentUri
            )
            Toast.makeText(requireContext(), "Updating...", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.createSyllabus(subject, topic, description, selectedClassId!!, selectedAttachmentUri)
            Toast.makeText(requireContext(), "Uploading...", Toast.LENGTH_SHORT).show()
        }
        dismiss()
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "file"
    }

    companion object {
        fun newInstance(syllabus: com.aewsn.alkhair.data.models.Syllabus?): AddSyllabusDialog {
            val fragment = AddSyllabusDialog()
            val args = Bundle()
            if (syllabus != null) {
                args.putParcelable("syllabus", syllabus)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
