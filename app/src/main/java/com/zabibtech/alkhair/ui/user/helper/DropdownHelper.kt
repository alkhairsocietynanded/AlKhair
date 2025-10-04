package com.zabibtech.alkhair.ui.user.helper

import android.R
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel

class DropdownHelper(
    private val context: Context
) {
    private var allDivisions: List<DivisionModel> = emptyList()
    private var allClasses: List<ClassModel> = emptyList()
    private var onClassSelected: ((String?) -> Unit)? = null

    /**
     * Setup division dropdown with optional pre-selected values
     */
    fun setupDivisionDropdown(
        divisionView: AutoCompleteTextView,
        classView: AutoCompleteTextView,
        divisions: List<DivisionModel>,
        classes: List<ClassModel>,
        preSelectedDivision: String? = null,
        preSelectedClass: String? = null,
        onClassSelected: ((String?) -> Unit)? = null
    ) {
        this.allDivisions = divisions
        this.allClasses = classes
        this.onClassSelected = onClassSelected

        val divisionNames = allDivisions.map { it.name }
        divisionView.setAdapter(
            ArrayAdapter(context, R.layout.simple_dropdown_item_1line, divisionNames)
        )

        // Prefill division if provided
        preSelectedDivision?.let {
            divisionView.setText(it, false)
            filterClassesByDivision(it, classView, preSelectedClass)
        }

        divisionView.setOnItemClickListener { _, _, position, _ ->
            val selectedDivision = divisionNames[position]
            classView.setText("", false)
            filterClassesByDivision(selectedDivision, classView)
        }
    }

    /**
     * Filter class dropdown based on selected division
     */
    fun filterClassesByDivision(
        divisionName: String,
        classView: AutoCompleteTextView,
        preSelectedClass: String? = null
    ) {
        val filteredClasses = allClasses.filter { it.division == divisionName }
        val classNames = filteredClasses.map { it.className }

        classView.setAdapter(
            ArrayAdapter(context, R.layout.simple_dropdown_item_1line, classNames)
        )

        // Prefill class if provided
        preSelectedClass?.let { cls ->
            if (classNames.contains(cls)) {
                classView.setText(cls, false)
                val selectedId = filteredClasses.find { it.className == cls }?.id
                onClassSelected?.invoke(selectedId)
            }
        }

        classView.setOnItemClickListener { _, _, position, _ ->
            val selectedClassId = filteredClasses[position].id
            onClassSelected?.invoke(selectedClassId)
        }
    }

    /**
     * Update division list dynamically
     */
    fun updateDivisions(divisions: List<DivisionModel>) {
        this.allDivisions = divisions
    }

    /**
     * Update class list dynamically
     */
    fun updateClasses(classes: List<ClassModel>) {
        this.allClasses = classes
    }
}