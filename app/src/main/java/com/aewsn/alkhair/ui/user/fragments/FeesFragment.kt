package com.aewsn.alkhair.ui.user.fragments

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
import com.aewsn.alkhair.data.models.FeesModel
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.FragmentFeesBinding
import android.content.Intent
import com.aewsn.alkhair.ui.fees.AddEditFeesDialog
import com.aewsn.alkhair.ui.fees.FeesViewModel
import com.aewsn.alkhair.ui.user.adapters.FeesAdapter
import com.aewsn.alkhair.utils.Constants
import com.aewsn.alkhair.utils.DialogUtils
import com.aewsn.alkhair.utils.UiState
import com.aewsn.alkhair.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.core.net.toUri

@AndroidEntryPoint
class FeesFragment : Fragment() {

    private var _binding: FragmentFeesBinding? = null
    private val binding get() = _binding!!

    // ViewModel setup
    private val feesViewModel: FeesViewModel by viewModels()
    private lateinit var adapter: FeesAdapter

    private var user: User? = null
    private var currentDueAmount: Double = 0.0

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
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )

            binding.fabAddFee.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Increased base margin to 50dp to ensure full visibility above nav bar
                bottomMargin = 50.dpToPx() + bars.bottom
                // Handle landscape/cutout
                rightMargin = 24.dpToPx() + bars.right
            }
            insets
        }
        // Force request to ensure listener is called
        ViewCompat.requestApplyInsets(binding.root)
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

    private var isReadOnly = true // Default to true until role is fetched

    private fun setupRecyclerView() {
        adapter = FeesAdapter(
            isReadOnly = isReadOnly, // Initial state, update later if needed
            onDeleteClick = { fee ->
                // Extra check
                if (!isReadOnly) {
                    DialogUtils.showConfirmation(
                        requireContext(),
                        title = "Delete Fee Record",
                        message = "Are you sure you want to delete this fee record?",
                        onConfirmed = { feesViewModel.deleteFee(fee.id) }
                    )
                }
            },
            onEditClick = { fee ->
                if (!isReadOnly) {
                    showAddFeeDialog(fee)
                }
            }
        )

        binding.recyclerViewFees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FeesFragment.adapter
        }

        // Initial setup - FAB hidden by default until role is confirmed
        binding.fabAddFee.isVisible = false

        // Listener uses internal check, we'll update visibility in Observer
    }

    private fun updateFabVisibility(role: String?) {
        val isLoggedInStudent = role?.equals(com.aewsn.alkhair.utils.Roles.STUDENT, ignoreCase = true) == true
        isReadOnly = isLoggedInStudent
        binding.fabAddFee.isVisible = !isLoggedInStudent

        // Re-bind adapter if needed or notify changes to update item visuals (like delete button)
        // Ideally adapter should observe this or we recreate it. 
        // For quick fix:
        // Re-bind adapter if needed or notify changes to update item visuals (like delete button)
        // Ideally adapter should observe this or we recreate it. 
        // For quick fix:
        adapter = FeesAdapter(
            isReadOnly = isReadOnly,
            onDeleteClick = { fee ->
                 if (!isReadOnly) {
                    DialogUtils.showConfirmation(
                        requireContext(),
                        title = "Delete Fee Record",
                        message = "Are you sure you want to delete this fee record?",
                        onConfirmed = { feesViewModel.deleteFee(fee.id) }
                    )
                 }
            },
            onEditClick = { fee ->
                if (!isReadOnly) {
                    showAddFeeDialog(fee)
                }
            }
        )
        binding.recyclerViewFees.adapter = adapter
        // Re-submit list if data exists
        val currentList = (adapter as? FeesAdapter)?.currentList ?: emptyList()
         if (feesViewModel.studentFeesListState.value is UiState.Success) {
             (binding.recyclerViewFees.adapter as FeesAdapter).submitList((feesViewModel.studentFeesListState.value as UiState.Success).data)
         }
    }

    private fun setupListeners() {
        binding.fabAddFee.setOnClickListener {
            showAddFeeDialog()
        }

        binding.btnPayNow.setOnClickListener {
            if (currentDueAmount > 0) {
                initiateUpiPayment(currentDueAmount.toString())
            } else {
                Toast.makeText(requireContext(), "No pending dues to pay!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /* ============================================================
       👀 STATE OBSERVERS (Reactive Pattern)
       ============================================================ */

    private fun observeViewModel() {

        // 0. Role Observer (For FAB Visibility)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                feesViewModel.currentUserRole.collect { role ->
                    updateFabVisibility(role)
                }
            }
        }

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
        val totalFees = feesModels.sumOf { it.baseAmount }
        val totalPaid = feesModels.sumOf { it.paidAmount }
        val totalDisc = feesModels.sumOf { it.discounts }

        // Logic fix: Ensure due is not negative visually
        val rawDue = totalFees - (totalPaid + totalDisc)
        val adjustedDue = rawDue.coerceAtLeast(0.0)
        currentDueAmount = adjustedDue

        // Using standard formatting for currency: ₹1,250.00
        val format = "₹%,.2f"

        binding.tvTotalFeesAmount.text = String.format(format, totalFees)
        binding.tvTotalPaidAmount.text = String.format(format, totalPaid)
        binding.tvTotalDiscountAmount.text = String.format(format, totalDisc)
        binding.tvTotalDueAmount.text = String.format(format, adjustedDue)
    }

    private fun initiateUpiPayment(amount: String) {
        val upiId = feesViewModel.upiId.value
        val upiName = feesViewModel.upiName.value

        if (upiId.isNullOrBlank() || upiName.isNullOrBlank()) {
             Toast.makeText(requireContext(), "UPI details syncing. Please try again in 5 seconds.", Toast.LENGTH_SHORT).show()
             return
        }

        val uri = "upi://pay".toUri().buildUpon()
            .appendQueryParameter("pa", upiId)  // UPI Id
            .appendQueryParameter("pn", upiName)  // UPI Name
            .appendQueryParameter("tn", "School Fees Payment") // Transaction Note
            .appendQueryParameter("am", amount) // Amount
            .appendQueryParameter("cu", "INR") // Currency
            .build()

        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = uri

        val chooser = Intent.createChooser(intent, "Pay with")

        try {
            startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "No UPI app found, please install one to continue.", Toast.LENGTH_SHORT).show()
        }
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