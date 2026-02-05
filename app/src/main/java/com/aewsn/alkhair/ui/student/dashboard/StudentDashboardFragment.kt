package com.aewsn.alkhair.ui.student.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aewsn.alkhair.databinding.FragmentStudentDashboardBinding
import com.aewsn.alkhair.ui.student.StudentViewModel
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentDashboardFragment : Fragment() {

    private var _binding: FragmentStudentDashboardBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel from Activity (StudentViewModel)
    private val viewModel: StudentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupListeners()
        observeUserData()
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            startActivity(Intent(requireContext(), com.aewsn.alkhair.ui.auth.LoginActivity::class.java))
            requireActivity().finishAffinity()
        }
        
        binding.cardApplyLeave.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Leave application coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        binding.cardSubmitHomework.setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Homework submission coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeUserData() {
        val announcementAdapter = DashboardAnnouncementAdapter()
        binding.rvAnnouncements.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            adapter = announcementAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentUser.collectLatest { state ->
                        when (state) {
                            is UiState.Success -> {
                                val user = state.data
                                binding.tvWelcomeName.text = "Hi, ${user.name} \uD83D\uDC4B"
                                
                                val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
                                binding.tvDate.text = dateFormat.format(java.util.Date())
                            }
                            is UiState.Error -> {
                                binding.tvWelcomeName.text = "Hi, Student"
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.attendancePercent.collectLatest { state ->
                        if (state is UiState.Success) {
                            val percent = state.data
                            binding.tvAttendancePercent.text = "$percent% Present"
                            binding.progressAttendance.progress = percent
                        }
                    }
                }

                launch {
                    viewModel.announcements.collectLatest { state ->
                        if (state is UiState.Success) {
                            announcementAdapter.submitList(state.data)
                        } else {
                            // Optionally clear list or show empty state
                            announcementAdapter.submitList(emptyList())
                        }
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
