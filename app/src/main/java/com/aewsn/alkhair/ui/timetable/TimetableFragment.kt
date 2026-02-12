package com.aewsn.alkhair.ui.timetable

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aewsn.alkhair.R
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.Subject
import com.aewsn.alkhair.data.models.Timetable
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.databinding.FragmentTimetableBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class TimetableFragment : Fragment() {

    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TimetableViewModel by viewModels()
    private lateinit var timetableAdapter: TimetableAdapter

    private var selectedDay = "Monday"
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    
    // Data for dialogs
    private var classList: List<ClassModel> = emptyList()
    private var subjectList: List<Subject> = emptyList()
    private var teacherList: List<User> = emptyList()
    private var currentTimetable: List<Timetable> = emptyList()

    private var isAdmin = false // To be set via arguments or user role check
    private var selectedClassId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check argument for role
        isAdmin = arguments?.getBoolean("IS_ADMIN", false) ?: false
        
        setupUI()
        setupObservers()
        
        if (!isAdmin) {
            viewModel.initializeForCurrentUser()
        }
    }

    private fun setupUI() {
        // Admin Controls
        binding.layoutAdminControls.isVisible = isAdmin
        binding.fabAddSlot.isVisible = isAdmin

        if (isAdmin) {
            setupClassSelector()
            binding.fabAddSlot.setOnClickListener { showAddSlotDialog() }
        }

        // Tabs
        days.forEach { day ->
            binding.tabLayoutDays.addTab(binding.tabLayoutDays.newTab().setText(day))
        }

        binding.tabLayoutDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedDay = tab?.text.toString()
                filterTimetable()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Recycler
        timetableAdapter = TimetableAdapter(isAdmin) { item ->
            showDeleteConfirmation(item)
        }
        binding.rvTimetable.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timetableAdapter
        }
    }

    private fun setupClassSelector() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        (binding.actvClass as? AutoCompleteTextView)?.setAdapter(adapter)
        
        (binding.actvClass as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            if (classList.isNotEmpty()) {
                val selectedClass = classList[position]
                selectedClassId = selectedClass.id
                viewModel.selectClass(selectedClass.id)
            }
        }
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Classes Select (Admin)
                launch {
                    viewModel.classes.collectLatest { classes ->
                        classList = classes
                        val classNames = classes.map { it.className }
                        (binding.actvClass as? AutoCompleteTextView)?.setAdapter(
                             ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, classNames)
                        )
                    }
                }

                // Subjects & Teachers (For Dialog)
                launch { viewModel.subjects.collectLatest { subjectList = it } }
                launch { viewModel.teachers.collectLatest { teacherList = it } }

                // Timetable Data
                launch {
                    viewModel.timetableEntries.collectLatest { entries ->
                        currentTimetable = entries
                        filterTimetable()
                    }
                }

                // UI State
                launch {
                    viewModel.uiState.collectLatest { state ->
                        binding.progressBar.isVisible = state is TimetableUiState.Loading
                        when(state) {
                            is TimetableUiState.Success -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetUiState()
                            }
                            is TimetableUiState.Error -> {
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetUiState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun filterTimetable() {
        val filtered = currentTimetable.filter { it.dayOfWeek == selectedDay }
            .sortedBy { it.startTime } // Sort by time
        
        timetableAdapter.submitList(filtered)
        binding.tvEmpty.isVisible = filtered.isEmpty()
    }

    private fun showAddSlotDialog() {
        if (selectedClassId == null) {
            Toast.makeText(requireContext(), "Please select a class first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_timetable_slot, null)
        val actvDay = dialogView.findViewById<AutoCompleteTextView>(R.id.actvDay)
        val actvSubject = dialogView.findViewById<AutoCompleteTextView>(R.id.actvSubject)
        val actvTeacher = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTeacher)
        val etStartTime = dialogView.findViewById<TextInputEditText>(R.id.etStartTime)
        val etEndTime = dialogView.findViewById<TextInputEditText>(R.id.etEndTime)
        val etRoom = dialogView.findViewById<TextInputEditText>(R.id.etRoomNo)

        // Setup Day Spinner
        actvDay.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days))
        actvDay.setText(selectedDay, false) // Default to current tab

        // Setup Subjects
        val subjectNames = subjectList.map { "${it.name} (${it.code})" }
        actvSubject.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subjectNames))

        // Setup Teachers
        val teacherNames = teacherList.map { it.name }
        actvTeacher.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teacherNames))

        // Time Pickers
        etStartTime.setOnClickListener { showTimePicker(etStartTime) }
        etEndTime.setOnClickListener { showTimePicker(etEndTime) }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val day = actvDay.text.toString()
                val subjectNameFull = actvSubject.text.toString()
                val teacherNameFull = actvTeacher.text.toString()
                val start = etStartTime.text.toString()
                val end = etEndTime.text.toString()
                val room = etRoom.text.toString()

                if (day.isEmpty() || subjectNameFull.isEmpty() || start.isEmpty() || end.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill required fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Find IDs
                val subjectId = subjectList.find { "${it.name} (${it.code})" == subjectNameFull }?.id
                val teacherId = teacherList.find { it.name == teacherNameFull }?.uid
                
                if (subjectId != null) {
                    val newEntry = Timetable(
                        id = "",
                        classId = selectedClassId!!,
                        subjectId = subjectId,
                        teacherId = teacherId ?: "", // Optional?
                        dayOfWeek = day,
                        periodIndex = 0, // Auto-increment or calc? For now 0, use time for sort
                        startTime = start,
                        endTime = end,
                        roomNo = room,
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                    viewModel.addTimetableEntry(newEntry)
                } else {
                    Toast.makeText(requireContext(), "Invalid Subject selected", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showTimePicker(view: TextView) {
        val cal = Calendar.getInstance()
        val listener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            view.text = time
        }
        TimePickerDialog(requireContext(), listener, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun showDeleteConfirmation(item: Timetable) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Slot")
            .setMessage("Are you sure you want to delete ${item.subjectName} at ${item.startTime}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTimetableEntry(item.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
