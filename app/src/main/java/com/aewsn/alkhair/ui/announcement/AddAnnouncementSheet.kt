package com.aewsn.alkhair.ui.announcement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aewsn.alkhair.databinding.BottomSheetAddAnnouncementBinding

class AddAnnouncementSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddAnnouncementBinding? = null
    private val binding get() = _binding!!

    // Using requireActivity() to access the shared ViewModel instance
    private val announcementViewModel: AnnouncementViewModel by viewModels({ requireActivity() })

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

                // ✅ UPDATED CALL:
                // Ab hum yahan model create nahi kar rahe, seedha values pass kar rahe hain.
                // Default target "ALL" rahega.
                // Agar future me class dropdown lagate hain, to teesra parameter yahan pass karein.
                announcementViewModel.createAnnouncement(title, content)

                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
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