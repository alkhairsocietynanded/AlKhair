package com.aewsn.alkhair.ui.student.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aewsn.alkhair.databinding.FragmentStudentProfileBinding
import com.aewsn.alkhair.ui.student.StudentViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudentProfileFragment : Fragment() {

    private var _binding: FragmentStudentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StudentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
/*

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentUser.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            val user = state.data
                            binding.tvProfileName.text = user.name
                            binding.tvProfileRole.text = user.role.replaceFirstChar { it.uppercase() }
                            
                            // Bind Details using include binding helper or manual finding
                            bindDetailRow(binding.rowClass, "Class", "${user.className} - ${user.divisionName}")
                            bindDetailRow(binding.rowMobile, "Mobile", user.mobile)
                            bindDetailRow(binding.rowEmail, "Email", user.email.ifEmpty { "N/A" })
                        }
                        is UiState.Loading -> {
                            binding.tvProfileName.text = "Loading..."
                        }
                        is UiState.Error -> {
                            binding.tvProfileName.text = "Error"
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    private fun bindDetailRow(rowView: View, label: String, value: String) {
        val rowBinding = ItemProfileDetailRowBinding.bind(rowView)
        rowBinding.tvLabel.text = label
        rowBinding.tvValue.text = value
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
*/

}
