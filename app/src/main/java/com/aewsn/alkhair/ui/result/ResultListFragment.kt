package com.aewsn.alkhair.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.FragmentResultListBinding
import com.aewsn.alkhair.utils.Roles
import com.aewsn.alkhair.utils.UiState
import com.aewsn.alkhair.utils.gone
import com.aewsn.alkhair.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResultListFragment : Fragment() {

    private var _binding: FragmentResultListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        binding.rvExams.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFab() {
        // Initially hidden, will show based on role
        binding.fabAddExam.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val user = viewModel.fetchCurrentUser()
            if (user != null) {
                val role = user.role.trim()
                // Show FAB for Teacher and Admin only
                val canManage = role.equals(Roles.TEACHER, ignoreCase = true) ||
                        role.equals(Roles.ADMIN, ignoreCase = true)
                binding.fabAddExam.isVisible = canManage
            }
        }

        binding.fabAddExam.setOnClickListener {
            val dialog = AddExamDialog.newInstance()
            dialog.show(childFragmentManager, "AddExamDialog")
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exams.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.progressBar.visible()
                            binding.tvEmpty.gone()
                            binding.rvExams.gone()
                        }
                        is UiState.Error -> {
                            binding.progressBar.gone()
                            binding.tvEmpty.text = state.message
                            binding.tvEmpty.visible()
                            binding.rvExams.gone()
                        }
                        is UiState.Success -> {
                            binding.progressBar.gone()
                            if (state.data.isEmpty()) {
                                binding.tvEmpty.visible()
                                binding.rvExams.gone()
                            } else {
                                binding.tvEmpty.gone()
                                binding.rvExams.visible()
                                val adapter = ExamAdapter(state.data) { exam ->
                                    parentFragmentManager.beginTransaction()
                                        .replace(
                                            R.id.container,
                                            ResultDetailFragment.newInstance(exam.id, exam.title, exam.classId)
                                        )
                                        .addToBackStack(null)
                                        .commit()
                                }
                                binding.rvExams.adapter = adapter
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
