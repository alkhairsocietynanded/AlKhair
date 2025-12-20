package com.zabibtech.alkhair.ui.user.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class FeesFragment : Fragment() {

    private var _binding: FragmentFeesBinding? = null
    private val binding get() = _binding!!

    // ViewModel setup
    private val feesViewModel: FeesViewModel by viewModels()
    private lateinit var adapter: FeesAdapter

    private var user: User? = null

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

        setupInsets()
        setupFabAnimation()
        setupRecyclerView()
        setupListeners()

        // 🚀 Trigger Data Load (सिर्फ एक बार ID सेट करें)
        user?.uid?.let { feesViewModel.setStudentId(it) }

        observeViewModel()
    }

    /* ============================================================
       🔧 UI SETUP
       ============================================================ */

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddFee) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 16.dpToPx() + systemBars.bottom
            }
            insets
        }
    }

    private fun setupFabAnimation() {
        binding.nestedScrollView.setOnScrollChangeListener { _: NestedScrollView, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            if (abs(dy) > 5) {
                if (dy > 0) { // Scroll Down -> Hide FAB
                    if (binding.fabAddFee.translationX == 0f) {
                        val slideRight = binding.fabAddFee.width.toFloat() + binding.fabAddFee.marginEnd.toFloat() + 50f
                        binding.fabAddFee.animate().translationX(slideRight).setDuration(200).start()
                    }
                } else { // Scroll Up -> Show FAB
                    if (binding.fabAddFee.translationX > 0f) {
                        binding.fabAddFee.animate().translationX(0f).setDuration(200).start()
                    }
                }
            }
        }
    }

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
                showAddFeeDialog(fee)
            }
        )

        binding.recyclerViewFees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FeesFragment.adapter
        }
    }

    private fun setupListeners() {
        binding.fabAddFee.setOnClickListener {
            showAddFeeDialog()
        }
    }

    /* ============================================================
       👀 STATE OBSERVERS (Reactive Pattern)
       ============================================================ */

    private fun observeViewModel() {

        // 1. List Observer (SSOT: Room -> ViewModel -> UI)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                feesViewModel.studentFeesListState.collect { state ->

                    // Loading handling logic can be customized (Dialog vs ProgressBar)
                    if (state is UiState.Loading && adapter.itemCount == 0) {
                        DialogUtils.showLoading(childFragmentManager)
                    } else {
                        DialogUtils.hideLoading(childFragmentManager)
                    }

                    when (state) {
                        is UiState.Success -> {
                            val fees = state.data
                            adapter.submitList(fees)

                            binding.emptyView.isVisible = fees.isEmpty()
                            binding.recyclerViewFees.isVisible = fees.isNotEmpty()

                            // Dashboard ko yahi update karein
                            updateFeeDashboard(fees)
                        }

                        is UiState.Error -> {
                            DialogUtils.showAlert(requireContext(), message = state.message)
                        }

                        else -> Unit
                    }
                }
            }
        }

        // 2. Mutation Observer (Save/Delete Status)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                feesViewModel.mutationState.collect { state -> // Note: Renamed feeState to mutationState in ViewModel
                    when (state) {
                        is UiState.Loading -> DialogUtils.showLoading(childFragmentManager)

                        is UiState.Success -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            Toast.makeText(requireContext(), "Operation successful", Toast.LENGTH_SHORT).show()
                            feesViewModel.resetMutationState()

                            // ❌ NO NEED to call loadFees() here.
                            // Room will update -> Flow emits new list -> UI updates automatically.
                        }

                        is UiState.Error -> {
                            DialogUtils.hideLoading(childFragmentManager)
                            DialogUtils.showAlert(requireContext(), message = state.message)
                            feesViewModel.resetMutationState()
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    /* ============================================================
       📊 HELPER METHODS
       ============================================================ */

    private fun updateFeeDashboard(feesModels: List<FeesModel>) {
        val totalFees = feesModels.sumOf { it.baseAmount }.toInt()
        val totalPaid = feesModels.sumOf { it.paidAmount }.toInt()
        val totalDisc = feesModels.sumOf { it.discounts }.toInt()

        // Logic fix: Ensure due is not negative visually
        val rawDue = totalFees - (totalPaid + totalDisc)
        val adjustedDue = rawDue.coerceAtLeast(0)

        binding.tvTotalFeesAmount.text = "₹$totalFees"
        binding.tvTotalPaidAmount.text = "₹$totalPaid"
        binding.tvTotalDiscountAmount.text = "₹$totalDisc"
        binding.tvTotalDueAmount.text = "₹$adjustedDue"
    }

    private fun showAddFeeDialog(feesModelToEdit: FeesModel? = null) {
        val dialog = AddEditFeesDialog.newInstance(user, feesModelToEdit)
        dialog.show(childFragmentManager, "AddEditFeesDialog")
    }

    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

    private val View.marginEnd: Int
        get() {
            val lp = layoutParams as? ViewGroup.MarginLayoutParams
            return lp?.marginEnd ?: 0
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}