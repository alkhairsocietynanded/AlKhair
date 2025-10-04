package com.zabibtech.alkhair.ui.user.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.FragmentProfileBinding

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

        @Suppress("DEPRECATION")
        user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_USER, User::class.java)
        } else {
            arguments?.getParcelable(ARG_USER)
        }
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
            // Load profile image using Glide/Picasso if you have URL
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
