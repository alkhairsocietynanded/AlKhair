package com.zabibtech.alkhair.utils

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.databinding.DialogLoadingBinding

class LoadingDialogFragment : DialogFragment() {

    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = arguments?.getString(ARG_MESSAGE)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogLoadingBinding.inflate(layoutInflater)

        binding.tvMessage.text = message ?: "Please wait..."

        val dialog = AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    companion object {
        const val TAG = "LoadingDialog"

        private const val ARG_MESSAGE = "ARG_MESSAGE"

        fun newInstance(message: String): LoadingDialogFragment {
            val frag = LoadingDialogFragment()
            frag.arguments = Bundle().apply {
                putString(ARG_MESSAGE, message)
            }
            return frag
        }
    }
}
