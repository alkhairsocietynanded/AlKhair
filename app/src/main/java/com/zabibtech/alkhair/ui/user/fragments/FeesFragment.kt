package com.zabibtech.alkhair.ui.user.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels // Import viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.databinding.FragmentFeesBinding
import com.zabibtech.alkhair.ui.fees.AddEditFeesDialog
import com.zabibtech.alkhair.ui.fees.FeesViewModel
import com.zabibtech.alkhair.ui.user.adapters.FeesAdapter
import com.zabibtech.alkhair.utils.DialogUtils
import com.zabibtech.alkhair.utils.UiState
import com.zabibtech.alkhair.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class FeesFragment : Fragment() {

    private var user: User? = null
    private var _binding: FragmentFeesBinding? = null
    private val binding get() = _binding!!

    // ViewModel ko fragment scope mein define karein aur private banayein
    private val feesViewModel: FeesViewModel by viewModels() // activityViewModels se viewModels mein badal diya
    private lateinit var adapter: FeesAdapter

    private var studentId: String? = null

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

        user = arguments?.getParcelableCompat(ARG_USER, User::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Insets Handle karo (FAB ko Navigation Bar ke upar rakhne ke liye)
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddFee) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 16.dpToPx() + systemBars.bottom
            }
            insets
        }

        // 2. MD3 STYLE: SLIDE RIGHT ON SCROLL (Instant Reaction)
        binding.nestedScrollView.setOnScrollChangeListener { _: NestedScrollView, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY

            // Threshold: 5 pixels se zyada movement hote hi action lo (Responsive)
            if (abs(dy) > 5) {
                if (dy > 0) {
                    // SCROLL DOWN -> Slide Right (Hide)
                    // Check karein agar already hidden nahi hai
                    if (binding.fabAddFee.translationX == 0f) {
                        // FAB ki width + margin calculate k करके usko right bhej do
                        val slideRightDistance =
                            binding.fabAddFee.width.toFloat() + binding.fabAddFee.marginEnd.toFloat() + 50f // +50 extra safety

                        binding.fabAddFee.animate()
                            .translationX(slideRightDistance) // Right side movement
                            .setDuration(200) // Fast duration (Snappy feel)
                            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                            .start()
                    }
                } else {
                    // SCROLL UP -> Slide Left/Back (Show)
                    // Check karein agar chupa hua hai
                    if (binding.fabAddFee.translationX > 0f) {
                        binding.fabAddFee.animate()
                            .translationX(0f) // Wapas 0 position par
                            .setDuration(200)
                            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                            .start()
                    }
                }
            }
        }

        setupRecyclerView()
        setupObservers()

        binding.fabAddFee.setOnClickListener {
            showAddFeeDialog()
        }

        studentId = user?.uid
        studentId?.let {
            feesViewModel.loadFeesByStudent(it)
        }
    } // Moved this here to avoid nullability issues due to early return for 'Invalid student ID'

    private fun setupRecyclerView() {
        adapter = FeesAdapter(
            onDeleteClick = { fee ->
                DialogUtils.showConfirmation(
                    requireContext(),
                    title = "Delete Fee Record",
                    message = "Are you sure you want to delete this fee record?",
                    onConfirmed = { feesViewModel.deleteFee(fee.id) }
                )
            },
            onEditClick = { fee ->
                showAddFeeDialog(fee) // open dialog with pre-filled data
            }
        )

        binding.recyclerViewFees.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFees.adapter = adapter
    }

    private fun setupObservers() {
        // 🔹 Collect Fees List
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                feesViewModel.feesModelListState.collectLatest { state ->
                    when (state) {
                        is UiState.Idle -> Unit
                        is UiState.Loading -> DialogUtils.showLoading(childFragmentManager)
                        is UiState.Success -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            val fees = state.data
                            adapter.submitList(fees)
                            binding.emptyView.visibility =
                                if (fees.isEmpty()) View.VISIBLE else View.GONE
                            updateFeeDashboard(fees)
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            DialogUtils.showAlert(requireContext(), message = state.message)
                        }
                    }
                }
            }
        }

        // 🔹 Collect Save/Delete States
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                feesViewModel.feeState.collectLatest { state ->
                    when (state) {
                        is UiState.Idle -> Unit
                        is UiState.Loading -> DialogUtils.showLoading(childFragmentManager)
                        is UiState.Success -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            Toast.makeText(
                                requireContext(),
                                "Operation completed successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            // ✅ Reload fees list after successful save/delete
                            studentId?.let { feesViewModel.loadFeesByStudent(it) }

                            // ✅ Reset mutation state
                            feesViewModel.resetFeeState()
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            DialogUtils.showAlert(requireContext(), message = state.message)
                            // ✅ Reset mutation state even on error
                            feesViewModel.resetFeeState()
                        }
                    }
                }
            }
        }
    }

    private fun updateFeeDashboard(feesModels: List<FeesModel>) {
        val totalFees = feesModels.sumOf { it.baseAmount }.toInt()
        val totalPaid = feesModels.sumOf { it.paidAmount }.toInt()
        val totalDisc = feesModels.sumOf { it.discounts }.toInt()
        val totalDue = totalFees - (totalPaid + totalDisc)

        // Prevent negative due (in case of overpayment)
        val adjustedDue = if (totalDue < 0) 0.0 else totalDue

        binding.tvTotalFeesAmount.text = "₹$totalFees"
        binding.tvTotalPaidAmount.text = "₹$totalPaid"
        binding.tvTotalDiscountAmount.text = "₹$totalDisc"
        binding.tvTotalDueAmount.text = "₹$adjustedDue"
    }

    private fun showAddFeeDialog(feesModelToEdit: FeesModel? = null) {
        val dialog = AddEditFeesDialog.newInstance(user, feesModelToEdit)
        dialog.show(childFragmentManager, "AddEditFeesDialog")
    }

    // Helper function to convert dp to px (agar aapke paas utils mein nahi hai)
    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

    // Helper Extension for MarginEnd
    private val View.marginEnd: Int
        get() {
            val lp = layoutParams as? ViewGroup.MarginLayoutParams
            return lp?.marginEnd ?: 0
        }

    override fun onPause() {
        super.onPause()
        DialogUtils.hideLoading(childFragmentManager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
