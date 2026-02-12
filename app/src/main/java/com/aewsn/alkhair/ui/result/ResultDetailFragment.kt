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
import com.aewsn.alkhair.databinding.FragmentResultDetailBinding
import com.aewsn.alkhair.utils.Roles
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
    private var examClassId: String? = null

    companion object {
        private const val ARG_EXAM_ID = "arg_exam_id"
        private const val ARG_EXAM_TITLE = "arg_exam_title"
        private const val ARG_EXAM_CLASS_ID = "arg_exam_class_id"

        fun newInstance(examId: String, examTitle: String, classId: String? = null) =
            ResultDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EXAM_ID, examId)
                    putString(ARG_EXAM_TITLE, examTitle)
                    putString(ARG_EXAM_CLASS_ID, classId)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            examId = it.getString(ARG_EXAM_ID, "")
            examTitle = it.getString(ARG_EXAM_TITLE, "")
            examClassId = it.getString(ARG_EXAM_CLASS_ID)
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

        (requireActivity() as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title =
            examTitle

        setupRecyclerView()
        setupFab()

        if (examId.isNotEmpty()) {
            viewModel.fetchResultsForExam(examId)
        }

        observeData()
    }

    private fun setupRecyclerView() {
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupFab() {
        binding.fabAddResult.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            val user = viewModel.fetchCurrentUser()
            if (user != null) {
                val role = user.role.trim()
                val canManage = role.equals(Roles.TEACHER, ignoreCase = true) ||
                        role.equals(Roles.ADMIN, ignoreCase = true)
                binding.fabAddResult.isVisible = canManage
            }
        }

        binding.fabAddResult.setOnClickListener {
            val dialog = AddResultDialog.newInstance(examId, examClassId)
            dialog.show(childFragmentManager, "AddResultDialog")
        }
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
                                val totalMarksObtained =
                                    results.sumOf { it.result.marksObtained }
                                val totalMaxMarks = results.sumOf { it.result.totalMarks }
                                val percentage: Double =
                                    if (totalMaxMarks > 0) (totalMarksObtained / totalMaxMarks * 100) else 0.0

                                binding.tvTotalPercentage.text =
                                    String.format("%.1f%%", percentage)
                                binding.tvTotalMarks.text =
                                    "$totalMarksObtained / $totalMaxMarks"

                                val grade = when {
                                    percentage >= 90 -> "A+"
                                    percentage >= 80 -> "A"
                                    percentage >= 70 -> "B"
                                    percentage >= 60 -> "C"
                                    percentage >= 50 -> "D"
                                    else -> "F"
                                }
                                binding.tvGrade.text = grade
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
