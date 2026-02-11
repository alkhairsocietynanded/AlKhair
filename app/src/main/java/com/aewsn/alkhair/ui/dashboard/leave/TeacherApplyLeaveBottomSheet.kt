package com.aewsn.alkhair.ui.dashboard.leave

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aewsn.alkhair.databinding.BottomSheetApplyLeaveBinding
import com.aewsn.alkhair.ui.dashboard.TeacherDashboardViewModel
import com.aewsn.alkhair.utils.UiState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class TeacherApplyLeaveBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetApplyLeaveBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TeacherDashboardViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetApplyLeaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDatePickers()
        setupSubmitButton()
        observeState()
    }

    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener { showDatePicker { date -> binding.etStartDate.setText(date) } }
        binding.etEndDate.setOnClickListener { showDatePicker { date -> binding.etEndDate.setText(date) } }
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = "$year-${month + 1}-$day"
                onDateSelected(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupSubmitButton() {
        binding.btnSubmitLeave.setOnClickListener {
            val startDate = binding.etStartDate.text.toString()
            val endDate = binding.etEndDate.text.toString()
            val reason = binding.etReason.text.toString()

            if (startDate.isEmpty() || endDate.isEmpty() || reason.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.applyLeave(startDate, endDate, reason)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.leaveSubmissionState.collectLatest { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.btnSubmitLeave.isEnabled = false
                        binding.btnSubmitLeave.text = "Submitting..."
                    }
                    is UiState.Success -> {
                        Toast.makeText(requireContext(), "Leave Applied Successfully!", Toast.LENGTH_LONG).show()
                        dismiss()
                        viewModel.resetLeaveState()
                    }
                    is UiState.Error -> {
                        binding.btnSubmitLeave.isEnabled = true
                        binding.btnSubmitLeave.text = "Submit Application"
                        Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                        viewModel.resetLeaveState()
                    }
                    else -> {
                        binding.btnSubmitLeave.isEnabled = true
                        binding.btnSubmitLeave.text = "Submit Application"
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
        const val TAG = "TeacherApplyLeaveBottomSheet"
    }
}
