package com.so5km.qrstrainer.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel that provides access to the store and computed values
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppStore.getInstance()
    
    val state: StateFlow<AppState> = store.state
    
    // Computed values with proper state management
    val trainingState: StateFlow<TrainingStateData> = store.state
        .map { it.trainingState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrainingStateData()
        )
    
    val audioState: StateFlow<AudioStateData> = store.state
        .map { it.audioState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AudioStateData()
        )
    
    val settings: StateFlow<TrainingSettings> = store.state
        .map { it.settings }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrainingSettings()
        )
    
    fun dispatch(action: AppAction) {
        store.dispatch(action)
    }
}