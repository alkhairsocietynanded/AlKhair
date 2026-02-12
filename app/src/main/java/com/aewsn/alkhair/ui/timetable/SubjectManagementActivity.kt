package com.aewsn.alkhair.ui.timetable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.databinding.ActivitySubjectManagementBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubjectManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectManagementBinding
    private val viewModel: SubjectViewModel by viewModels()
    private lateinit var subjectAdapter: SubjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        subjectAdapter = SubjectAdapter(
            onDeleteClick = { subject ->
                showDeleteConfirmation(subject)
            },
            onItemClick = { subject ->
                showEditSubjectDialog(subject)
            }
        )
        binding.rvSubjects.apply {
            layoutManager = LinearLayoutManager(this@SubjectManagementActivity)
            adapter = subjectAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddSubject.setOnClickListener {
            showAddSubjectDialog()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Subjects List
                launch {
                    viewModel.subjects.collect { subjects ->
                        subjectAdapter.submitList(subjects)
                        binding.tvEmpty.isVisible = subjects.isEmpty()
                    }
                }

                // Observe UI State (Loading/Error/Success)
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is SubjectUiState.Idle -> {
                                binding.progressBar.isVisible = false
                            }
                            is SubjectUiState.Loading -> {
                                binding.progressBar.isVisible = true
                            }
                            is SubjectUiState.Success -> {
                                binding.progressBar.isVisible = false
                                Toast.makeText(this@SubjectManagementActivity, state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetUiState()
                            }
                            is SubjectUiState.Error -> {
                                binding.progressBar.isVisible = false
                                Toast.makeText(this@SubjectManagementActivity, state.message, Toast.LENGTH_LONG).show()
                                viewModel.resetUiState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showAddSubjectDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSubjectName)
        val etCode = dialogView.findViewById<EditText>(R.id.etSubjectCode)

        AlertDialog.Builder(this)
            .setTitle("Add Subject")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addSubject(name, code)
                } else {
                    Toast.makeText(this, "Subject name is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSubjectDialog(subject: Subject) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_subject, null)
        val etName = dialogView.findViewById<EditText>(R.id.etSubjectName)
        val etCode = dialogView.findViewById<EditText>(R.id.etSubjectCode)
        
        etName.setText(subject.name)
        etCode.setText(subject.code)

        AlertDialog.Builder(this)
            .setTitle("Edit Subject")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()
                if (name.isNotEmpty()) {
                    val updatedSubject = subject.copy(name = name, code = code)
                    viewModel.updateSubject(updatedSubject)
                } else {
                    Toast.makeText(this, "Subject name is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(subject: Subject) {
        AlertDialog.Builder(this)
            .setTitle("Delete Subject")
            .setMessage("Are you sure you want to delete '${subject.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSubject(subject.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
