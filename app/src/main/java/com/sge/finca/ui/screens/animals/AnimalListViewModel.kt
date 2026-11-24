package com.sge.finca.ui.screens.animals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sge.finca.data.ranch.RanchExitMonitor
import com.sge.finca.domain.usecase.ObserveTrackedAnimalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lista de animales: posición en vivo de los collares (la misma que el mapa) y si cada vaca está
 * dentro o fuera del rancho, según RanchExitMonitor.
 */
@HiltViewModel
class AnimalListViewModel @Inject constructor(
    private val observeTrackedAnimals: ObserveTrackedAnimalsUseCase,
    private val ranchExitMonitor: RanchExitMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimalListUiState())
    val uiState: StateFlow<AnimalListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeTrackedAnimals().collect { animals ->
                _uiState.update { it.copy(animals = animals, isLoading = false) }
            }
        }
        viewModelScope.launch {
            ranchExitMonitor.state.collect { ranch ->
                _uiState.update { it.copy(hasRanch = ranch.hasRanch, ranchExits = ranch.exits) }
            }
        }
    }
}
