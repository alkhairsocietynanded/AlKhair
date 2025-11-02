package com.zabibtech.alkhair.ui.user.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zabibtech.alkhair.utils.getParcelableCompat
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.FragmentProfileBinding
import com.zabibtech.alkhair.utils.DialogUtils

class ProfileFragment : Fragment() {

    private var user: User? = null
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_USER = "arg_user"

        fun newInstance(user: User) = ProfileFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_USER, user)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        user = arguments?.getParcelableCompat(ARG_USER, User::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate UI with user data
        user?.let {
            binding.tvName.text = it.name
            binding.tvEmail.text = it.email
            binding.tvPhone.text = it.phone
            binding.tvRole.text = it.role
            binding.tvClass.text = it.className
            binding.tvShift.text = it.shift
            binding.tvAddress.text = it.address
            binding.tvDivision.text = it.divisionName
            binding.chipClass.text = it.className
            binding.chipDivision.text = it.divisionName
            binding.tvJoinedDate.text = it.dateOfJoining
//            binding.tvJoinedDate.text = it.joinedDate
            // Load profile image using Glide/Picasso if you have URL
        }
    }

    override fun onPause() {
        super.onPause()
        DialogUtils.hideLoading(childFragmentManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hide any active loading dialog to prevent window leaks when navigating away
        DialogUtils.hideLoading(parentFragmentManager)
        _binding = null
    }
}
