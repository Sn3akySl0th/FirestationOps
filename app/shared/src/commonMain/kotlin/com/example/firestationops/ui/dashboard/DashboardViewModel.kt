package com.example.firestationops.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val departmentId: String,
    private val apparatusRepository: ApparatusRepository
) : ViewModel() {

    val stations: StateFlow<List<Station>> = apparatusRepository.getStations(departmentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apparatus: StateFlow<List<Apparatus>> = apparatusRepository.getApparatusByDepartment(departmentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
