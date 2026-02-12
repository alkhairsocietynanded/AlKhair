package com.aewsn.alkhair.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aewsn.alkhair.data.manager.UserRepoManager
import com.aewsn.alkhair.data.local.dao.SubjectDao
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.DialogAddResultBinding
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddResultDialog : BottomSheetDialogFragment() {

    private val viewModel: ResultViewModel by activityViewModels()

    @Inject lateinit var userRepoManager: UserRepoManager
    @Inject lateinit var subjectDao: SubjectDao

    private var _binding: DialogAddResultBinding? = null
    private val binding get() = _binding!!

    private var examId: String = ""
    private var examClassId: String? = null
    private var subjectList: List<Subject> = emptyList()
    private var selectedSubjectId: String? = null
    private var adapter: StudentMarksAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        examId = arguments?.getString("exam_id") ?: ""
        examClassId = arguments?.getString("exam_class_id")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSubjectDropdown()
        loadStudents()
        observeMutationState()

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSaveResults.setOnClickListener {
            if (validateInput()) {
                submitResults()
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
            binding.etSubject.setAdapter(subjectAdapter)

            binding.etSubject.setOnItemClickListener { _, _, position, _ ->
                selectedSubjectId = subjectList[position].id
            }
        }
    }

    private fun loadStudents() {
        binding.progressStudents.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val classId = examClassId
            if (classId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "No class assigned to exam", Toast.LENGTH_LONG).show()
                binding.progressStudents.isVisible = false
                return@launch
            }

            // Fetch students by role=STUDENT from local DB, then filter by classId
            val students = userRepoManager.observeUsersByRole(Roles.STUDENT).first()
                .filter { it.classId == classId }
                .sortedBy { it.name }

            binding.progressStudents.isVisible = false

            if (students.isEmpty()) {
                Toast.makeText(requireContext(), "No students found for this class", Toast.LENGTH_LONG).show()
                return@launch
            }

            val entries = students.map { student ->
                ResultViewModel.StudentMarkEntry(
                    studentId = student.uid,
                    studentName = student.name
                )
            }.toMutableList()

            adapter = StudentMarksAdapter(entries)
            binding.rvStudentMarks.adapter = adapter
        }
    }

    private fun submitResults() {
        val entries = adapter?.getEntries() ?: return
        val totalMarks = binding.etTotalMarks.text.toString().toIntOrNull() ?: 0

        viewModel.saveResults(
            examId = examId,
            subjectId = selectedSubjectId ?: "",
            totalMarks = totalMarks,
            entries = entries.filter { it.marksObtained >= 0 }
        )
    }

    private fun observeMutationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.resultMutationState.collect { state ->
                    when (state) {
                        is UiState.Loading -> binding.btnSaveResults.isEnabled = false
                        is UiState.Success -> {
                            binding.btnSaveResults.isEnabled = true
                            Toast.makeText(requireContext(), "Results Saved", Toast.LENGTH_SHORT).show()
                            dismiss()
                            viewModel.resetResultMutationState()
                        }
                        is UiState.Error -> {
                            binding.btnSaveResults.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetResultMutationState()
                        }
                        else -> binding.btnSaveResults.isEnabled = true
                    }
                }
            }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (binding.etSubject.text.isNullOrBlank()) {
            binding.tilSubject.error = "Subject is required"
            isValid = false
        } else binding.tilSubject.error = null

        if (binding.etTotalMarks.text.isNullOrBlank()) {
            binding.tilTotalMarks.error = "Total marks is required"
            isValid = false
        } else binding.tilTotalMarks.error = null

        val entries = adapter?.getEntries()
        if (entries.isNullOrEmpty() || entries.none { it.marksObtained >= 0 }) {
            Toast.makeText(requireContext(), "Enter marks for at least one student", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(examId: String, classId: String?): AddResultDialog {
            return AddResultDialog().apply {
                arguments = Bundle().apply {
                    putString("exam_id", examId)
                    putString("exam_class_id", classId)
                }
            }
        }
    }
}
