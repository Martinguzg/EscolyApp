package com.example.escoly3

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationViewModel(
    private val locationManager: LocationManager,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var currentDeviceId: String? = null

    fun generateDeviceId(firebaseUid: String?) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                val id = firebaseUid?.let { uid ->
                    if (uid.length >= 5) uid.takeLast(5).uppercase()
                    else uid.uppercase().padStart(5, '0')
                } ?: generateRandomId()

                currentDeviceId = id
                _uiState.value = LocationUiState.IdGenerated(id)
                Log.d(TAG, "ID generado: $id")
            } catch (e: Exception) {
                handleError("Error al generar ID", e)
            }
        }
    }

    fun startTrackingWithValidId() {
        viewModelScope.launch {
            try {
                when (val currentState = _uiState.value) {
                    is LocationUiState.IdGenerated -> {
                        if (checkLocationPermissions()) {
                            startTracking(currentState.id)
                        } else {
                            _uiState.value = LocationUiState.Error("Permisos de ubicación requeridos")
                        }
                    }
                    else -> {
                        _uiState.value = LocationUiState.Error("Genere un ID primero")
                    }
                }
            } catch (e: Exception) {
                handleError("Error al iniciar rastreo", e)
            }
        }
    }

    private fun startTracking(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading

            try {
                if (!checkLocationPermissions()) {
                    throw SecurityException("Permisos revocados")
                }

                startForegroundService(deviceId)

                locationManager.startLocationUpdates(deviceId) { location ->
                    viewModelScope.launch(Dispatchers.Main) {  // Solución corregida para withContext
                        _uiState.value = LocationUiState.LocationUpdated(location)
                    }
                }

                _uiState.value = LocationUiState.TrackingActive(deviceId)
                Log.i(TAG, "Rastreo iniciado para $deviceId")

            } catch (e: SecurityException) {
                handleError("Permisos insuficientes", e)
                stopTracking()
            } catch (e: Exception) {
                handleError("Error en rastreo", e)
                stopTracking()
            }
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            try {
                locationManager.stopLocationUpdates()
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LocationForegroundService::class.java).apply {
                        action = "STOP_TRACKING_ACTION"
                    }
                )
                _uiState.value = LocationUiState.Idle
                Log.d(TAG, "Rastreo detenido correctamente")
            } catch (e: Exception) {
                handleError("Error al detener rastreo", e)
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundService(deviceId: String) {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationForegroundService::class.java).apply {
                    putExtra("DEVICE_ID", deviceId)
                    action = "START_TRACKING_ACTION"
                }
            )
            Log.d(TAG, "Servicio foreground iniciado para $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar servicio foreground", e)
            throw e
        }
    }

    private fun generateRandomId(): String {
        val chars = ('A'..'Z') + ('0'..'9') - listOf('I', 'O', '0', '1')
        return (1..5).joinToString("") { chars.random().toString() }
    }

    private fun handleError(message: String, exception: Exception) {
        Log.e(TAG, "$message: ${exception.message}", exception)
        _uiState.value = LocationUiState.Error("$message: ${exception.message ?: "Error desconocido"}")
    }

    fun setErrorState(message: String) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Error(message)
        }
    }

    sealed class LocationUiState {
        object Idle : LocationUiState()
        object Loading : LocationUiState()
        data class IdGenerated(val id: String) : LocationUiState()
        data class TrackingActive(val id: String) : LocationUiState()
        data class LocationUpdated(val location: Location) : LocationUiState()
        data class Error(val message: String) : LocationUiState()
    }

    companion object {
        private const val TAG = "LocationViewModel"
    }
}