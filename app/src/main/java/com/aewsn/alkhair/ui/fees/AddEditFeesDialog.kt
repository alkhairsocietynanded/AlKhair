package com.aewsn.alkhair.ui.fees

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
import com.aewsn.alkhair.data.models.FeesModel
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.DialogAddEditFeesBinding
import com.aewsn.alkhair.utils.DateUtils
import com.aewsn.alkhair.utils.FeeUtils
import com.aewsn.alkhair.utils.getParcelableCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
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

    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupForm()
        setupTextWatchers()
        setupButtons()
    }

    private fun setupDropdowns() {
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
                val (year, monthStr, _) = it.feeDate.split("-")
                // Convert month "01" -> "January"
                try {
                    val monthIndex = monthStr.toInt()
                    val monthName = months.getOrElse(monthIndex - 1) { "" }
                    binding.spinnerMonth.setText(monthName, false)
                    binding.spinnerYear.setText(year, false)
                } catch (_: Exception) {
                    // Fallback
                }

                if (it.paymentDate.isNotBlank()) {
                    try {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.paymentDate)
                        if (date != null) {
                            binding.etPaymentDate.setText(
                                SimpleDateFormat(
                                    "dd MMM yyyy",
                                    Locale.US
                                ).format(date)
                            )
                            binding.etPaymentDate.tag = it.paymentDate
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            // Add Mode
            binding.tvDialogTitle.text = "Add Fee"
            binding.btnSave.text = "Save"

            // Auto-select current month and year using DateUtils
            binding.spinnerMonth.setText(DateUtils.currentMonthNameFull(), false)
            binding.spinnerYear.setText(DateUtils.currentYear(), false)

            // Auto-fill Payment Date with Today
            val todayCal = java.util.Calendar.getInstance()
            val displayDate = DateUtils.formatDate(todayCal, "dd MMM yyyy")
            val dbDate = DateUtils.formatDate(todayCal, "yyyy-MM-dd")

            binding.etPaymentDate.setText(displayDate)
            binding.etPaymentDate.tag = dbDate
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
        // Date Picker Logic
        val dateClick = View.OnClickListener {
            val calendar = java.util.Calendar.getInstance()
            if (!binding.etPaymentDate.text.isNullOrBlank()) {
                try {
                    // Tag contains "yyyy-MM-dd"
                    val date = SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                    ).parse(binding.etPaymentDate.tag.toString())
                    if (date != null) {
                        calendar.time = date
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            DateUtils.showMaterialDatePicker(
                parentFragmentManager,
                calendar
            ) { newCalendar ->
                val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val date = newCalendar.time

                binding.etPaymentDate.setText(displayFormat.format(date))
                binding.etPaymentDate.tag = dbFormat.format(date) // Save DB format in tag
            }
        }

        binding.etPaymentDate.setOnClickListener(dateClick)
        binding.tilPaymentDate.setEndIconOnClickListener(dateClick)

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


            // ✅ FIX: Construct proper feeDate (YYYY-MM-01) for the fee month
            val monthName = binding.spinnerMonth.text.toString()
            val monthIndex = months.indexOf(monthName) + 1
            val monthStr = String.format(Locale.US, "%02d", monthIndex)
            val year = binding.spinnerYear.text.toString()
            
            val feeDate = "$year-$monthStr-01" // Set to 1st of the month

            val base = binding.etBaseAmount.text.toString().toDoubleOrNull() ?: 0.0
            val paid = binding.etPaidAmount.text.toString().toDoubleOrNull() ?: 0.0
            val disc = binding.etDiscounts.text.toString().toDoubleOrNull() ?: 0.0
            val remarks = binding.etNotes.text.toString()

            // Read Payment Date from UI (tag holds the DB format yyyy-MM-dd)
            val paymentDate = binding.etPaymentDate.tag?.toString() ?: ""

            if (base <= 0) {
                binding.tilBaseAmount.error = "Base Amount must be greater than 0"
                return@setOnClickListener
            }
            if (monthName.isBlank() || year.isBlank()) {
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
                feeDate = feeDate,
                baseAmount = base,
                paidAmount = paid,
                discounts = disc,
                dueAmount = dueAmount,
                netFees = base - disc,
                paymentStatus = paymentStatus,
                remarks = remarks,
                paymentDate = paymentDate
            ) ?: FeesModel(
                id = "", // New ID will be generated by Repo
                studentId = student.uid,
                studentName = student.name,
                classId = student.classId,
                shift = student.shift,
                feeDate = feeDate,
                baseAmount = base,
                paidAmount = paid,
                dueAmount = dueAmount,
                discounts = disc,
                netFees = base - disc,
                paymentStatus = paymentStatus,
                remarks = remarks,
                paymentDate = paymentDate
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
