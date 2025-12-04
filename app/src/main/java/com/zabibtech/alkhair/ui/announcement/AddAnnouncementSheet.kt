package com.zabibtech.alkhair.ui.announcement

import DateUtils
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.databinding.BottomSheetAddAnnouncementBinding
import com.zabibtech.alkhair.ui.dashboard.AdminDashboardActivity

class AddAnnouncementSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddAnnouncementBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddAnnouncementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveAnnouncement.setOnClickListener {
            val title = binding.etAnnouncementTitle.text.toString().trim()
            val content = binding.etAnnouncementContent.text.toString().trim()

            if (title.isNotEmpty() && content.isNotEmpty()) {
                val newAnnouncement = Announcement(
                    title = title,
                    content = content,
                    timeStamp = DateUtils.getCurrentTimestamp()
                )
                // ✅ Activity ko cast karein aur uska public function call karein
                (activity as? AdminDashboardActivity)?.announcementViewModel?.createAnnouncement(
                    newAnnouncement
                )
                dismiss() // Data bhej kar bottom sheet ko band kar dein
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddAnnouncementSheet"
    }
}
