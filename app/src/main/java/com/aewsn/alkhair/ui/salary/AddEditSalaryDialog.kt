package com.aewsn.alkhair.ui.salary

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.SalaryModel
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.DialogAddEditSalaryBinding
import com.aewsn.alkhair.utils.DateUtils
import java.text.NumberFormat
import java.util.Locale

class AddEditSalaryDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddEditSalaryBinding? = null
    private val binding get() = _binding!!
    private var existingSalary: SalaryModel? = null
    private var teachers: List<User> = emptyList()
    private var selectedTeacher: User? = null
    private val viewModel: SalaryViewModel by activityViewModels()
    
    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    companion object {
        fun newInstance(salary: SalaryModel?, teachers: List<User>) = AddEditSalaryDialog().apply {
            arguments = Bundle().apply {
                putParcelable("salary", salary)
                putParcelableArrayList("teachers", ArrayList(teachers))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                existingSalary = it.getParcelable("salary", SalaryModel::class.java)
                teachers = it.getParcelableArrayList("teachers", User::class.java) ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                existingSalary = it.getParcelable("salary")
                @Suppress("DEPRECATION")
                teachers = it.getParcelableArrayList("teachers") ?: emptyList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditSalaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupDropdowns()
        setupForm()
        setupTextWatchers()
        setupButtons()
    }

    private fun setupDropdowns() {
        // Teacher Dropdown
        val teacherNames = teachers.map { it.name }
        binding.etTeacherName.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, teacherNames)
        )
        binding.etTeacherName.setOnItemClickListener { _, _, position, _ ->
            selectedTeacher = teachers[position]
        }

        // Month and Year Dropdowns

        val years = (2024..2050).map { it.toString() }

        binding.spinnerMonth.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, months)
        )
        binding.spinnerYear.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, years)
        )
    }

    private fun setupForm() {
        existingSalary?.let {
            binding.tvDialogTitle.text = getString(R.string.edit_salary)
            binding.btnSave.text = getString(R.string.update)
            binding.etBaseSalary.setText(it.basicSalary.toString())
            binding.etAllowances.setText(it.allowances.toString())
            binding.etDeductions.setText(it.deductions.toString())
            binding.etNotes.setText(it.remarks)
            binding.etNotes.setText(it.remarks)
            
            // Parse YYYY-MM-DD or YYYY-MM
            val parts = it.salaryDate.split("-")
            if (parts.size >= 2) {
                val year = parts[0]
                val monthIndex = parts[1].toIntOrNull() ?: 1
                val monthName = if (monthIndex in 1..12) months[monthIndex - 1] else months[0]
                binding.spinnerMonth.setText(monthName, false)
                binding.spinnerYear.setText(year, false)
            }

            // Set selected teacher
            selectedTeacher = teachers.find { teacher -> teacher.uid == it.staffId }
            binding.etTeacherName.setText(selectedTeacher?.name, false)

            updateCalculatedNet()
        } ?: run {
            // Add Mode Defaults
            binding.tvDialogTitle.text = getString(R.string.add_salary)
            binding.btnSave.text = "Save"
            
            // Auto-select current month and year using DateUtils
            binding.spinnerMonth.setText(DateUtils.currentMonthNameFull(), false)
            binding.spinnerYear.setText(DateUtils.currentYear(), false)
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateCalculatedNet()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etBaseSalary.addTextChangedListener(watcher)
        binding.etAllowances.addTextChangedListener(watcher)
        binding.etDeductions.addTextChangedListener(watcher)
    }

    private fun updateCalculatedNet() {
        val basic = binding.etBaseSalary.text.toString().toDoubleOrNull() ?: 0.0
        val allow = binding.etAllowances.text.toString().toDoubleOrNull() ?: 0.0
        val deduc = binding.etDeductions.text.toString().toDoubleOrNull() ?: 0.0

        val net = basic + allow - deduc
        val formatted = NumberFormat.getCurrencyInstance(
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        ).format(net)
        binding.tvCalculatedNetSalary.text = "Calculated: $formatted"
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            val monthName = binding.spinnerMonth.text.toString()
            val year = binding.spinnerYear.text.toString()
            val base = binding.etBaseSalary.text.toString().toDoubleOrNull() ?: 0.0
            val allow = binding.etAllowances.text.toString().toDoubleOrNull() ?: 0.0
            val deduc = binding.etDeductions.text.toString().toDoubleOrNull() ?: 0.0
            val remarks = binding.etNotes.text.toString()

            if (selectedTeacher == null && existingSalary == null) {
                binding.tilTeacherName.error = "Please select a teacher"
                return@setOnClickListener
            }

            if (base <= 0) {
                binding.tilBaseSalary.error = "Base salary must be greater than 0"
                return@setOnClickListener
            }

            val finalStaffId = selectedTeacher?.uid ?: existingSalary!!.staffId
            val finalStaffName = selectedTeacher?.name ?: existingSalary!!.staffName

            // Convert Month Name to MM (01-12)
            val monthIndex = months.indexOf(monthName) + 1
            val formattedMonth = monthIndex.toString().padStart(2, '0')
            val salaryDateISO = "$year-$formattedMonth-01" // YYYY-MM-DD

            val salary = existingSalary?.copy(
                staffId = finalStaffId,
                staffName = finalStaffName,
                salaryDate = salaryDateISO,
                basicSalary = base,
                allowances = allow,
                deductions = deduc,
                netSalary = base + allow - deduc,
                remarks = remarks
            ) ?: SalaryModel(
                id = "",
                staffId = finalStaffId,
                staffName = finalStaffName,
                salaryDate = salaryDateISO,
                basicSalary = base,
                allowances = allow,
                deductions = deduc,
                netSalary = base + allow - deduc,
                paymentStatus = "Pending",
                remarks = remarks
            )

            viewModel.saveSalary(salary)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
