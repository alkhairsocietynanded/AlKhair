package com.aewsn.alkhair.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.databinding.FragmentResultDetailBinding
import com.aewsn.alkhair.utils.UiState
import com.aewsn.alkhair.utils.gone
import com.aewsn.alkhair.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResultDetailFragment : Fragment() {

    private var _binding: FragmentResultDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by activityViewModels()
    
    // Args
    private var examId: String = ""
    private var examTitle: String = ""

    companion object {
        private const val ARG_EXAM_ID = "arg_exam_id"
        private const val ARG_EXAM_TITLE = "arg_exam_title"

        fun newInstance(examId: String, examTitle: String) = ResultDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_EXAM_ID, examId)
                putString(ARG_EXAM_TITLE, examTitle)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            examId = it.getString(ARG_EXAM_ID, "")
            examTitle = it.getString(ARG_EXAM_TITLE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set Title in Toolbar (if activity logic allows, but simpler to just show it in layout if needed)
        // Since we are in a Fragment, accessing Activity toolbar is a bit coupled. 
        // We added BackStack in ListFragment, so Activity title changes might be needed.
        (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = examTitle
        
        setupRecyclerView()
        
        // Trigger fetch
        if (examId.isNotEmpty()) {
            viewModel.fetchResultsForExam(examId)
        }
        
        observeData()
    }

    private fun setupRecyclerView() {
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.results.collectLatest { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.progressBar.visible()
                            binding.cardSummary.gone()
                            binding.rvResults.gone()
                            binding.tvEmpty.gone()
                        }
                        is UiState.Error -> {
                            binding.progressBar.gone()
                            binding.cardSummary.gone()
                            binding.rvResults.gone()
                            binding.tvEmpty.text = state.message
                            binding.tvEmpty.visible()
                        }
                        is UiState.Success -> {
                            binding.progressBar.gone()
                            val results = state.data
                            if (results.isEmpty()) {
                                binding.cardSummary.gone()
                                binding.rvResults.gone()
                                binding.tvEmpty.visible()
                            } else {
                                binding.tvEmpty.gone()
                                binding.cardSummary.visible()
                                binding.rvResults.visible()
                                
                                val adapter = ResultDetailAdapter(results)
                                binding.rvResults.adapter = adapter
                                
                                // Calculate Totals
                                val totalMarksObtained = results.sumOf { it.result.marksObtained }
                                val totalMaxMarks = results.sumOf { it.result.totalMarks }
                                val percentage = if (totalMaxMarks > 0) (totalMarksObtained / totalMaxMarks) * 100 else 0.0
                                
                                binding.tvTotalPercentage.text = String.format("%.1f%%", percentage)
                                binding.tvTotalMarks.text = "Total: ${totalMarksObtained.toInt()} / ${totalMaxMarks.toInt()}"
                                
                                // Grade Logic (Simple)
                                val grade = when {
                                    percentage >= 90 -> "A+"
                                    percentage >= 80 -> "A"
                                    percentage >= 70 -> "B"
                                    percentage >= 60 -> "C"
                                    percentage >= 50 -> "D"
                                    else -> "F"
                                }
                                binding.tvGrade.text = "Grade: $grade"
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
