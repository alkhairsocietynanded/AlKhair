package com.zabibtech.alkhair.ui.fees

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.DialogAddEditFeesBinding
import com.zabibtech.alkhair.utils.FeeUtils
import com.zabibtech.alkhair.utils.getParcelableCompat
import java.text.NumberFormat
import java.util.Locale

class AddEditFeesDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddEditFeesBinding? = null
    private val binding get() = _binding!!
    private val feesViewModel: FeesViewModel by viewModels(ownerProducer = { requireParentFragment() }) // Use parent fragment's ViewModel

    private var existingFees: FeesModel? = null
    private var user: User? = null

    companion object {
        fun newInstance(user: User?, fees: FeesModel? = null) = AddEditFeesDialog().apply {
            arguments = Bundle().apply {
                putParcelable("user", user)
                putParcelable("fees", fees)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            user = it.getParcelableCompat("user", User::class.java)
            existingFees = it.getParcelableCompat("fees", FeesModel::class.java)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditFeesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupForm()
        setupTextWatchers()
        setupButtons()
    }

    private fun setupDropdowns() {
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val years = (2024..2050).map { it.toString() }

        binding.spinnerMonth.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, months)
        )
        binding.spinnerYear.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, years)
        )
    }

    private fun setupForm() {
        if (existingFees != null) {
            // Edit Mode
            binding.tvDialogTitle.text = "Edit Fee"
            binding.btnSave.text = "Update"
            existingFees?.let {
                binding.etBaseAmount.setText(it.baseAmount.toString())
                binding.etPaidAmount.setText(it.paidAmount.toString())
                binding.etDiscounts.setText(it.discounts.toString())
                binding.etNotes.setText(it.remarks)
                val (year, month) = it.monthYear.split("-")
                binding.spinnerMonth.setText(month, false)
                binding.spinnerYear.setText(year, false)
            }
        } else {
            // Add Mode
            binding.tvDialogTitle.text = "Add Fee"
            binding.btnSave.text = "Save"
        }
        updateCalculatedNet()
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateCalculatedNet()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etBaseAmount.addTextChangedListener(watcher)
        binding.etPaidAmount.addTextChangedListener(watcher)
        binding.etDiscounts.addTextChangedListener(watcher)
    }

    private fun updateCalculatedNet() {
        val basic = binding.etBaseAmount.text.toString().toDoubleOrNull() ?: 0.0
        val paid = binding.etPaidAmount.text.toString().toDoubleOrNull() ?: 0.0
        val disc = binding.etDiscounts.text.toString().toDoubleOrNull() ?: 0.0

        val net = basic - (paid + disc)
        val formatted = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        ).format(net)
        binding.tvCalculatedNetFees.text = "Calculated: $formatted"
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            val student = user
            if (student == null) {
                Toast.makeText(
                    requireContext(),
                    "Student information is missing.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }


            // ✅ FIX: Month Name ko Short karein (January -> Jan)
            // Taaki ye Dashboard ke logic (YYYY-MMM) se match kare
            val month = binding.spinnerMonth.text.toString().take(3)
            val year = binding.spinnerYear.text.toString()
            val base = binding.etBaseAmount.text.toString().toDoubleOrNull() ?: 0.0
            val paid = binding.etPaidAmount.text.toString().toDoubleOrNull() ?: 0.0
            val disc = binding.etDiscounts.text.toString().toDoubleOrNull() ?: 0.0
            val remarks = binding.etNotes.text.toString()

            if (base <= 0) {
                binding.tilBaseAmount.error = "Base Amount must be greater than 0"
                return@setOnClickListener
            }
            if (month.isBlank() || year.isBlank()) {
                Toast.makeText(requireContext(), "Please select month and year", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val paymentStatus = FeeUtils.calculatePaymentStatus(base, disc, paid)
            val dueAmount = FeeUtils.calculateDueAmount(base, disc, paid)

            val fees = existingFees?.copy(
                studentId = student.uid,
                studentName = student.name,
                classId = student.classId,
                shift = student.shift,
                monthYear = "$year-$month",
                baseAmount = base,
                paidAmount = paid,
                discounts = disc,
                dueAmount = dueAmount,
                netFees = base - disc,
                paymentStatus = paymentStatus,
                remarks = remarks
            ) ?: FeesModel(
                studentId = student.uid,
                studentName = student.name,
                classId = student.classId,
                shift = student.shift,
                monthYear = "$year-$month",
                baseAmount = base,
                paidAmount = paid,
                dueAmount = dueAmount,
                discounts = disc,
                netFees = base - disc,
                paymentStatus = paymentStatus,
                remarks = remarks
            )

            feesViewModel.saveFee(fees)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
