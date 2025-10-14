package com.zabibtech.alkhair.ui.user.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.FragmentFeesBinding
import com.zabibtech.alkhair.ui.fees.FeesViewModel
import com.zabibtech.alkhair.ui.user.adapters.FeesAdapter
import com.zabibtech.alkhair.utils.DateUtils
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FeesFragment : Fragment() {

    private var user: User? = null
    private var _binding: FragmentFeesBinding? = null
    private val binding get() = _binding!!

    private val feesViewModel: FeesViewModel by viewModels()
    private lateinit var adapter: FeesAdapter

    private var studentId: String? = null

    companion object {
        private const val ARG_USER = "arg_user"

        fun newInstance(user: User) = FeesFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_USER, user)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_USER, User::class.java)
        } else {
            arguments?.getParcelable(ARG_USER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()

        binding.fabAddFee.setOnClickListener {
            showAddFeeDialog()
        }

        studentId = user?.uid
        studentId?.let {
            feesViewModel.loadFeesByStudent(it)
        } ?: DialogUtils.showAlert(requireContext(), message = "Invalid student ID")
    }

    private fun setupRecyclerView() {
        adapter = FeesAdapter(
            onDeleteClick = { fee ->
                DialogUtils.showConfirmation(
                    requireContext(),
                    title = "Delete FeesModel",
                    message = "Are you sure you want to delete this fee record?",
                    onConfirmed = { feesViewModel.deleteFee(fee.id) }
                )
            },
            onEditClick = { fee ->
                showAddFeeDialog(fee) // open dialog with pre-filled data
            }
        )

        binding.recyclerViewFees.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFees.adapter = adapter
    }

    private fun setupObservers() {
        // 🔹 Collect FeesModel List
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                feesViewModel.feesModelListState.collectLatest { state ->
                    when (state) {
                        is UiState.Idle -> Unit
                        is UiState.Loading -> DialogUtils.showLoading(parentFragmentManager)
                        is UiState.Success -> {
                            DialogUtils.hideLoading(parentFragmentManager)
                            val fees = state.data
                            adapter.submitList(fees)
                            binding.emptyView.visibility =
                                if (fees.isEmpty()) View.VISIBLE else View.GONE

                            // 🔹 Update summary
                            updateFeeDashboard(fees)
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(parentFragmentManager)
                            DialogUtils.showAlert(requireContext(), message = state.message)
                        }
                    }
                }
            }
        }

        // 🔹 Collect Save/Delete States
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                feesViewModel.feeState.collectLatest { state ->
                    when (state) {
                        is UiState.Idle -> Unit
                        is UiState.Loading -> DialogUtils.showLoading(parentFragmentManager)
                        is UiState.Success -> {
                            DialogUtils.hideLoading(parentFragmentManager)
                            Toast.makeText(
                                requireContext(),
                                "Operation completed successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            studentId?.let { feesViewModel.loadFeesByStudent(it) } // Refresh list
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(parentFragmentManager)
                            DialogUtils.showAlert(requireContext(), message = state.message)
                        }
                    }
                }
            }
        }
    }

    private fun updateFeeDashboard(feesModels: List<FeesModel>) {
        val totalFees = user?.totalFees?.toDoubleOrNull() ?: 0.0
        val totalPaid = feesModels.sumOf { it.paidAmount }
        val totalDue = totalFees - totalPaid

        // Prevent negative due (in case of overpayment)
        val adjustedDue = if (totalDue < 0) 0.0 else totalDue

        binding.tvTotalFeesAmount.text = "₹$totalFees"
        binding.tvTotalPaidAmount.text = "₹$totalPaid"
        binding.tvTotalDueAmount.text = "₹$adjustedDue"
    }

    private fun showAddFeeDialog(feesModelToEdit: FeesModel? = null) {
        val dialogBinding =
            com.zabibtech.alkhair.databinding.DialogAddFeeBinding.inflate(layoutInflater)

        // 🔹 Get current year
        val currentYear = DateUtils.currentYear()

        // 🔹 Combine months with year (e.g. "January 2025")
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        ).map { "$it $currentYear" }

        val monthAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            months
        )
        dialogBinding.spinnerMonth.adapter = monthAdapter
        dialogBinding.spinnerMonth.setSelection(DateUtils.getCurrentMonthIndex())

        // If editing, pre-fill values
        feesModelToEdit?.let { fee ->
            dialogBinding.etTotalAmount.setText(fee.totalAmount.toString())
            dialogBinding.etPaidAmount.setText(fee.paidAmount.toString())
            val index = months.indexOf(fee.month)
            if (index >= 0) dialogBinding.spinnerMonth.setSelection(index)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // ==========================
        // 🔹 Real-time Due & Status
        // ==========================
        val updateDueAndStatus: () -> Unit = {
            val total = dialogBinding.etTotalAmount.text.toString().toDoubleOrNull() ?: 0.0
            val paid = dialogBinding.etPaidAmount.text.toString().toDoubleOrNull() ?: 0.0
            val due = total - paid
            dialogBinding.tvDueAmount.text = "Due Amount: ₹$due"
        }

        dialogBinding.etTotalAmount.addTextChangedListener { updateDueAndStatus() }
        dialogBinding.etPaidAmount.addTextChangedListener { updateDueAndStatus() }

        // ==========================
        // 🔹 Save Button Action
        // ==========================
        dialogBinding.btnSaveFee.setOnClickListener {
            val total = dialogBinding.etTotalAmount.text.toString().toDoubleOrNull()
            val paid = dialogBinding.etPaidAmount.text.toString().toDoubleOrNull()
            val month = dialogBinding.spinnerMonth.selectedItem.toString() // e.g. "January 2025"

            // Validations
            if (total == null || total <= 0.0) {
                DialogUtils.showAlert(
                    requireContext(),
                    message = "Please enter a valid total amount (> 0)"
                )
                return@setOnClickListener
            }
            if (paid == null || paid < 0.0) {
                DialogUtils.showAlert(
                    requireContext(),
                    message = "Please enter a valid paid amount (>= 0)"
                )
                return@setOnClickListener
            }
            if (paid > total) {
                DialogUtils.showAlert(
                    requireContext(),
                    message = "Paid amount cannot exceed total amount"
                )
                return@setOnClickListener
            }

            val due = total - paid
            val status = when {
                paid <= 0.0 -> "Unpaid"
                paid in 0.0..total - 0.01 -> "Partially Paid"
                paid >= total -> "Paid"
                else -> "Unpaid"
            }

            val feesModel = FeesModel(
                id = feesModelToEdit?.id ?: "",
                studentId = studentId ?: "",
                month = month, // Already includes "January 2025"
                totalAmount = total,
                paidAmount = paid,
                dueAmount = due,
                status = status,
                paymentDate = if (paid > 0) DateUtils.today() else "-"
            )

            feesViewModel.saveFee(feesModel)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}