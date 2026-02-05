package com.aewsn.alkhair.ui.approval

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.databinding.FragmentLeaveApprovalBinding
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LeaveApprovalFragment : Fragment() {

    private var _binding: FragmentLeaveApprovalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LeaveApprovalViewModel by viewModels()
    private lateinit var adapter: LeaveApprovalAdapter
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Retrieve User passed from Dashboard/Activity
            // Logic to get User depends on navigation; assuming passed as Parcelable
            @Suppress("DEPRECATION")
            currentUser = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("user", User::class.java)
            } else {
                it.getParcelable("user")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaveApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        
        currentUser?.let {
            viewModel.loadLeaves(it)
        } ?: run {
            Toast.makeText(requireContext(), "Error: User not found", Toast.LENGTH_SHORT).show()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            currentUser?.let { viewModel.loadLeaves(it) }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        adapter = LeaveApprovalAdapter(
            onApprove = { leave -> viewModel.approveLeave(leave) },
            onReject = { leave -> viewModel.rejectLeave(leave) }
        )
        binding.rvLeaveRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LeaveApprovalFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.leavesState.collectLatest { state ->
                        when (state) {
                            is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                            is UiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                adapter.submitList(state.data)
                                binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                }
                
                launch {
                    viewModel.actionState.collectLatest { state ->
                        when (state) {
                            is UiState.Loading -> {
                                // Optional: Show overlay loading or keep UI responsive
                            }
                            is UiState.Success -> {
                                Toast.makeText(requireContext(), "Status Updated Successfully", Toast.LENGTH_SHORT).show()
                                viewModel.resetActionState()
                                currentUser?.let { viewModel.loadLeaves(it) } // Refresh list
                            }
                            is UiState.Error -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetActionState()
                            }
                            else -> {}
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
    
    companion object {
        fun newInstance(user: User) = LeaveApprovalFragment().apply {
            arguments = Bundle().apply {
                putParcelable("user", user)
            }
        }
    }
}
