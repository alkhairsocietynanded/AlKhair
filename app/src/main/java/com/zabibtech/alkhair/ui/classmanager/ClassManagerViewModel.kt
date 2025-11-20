package com.zabibtech.alkhair.ui.classmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.repository.ClassManagerRepository
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.utils.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClassManagerViewModel @Inject constructor(
    private val repository: ClassManagerRepository,
    private val classDivisionStore: ClassDivisionStore
) : ViewModel() {

    private var currentDivisionFilterForRefresh: String? = null

    private val _divisions = MutableStateFlow<UiState<List<DivisionModel>>>(UiState.Loading)
    val divisions: StateFlow<UiState<List<DivisionModel>>> = _divisions

    private val _classes = MutableStateFlow<UiState<List<ClassModel>>>(UiState.Loading)
    val classes: StateFlow<UiState<List<ClassModel>>> = _classes

    // This is a helper function to ensure a division exists
    private suspend fun ensureDivisionExists(divisionName: String) {
        // Get the current list of divisions from the ViewModel's state
        val currentDivisions = (_divisions.value as? UiState.Success)?.data ?: emptyList()

        // Check if the divisionName already exists (case-insensitive)
        if (currentDivisions.none { it.name.equals(divisionName, ignoreCase = true) }) {
            // If it doesn't exist, add it
            repository.addDivision(DivisionModel(id = "", name = divisionName))
            // After adding, reload divisions to update the ViewModel's state
            loadDivisions()
        }
    }

    // -------------------------------
    // Division Filter + Auto Refresh
    // -------------------------------
    fun setCurrentDivisionFilter(divisionName: String?) {
        currentDivisionFilterForRefresh = divisionName
        refreshClassesBasedOnFilter()
    }

    private fun refreshClassesBasedOnFilter() {
        if (currentDivisionFilterForRefresh == null) {
            loadClasses()
        } else {
            loadClassesByDivision(currentDivisionFilterForRefresh!!)
        }
    }

    // -------------------------------
    // Division CRUD
    // -------------------------------
    fun loadDivisions() {
        viewModelScope.launch {
            _divisions.value = UiState.Loading
            try {
                val list = classDivisionStore.getOrFetchDivisionList()
                _divisions.value = UiState.Success(list)
            } catch (e: Exception) {
                _divisions.value = UiState.Error(e.message ?: "Failed to load divisions")
            }
        }
    }

    fun addDivision(name: String) {
        viewModelScope.launch {
            try {
                repository.addDivision(DivisionModel(id = "", name = name))
                loadDivisions()
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _divisions.value = UiState.Error(e.message ?: "Failed to add division")
            }
        }
    }

    fun updateDivision(division: DivisionModel) {
        viewModelScope.launch {
            try {
                repository.updateDivision(division)
                loadDivisions()
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _divisions.value = UiState.Error(e.message ?: "Failed to update division")
            }
        }
    }

    fun deleteDivision(divisionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteDivision(divisionId)
                loadDivisions()
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _divisions.value = UiState.Error(e.message ?: "Failed to delete division")
            }
        }
    }

    // -------------------------------
    // Class CRUD
    // -------------------------------
    fun loadClasses() {
        viewModelScope.launch {
            _classes.value = UiState.Loading
            try {
                val list = classDivisionStore.getOrFetchClassList()
                _classes.value = UiState.Success(list)
            } catch (e: Exception) {
                _classes.value = UiState.Error(e.message ?: "Failed to load classes")
            }
        }
    }

    fun loadClassesByDivision(divisionName: String) {
        viewModelScope.launch {
            _classes.value = UiState.Loading
            try {
                // Step 1: Load all classes from cache
                val allClasses = classDivisionStore.getOrFetchClassList()
                val filtered = allClasses.filter { it.division == divisionName }
                _classes.value = UiState.Success(filtered)

                // Step 2: Background refresh from repository
                launch(Dispatchers.IO) {
                    try {
                        val freshList = repository.getClassesByDivision(divisionName)
                        // Merge freshList into cache
                        val updatedCache = allClasses.filter { it.division != divisionName } + freshList
                        classDivisionStore.saveClassList(updatedCache)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            } catch (e: Exception) {
                _classes.value = UiState.Error(e.message ?: "Failed to load classes by division")
            }
        }
    }

    fun addClass(divisionName: String, className: String) {
        viewModelScope.launch {
            try {
                // First, ensure the division exists in the database
                ensureDivisionExists(divisionName)

                // Then, add the class
                // NOTE: repository.addClassWithDivisionCheck might have redundant division checking
                // if ensureDivisionExists is called prior. Consider simplifying repository.addClass.
                repository.addClassWithDivisionCheck(
                    ClassModel(id = "", division = divisionName, className = className)
                )
                // Reload divisions and refresh classes to reflect changes
                loadDivisions()
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _classes.value = UiState.Error(e.message ?: "Failed to add class")
            }
        }
    }

    fun updateClass(classModel: ClassModel) {
        viewModelScope.launch {
            try {
                // First, ensure the division for the updated class exists
                ensureDivisionExists(classModel.division)

                // Then, update the class
                repository.updateClass(classModel)
                // Reload divisions (in case a new one was added) and refresh classes
                loadDivisions() // Important: reload divisions if ensureDivisionExists added a new one
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _classes.value = UiState.Error(e.message ?: "Failed to update class")
            }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            try {
                repository.deleteClass(classId)
                refreshClassesBasedOnFilter()
            } catch (e: Exception) {
                _classes.value = UiState.Error(e.message ?: "Failed to delete class")
            }
        }
    }
}
