package com.zabibtech.alkhair.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.zabibtech.alkhair.R

object DialogUtils {

    fun showAlert(
        context: Context,
        title: String = "Message",
        message: String,
        positiveText: String = "OK",
        onPositiveClick: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                dialog.dismiss()
                onPositiveClick?.invoke()
            }
            .show()
    }

    // ✅ Confirmation Dialog (OK + Cancel)
    fun showConfirmation(
        context: Context,
        title: String = "Confirm",
        message: String,
        positiveText: String = "OK",
        negativeText: String = "Cancel",
        onConfirmed: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { dialog, _ ->
                dialog.dismiss()
                onConfirmed?.invoke()
            }
            .setNegativeButton(negativeText) { dialog, _ ->
                dialog.dismiss()
                onCancelled?.invoke()
            }
            .show()
    }

    // 🔹 Show Loading with custom message
    fun showLoading(manager: FragmentManager, message: String = "Please wait...") {
        val existing = manager.findFragmentByTag(LoadingDialogFragment.TAG)
        if (existing == null) {
            LoadingDialogFragment.newInstance(message)
                .show(manager, LoadingDialogFragment.TAG)
        }
    }

    // 🔹 Hide Loading
    fun hideLoading(manager: FragmentManager) {
        val dialog = manager.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
        dialog?.dismissAllowingStateLoss()
    }

    fun showAddClassDialog(
        context: Context,
        divisions: List<String>,
        existingDivision: String? = null,
        existingClassName: String? = null,
        onSave: (division: String, className: String) -> Unit
    ) {
        val inflater = android.view.LayoutInflater.from(context)
        val binding = com.zabibtech.alkhair.databinding.DialogAddClassBinding.inflate(inflater)

        // Spinner ke jagah AutoCompleteTextView me data set karo
        val adapter = android.widget.ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            divisions
        )
        binding.etDivision.setAdapter(adapter)

        // Dropdown ko click par kholna
        binding.etDivision.setOnClickListener {
            binding.etDivision.showDropDown()
        }

        // ✅ Agar edit ke liye open hua hai to purane values set karo
        existingDivision?.let {
            binding.etDivision.setText(it, false)  // false = dropdown trigger na ho
        }
        existingClassName?.let {
            binding.etClassName.setText(it)
        }

        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(if (existingDivision == null) "Add Class" else "Edit Class")
            .setView(binding.root)
            .setPositiveButton("Save") { dialog, _ ->
                val division = binding.etDivision.text.toString().trim()
                val className = binding.etClassName.text.toString().trim()
                if (division.isNotEmpty() && className.isNotEmpty()) {
                    onSave(division, className)
                } else {
                    showAlert(context, message = "Please enter both Division and Class Name")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

}
