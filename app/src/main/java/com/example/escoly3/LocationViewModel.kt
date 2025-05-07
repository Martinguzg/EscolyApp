package com.example.escoly3

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    private val locationManager: LocationManager,
    private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()


    // Mantén exactamente el mismo código que me enviaste
// Solo asegúrate de que generateDeviceId() use el UID correctamente:
    fun generateDeviceId(firebaseUid: String?) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                val id = firebaseUid?.takeLast(5)?.uppercase() ?: generateRandomId()
                _uiState.value = LocationUiState.IdGenerated(id)
            } catch (e: Exception) {
                _uiState.value = LocationUiState.Error("Error al generar ID")
            }
        }
    }

    fun startTracking(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                startForegroundService(deviceId)
                locationManager.startLocationUpdates(deviceId) { location ->
                    _uiState.value = LocationUiState.LocationUpdated(location)
                }
                _uiState.value = LocationUiState.TrackingActive(deviceId)
            } catch (e: Exception) {
                _uiState.value = LocationUiState.Error("Error al rastrear: ${e.message}")
            }
        }
    }

    private fun startForegroundService(deviceId: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, LocationForegroundService::class.java).apply {
                putExtra("DEVICE_ID", deviceId)
            }
        )
    }

    fun stopTracking() {
        locationManager.stopLocationUpdates()
        context.stopService(Intent(context, LocationForegroundService::class.java))
        _uiState.value = LocationUiState.Idle
    }

    fun setError(message: String) {
        _uiState.value = LocationUiState.Error(message)
    }

    private fun generateRandomId(): String {
        return List(5) { ('A'..'Z').random() }.joinToString("")
    }

    sealed class LocationUiState {
        object Idle : LocationUiState()
        object Loading : LocationUiState()
        data class IdGenerated(val id: String) : LocationUiState()
        data class TrackingActive(val id: String) : LocationUiState()
        data class LocationUpdated(val location: Location) : LocationUiState()
        data class Error(val message: String) : LocationUiState()
    }
}