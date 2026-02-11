package com.aewsn.alkhair.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.aewsn.alkhair.databinding.BottomSheetAppInfoBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AppInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAppInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAppInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTermsConditions.setOnClickListener {
            openUrl("https://example.com/terms") // Replace with actual URL
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            openUrl("https://example.com/privacy") // Replace with actual URL
        }

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            binding.tvAppVer.text = "Version $versionName"
        } catch (e: Exception) {
            binding.tvAppVer.text = "Version 1.0.0"
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AppInfoBottomSheet"
    }
}
