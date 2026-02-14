package com.aewsn.alkhair.ui.studymaterial

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
import com.aewsn.alkhair.data.local.dao.SubjectDao
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.StudyMaterial
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.databinding.DialogAddStudyMaterialBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.aewsn.alkhair.utils.getParcelableCompat
import javax.inject.Inject

@AndroidEntryPoint
class AddStudyMaterialDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddStudyMaterialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudyMaterialViewModel by activityViewModels()

    @Inject lateinit var subjectDao: SubjectDao

    private var selectedAttachmentUri: Uri? = null
    private var selectedClassId: String? = null
    private var selectedMaterialType: String = "PDF"
    private val classList = mutableListOf<ClassModel>()
    private var subjectList: List<Subject> = emptyList()

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
        _binding = DialogAddStudyMaterialBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var editMaterial: StudyMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            editMaterial = it.getParcelableCompat("study_material", StudyMaterial::class.java)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFilePicker()
        setupButtons()
        setupClassSelection()
        setupSubjectDropdown()
        setupMaterialTypeChips()

        editMaterial?.let { material ->
            binding.tvDialogTitle.text = "Edit Study Material"
            binding.etTitle.setText(material.title)
            binding.etDescription.setText(material.description)
            binding.btnSave.text = "Update"
            selectedMaterialType = material.materialType

            // Pre-select material type chip
            when (material.materialType) {
                "PDF" -> binding.chipPdf.isChecked = true
                "Notes" -> binding.chipNotes.isChecked = true
                "Video" -> binding.chipVideo.isChecked = true
                "Link" -> binding.chipLink.isChecked = true
            }

            // Update UI for the selected type
            updateUiForMaterialType(material.materialType)

            // Pre-fill link URL if Link type
            if (material.materialType == "Link" && material.attachmentUrl != null) {
                binding.etLinkUrl.setText(material.attachmentUrl)
            }

            if (material.attachmentUrl != null && material.materialType != "Link") {
                binding.tvSelectedFile.text = "Current: Attachment exists"
                binding.tvSelectedFile.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
        }
    }

    private fun setupSubjectDropdown() {
        viewLifecycleOwner.lifecycleScope.launch {
            subjectList = subjectDao.getAllSubjects().first()
            val subjectNames = subjectList.map { it.name }
            val subjectAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                subjectNames
            )
            binding.actvSubject.setAdapter(subjectAdapter)

            binding.actvSubject.setOnItemClickListener { _, _, position, _ ->
                // Subject selected — just using the name
            }

            // Pre-select subject for edit mode
            editMaterial?.let { material ->
                val index = subjectList.indexOfFirst { it.name == material.subject }
                if (index != -1) {
                    binding.actvSubject.setText(subjectList[index].name, false)
                }
            }
        }
    }

    private fun setupMaterialTypeChips() {
        binding.chipGroupType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedMaterialType = when (checkedIds[0]) {
                    com.aewsn.alkhair.R.id.chipPdf -> "PDF"
                    com.aewsn.alkhair.R.id.chipNotes -> "Notes"
                    com.aewsn.alkhair.R.id.chipVideo -> "Video"
                    com.aewsn.alkhair.R.id.chipLink -> "Link"
                    com.aewsn.alkhair.R.id.chipImage -> "Image"
                    else -> "PDF"
                }
                updateUiForMaterialType(selectedMaterialType)
            }
        }
    }

    private fun updateUiForMaterialType(type: String) {
        if (type == "Link") {
            // Link type: show URL input, hide file picker
            binding.layoutFilePicker.visibility = View.GONE
            binding.tilLinkUrl.visibility = View.VISIBLE
        } else {
            // File types: show file picker, hide URL input
            binding.layoutFilePicker.visibility = View.VISIBLE
            binding.tilLinkUrl.visibility = View.GONE

            // Update button text based on type
            binding.btnPickFile.text = when (type) {
                "PDF" -> "Pick PDF File"
                "Notes" -> "Pick Notes File"
                "Video" -> "Pick Video File"
                "Image" -> "Pick Image"
                else -> "Pick File"
            }
        }
    }

    private fun setupFilePicker() {
        binding.btnPickFile.setOnClickListener {
            val mimeType = when (selectedMaterialType) {
                "PDF" -> "application/pdf"
                "Video" -> "video/*"
                "Image" -> "image/*"
                "Notes" -> "*/*"
                else -> "*/*"
            }
            filePickerLauncher.launch(mimeType)
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
                    // Student - should not see this dialog
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
                if (editMaterial != null) {
                    val index = classes.indexOfFirst { it.id == editMaterial!!.classId }
                    if (index != -1) {
                        binding.actvClass.setText(classes[index].className, false)
                        selectedClassId = classes[index].id
                    }
                }
            }
        }
    }

    private fun validateAndSave() {
        val subject = binding.actvSubject.text.toString().trim()
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (selectedClassId == null) {
            binding.tilClass.error = "Select Class"
            return
        }
        if (subject.isEmpty()) {
            binding.tilSubject.error = "Select Subject"
            return
        } else {
            binding.tilSubject.error = null
        }
        if (title.isEmpty()) {
            binding.etTitle.error = "Required"
            return
        }

        if (selectedMaterialType == "Link") {
            // Link type: URL is required, file is not
            val linkUrl = binding.etLinkUrl.text.toString().trim()
            if (linkUrl.isEmpty()) {
                binding.tilLinkUrl.error = "Enter URL"
                return
            } else {
                binding.tilLinkUrl.error = null
            }

            if (editMaterial != null) {
                viewModel.updateMaterial(
                    id = editMaterial!!.id,
                    subject = subject,
                    title = title,
                    description = description,
                    materialType = selectedMaterialType,
                    classId = selectedClassId!!,
                    currentAttachmentUrl = linkUrl,
                    newAttachmentUri = null
                )
                Toast.makeText(requireContext(), "Updating...", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.createMaterial(subject, title, description, selectedMaterialType, selectedClassId!!, null, linkUrl)
                Toast.makeText(requireContext(), "Saving...", Toast.LENGTH_SHORT).show()
            }
        } else {
            // File types: file is required (for new, not for edit)
            if (selectedAttachmentUri == null && editMaterial == null) {
                Toast.makeText(requireContext(), "Please select a file to upload", Toast.LENGTH_SHORT).show()
                return
            }

            if (editMaterial != null) {
                viewModel.updateMaterial(
                    id = editMaterial!!.id,
                    subject = subject,
                    title = title,
                    description = description,
                    materialType = selectedMaterialType,
                    classId = selectedClassId!!,
                    currentAttachmentUrl = editMaterial!!.attachmentUrl,
                    newAttachmentUri = selectedAttachmentUri
                )
                Toast.makeText(requireContext(), "Updating...", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.createMaterial(subject, title, description, selectedMaterialType, selectedClassId!!, selectedAttachmentUri, null)
                Toast.makeText(requireContext(), "Uploading...", Toast.LENGTH_SHORT).show()
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(studyMaterial: StudyMaterial?): AddStudyMaterialDialog {
            val fragment = AddStudyMaterialDialog()
            val args = Bundle()
            if (studyMaterial != null) {
                args.putParcelable("study_material", studyMaterial)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
