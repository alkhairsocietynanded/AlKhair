package com.aewsn.alkhair.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.aewsn.alkhair.R

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

    // -------------------------------
    // 🔹 Safe Loading Dialog Controls
    // -------------------------------
    private var isDialogShowing = false

    fun showLoading(
        target: Any,
        message: String = "Please wait...",
        duration: Long = 0
    ) {
        if (isDialogShowing) return // avoid multiple instances

        when (target) {
            is FragmentManager -> {
                if (target.findFragmentByTag(LoadingDialogFragment.TAG) == null) {
                    LoadingDialogFragment.newInstance(message, duration)
                        .showNow(target, LoadingDialogFragment.TAG)
                    isDialogShowing = true
                }
            }
            is Fragment -> {
                val fm = target.childFragmentManager
                if (target.isAdded && target.isResumed &&
                    fm.findFragmentByTag(LoadingDialogFragment.TAG) == null
                ) {
                    LoadingDialogFragment.newInstance(message, duration)
                        .showNow(fm, LoadingDialogFragment.TAG)
                    isDialogShowing = true
                }
            }
        }
    }

    fun hideLoading(target: Any) {
        when (target) {
            is FragmentManager -> {
                val dialog =
                    target.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
                dialog?.dismissAllowingStateLoss()
            }
            is Fragment -> {
                val fm = target.childFragmentManager
                val dialog =
                    fm.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
                dialog?.dismissAllowingStateLoss()
            }
        }
        isDialogShowing = false
    }

}
