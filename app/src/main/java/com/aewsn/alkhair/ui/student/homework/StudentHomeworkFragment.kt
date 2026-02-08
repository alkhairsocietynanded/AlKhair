package com.aewsn.alkhair.ui.student.homework

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aewsn.alkhair.databinding.FragmentStudentHomeworkBinding
import com.aewsn.alkhair.ui.homework.HomeworkAdapter
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentHomeworkFragment : Fragment() {

    private var _binding: FragmentStudentHomeworkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudentHomeworkViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // Read-only adapter (null callbacks)
        val adapter = HomeworkAdapter(
            onEdit = null,
            onDelete = null
        )
        binding.rvStudentHomework.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeworkListState.collectLatest { state ->
                    binding.progressBar.isVisible = state is UiState.Loading
                    
                    when (state) {
                        is UiState.Success -> {
                            val list = state.data
                            (binding.rvStudentHomework.adapter as HomeworkAdapter).submitList(list)
                            
                            binding.rvStudentHomework.isVisible = list.isNotEmpty()
                            binding.tvEmptyState.isVisible = list.isEmpty()
                        }
                        is UiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            binding.tvEmptyState.isVisible = true
                            binding.tvEmptyState.text = state.message
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = StudentHomeworkFragment()
    }
}
