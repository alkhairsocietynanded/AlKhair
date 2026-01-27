package com.zabibtech.alkhair.ui.classmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.databinding.BottomSheetAddClassBinding
import com.zabibtech.alkhair.utils.DialogUtils

class AddClassSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddClassBinding? = null
    private val binding get() = _binding!!

    // Use the Activity's ViewModel. This is the modern, recommended way.
    private val viewModel: ClassManagerViewModel by activityViewModels()

    private var divisions: List<String> = emptyList()
    private var existingDivision: String? = null
    private var existingClassName: String? = null
    private var existingClassId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddClassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            divisions = it.getStringArrayList("divisions")?.toList() ?: emptyList()
            existingDivision = it.getString("existingDivision")
            existingClassName = it.getString("existingClassName")
            existingClassId = it.getString("existingClassId")
        }

        setupViews()

        binding.btnSave.setOnClickListener {
            handleSave()
        }
    }

    private fun handleSave() {
        val division = binding.etDivision.text.toString().trim()
        val className = binding.etClassName.text.toString().trim()

        if (division.isEmpty() || className.isEmpty()) {
            Snackbar.make(binding.root, "Please fill all fields", Snackbar.LENGTH_SHORT).show()
            return
        }

        val divisionExists = divisions.any { it.equals(division, ignoreCase = true) }

        if (divisionExists) {
            saveClass(division, className)
        } else {
            DialogUtils.showConfirmation(
                context = requireContext(),
                title = "Create New Division",
                message = "Division '$division' does not exist. Do you want to create it?",
                positiveText = "Create",
                onConfirmed = {
                      android.util.Log.d("AddClassSheet", "User confirmed creation of division: $division")
                      // ⚠️ IMPORTANT: Do NOT call viewModel.addDivision() explicitly here.
                      // The RepoManager.addClass() function now automatically handles creating 
                      // the division if it's missing, inside the same flow. 
                      // Calling it twice here causes race conditions and UI bugs.
                      saveClass(division, className)
                }
            )
        }
    }
    private fun saveClass(division: String, className: String) {
        android.util.Log.d("AddClassSheet", "Saving class: $className in $division")
        if (existingClassId != null) {
            // Update existing class
            val updatedClass = ClassModel(
                id = existingClassId!!,
                divisionName = division,
                className = className
            )
            viewModel.updateClass(updatedClass)
        } else {
            // Add new class
            viewModel.addClass(className, division)
        }
        dismiss()
    }

    private fun setupViews() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            divisions
        )
        binding.etDivision.setAdapter(adapter)

        binding.etDivision.setOnClickListener {
            binding.etDivision.showDropDown()
        }

        existingDivision?.let {
            binding.etDivision.setText(it, false)
        }
        existingClassName?.let {
            binding.etClassName.setText(it)
        }

        binding.tvTitle.text = if (existingClassId == null) "Add Class" else "Edit Class"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddClassSheet"

        fun newInstance(
            divisions: List<String>,
            existingDivision: String? = null,
            existingClassName: String? = null,
            existingClassId: String? = null
        ): AddClassSheet {
            val fragment = AddClassSheet()
            val args = Bundle().apply {
                putStringArrayList("divisions", ArrayList(divisions))
                putString("existingDivision", existingDivision)
                putString("existingClassName", existingClassName)
                putString("existingClassId", existingClassId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
