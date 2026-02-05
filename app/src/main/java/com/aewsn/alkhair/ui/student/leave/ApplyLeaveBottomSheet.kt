package com.aewsn.alkhair.ui.student.leave

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aewsn.alkhair.databinding.BottomSheetApplyLeaveBinding
import com.aewsn.alkhair.ui.student.StudentViewModel
import com.aewsn.alkhair.utils.UiState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class ApplyLeaveBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetApplyLeaveBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StudentViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetApplyLeaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var leaveToEdit: com.aewsn.alkhair.data.models.Leave? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        checkEditMode()
        setupDatePickers()
        setupSubmitButton()
        observeState()
    }

    private fun checkEditMode() {
        arguments?.getString("leave_json")?.let { json ->
            try {
                leaveToEdit = kotlinx.serialization.json.Json.decodeFromString<com.aewsn.alkhair.data.models.Leave>(json)
                leaveToEdit?.let { leave ->
                    binding.etStartDate.setText(leave.startDate)
                    binding.etEndDate.setText(leave.endDate)
                    binding.etReason.setText(leave.reason)
                    binding.btnSubmitLeave.text = "Update Application"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

            if (leaveToEdit != null) {
                val updatedLeave = leaveToEdit!!.copy(
                    startDate = startDate,
                    endDate = endDate,
                    reason = reason
                )
                viewModel.updateLeave(updatedLeave)
            } else {
                viewModel.applyLeave(startDate, endDate, reason)
            }
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
        const val TAG = "ApplyLeaveBottomSheet"
        
        fun newInstance(leave: com.aewsn.alkhair.data.models.Leave? = null): ApplyLeaveBottomSheet {
            val fragment = ApplyLeaveBottomSheet()
            if (leave != null) {
                val args = Bundle()
                args.putString("leave_json", kotlinx.serialization.json.Json.encodeToString(leave))
                fragment.arguments = args
            }
            return fragment
        }
    }
}
