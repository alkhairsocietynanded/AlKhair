package com.zabibtech.alkhair.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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

    // 🔹 Show Loading (safe for fragment/activity)
    fun showLoading(target: Any, message: String = "Please wait...") {
        when (target) {
            is FragmentManager -> {
                val existing = target.findFragmentByTag(LoadingDialogFragment.TAG)
                if (existing == null) {
                    LoadingDialogFragment.newInstance(message)
                        .show(target, LoadingDialogFragment.TAG)
                }
            }
            is Fragment -> {
                val fm = target.childFragmentManager
                if (target.isAdded && target.isResumed) {
                    val existing = fm.findFragmentByTag(LoadingDialogFragment.TAG)
                    if (existing == null) {
                        LoadingDialogFragment.newInstance(message)
                            .show(fm, LoadingDialogFragment.TAG)
                    }
                }
            }
        }
    }

    fun hideLoading(target: Any) {
        when (target) {
            is FragmentManager -> {
                val dialog = target.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
                dialog?.dismissAllowingStateLoss()
            }
            is Fragment -> {
                val fm = target.childFragmentManager
                if (target.isAdded && target.isResumed) {
                    val dialog = fm.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
                    dialog?.dismissAllowingStateLoss()
                }
            }
        }
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
