package com.zabibtech.alkhair.ui.user.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zabibtech.alkhair.R
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.FragmentFeesBinding

class FeesFragment : Fragment() {

    private var user: User? = null
    private var _binding: FragmentFeesBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_USER = "arg_user"

        fun newInstance(user: User) = FeesFragment().apply {
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
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_fees, container, false)
    }

}