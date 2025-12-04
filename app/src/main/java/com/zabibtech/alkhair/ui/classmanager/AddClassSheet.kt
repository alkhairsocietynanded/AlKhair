package com.zabibtech.alkhair.ui.classmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.databinding.BottomSheetAddClassBinding

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
            val division = binding.etDivision.text.toString().trim()
            val className = binding.etClassName.text.toString().trim()

            if (division.isNotEmpty() && className.isNotEmpty()) {
                if (existingClassId != null) {
                    // Update existing class by calling the ViewModel directly
                    val updatedClass = ClassModel(
                        id = existingClassId!!,
                        division = division,
                        className = className
                    )
                    viewModel.updateClass(updatedClass)
                } else {
                    // Add new class by calling the ViewModel directly
                    viewModel.addClass(className, division)
                }
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT)
                    .show()
            }
        }
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