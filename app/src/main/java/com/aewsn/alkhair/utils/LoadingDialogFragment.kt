package com.aewsn.alkhair.utils

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.aewsn.alkhair.R
import com.aewsn.alkhair.databinding.DialogLoadingBinding

class LoadingDialogFragment : DialogFragment() {

    private var message: String? = null
    private var duration: Long = 0

    private var listener: OnLoadingFinishedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnLoadingFinishedListener) {
            listener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            message = it.getString(ARG_MESSAGE)
            duration = it.getLong(ARG_DURATION)
        }
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

    override fun onResume() {
        super.onResume()
        if (duration > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                dismiss()
                listener?.onLoadingFinished()
            }, duration)
        }
    }

    interface OnLoadingFinishedListener {
        fun onLoadingFinished()
    }

    companion object {
        const val TAG = "LoadingDialog"

        private const val ARG_MESSAGE = "ARG_MESSAGE"
        private const val ARG_DURATION = "ARG_DURATION"

        fun newInstance(message: String, duration: Long = 0): LoadingDialogFragment {
            val frag = LoadingDialogFragment()
            frag.arguments = Bundle().apply {
                putString(ARG_MESSAGE, message)
                putLong(ARG_DURATION, duration)
            }
            return frag
        }
    }
}
