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
    private val locationManager: LocationManager = LocationManagerSingleton.get(),
    private val context: Context,
    private val idManager: IdManager
) : ViewModel() {

    // Estado inicial corregido
    private var currentDeviceId: String? = idManager.getId() // Carga el ID al iniciar

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()


    // Función mejorada para generar ID
    fun generateDeviceId(firebaseUid: String?) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                val id = firebaseUid?.let { uid ->
                    if (uid.length >= 5) uid.takeLast(5).uppercase()
                    else uid.uppercase().padStart(5, '0')
                } ?: generateRandomId()

                currentDeviceId = id
                idManager.saveId(id) // Guarda usando IdManager
                _uiState.value = LocationUiState.IdGenerated(id)
            } catch (e: Exception) {
                _uiState.value = LocationUiState.Error("Error generando ID")
            }
        }
    }

    fun loadSavedId() {
        viewModelScope.launch {
            idManager.getId()?.let { savedId ->
                currentDeviceId = savedId
                // Solo actualiza estado si estaba en Idle
                if (_uiState.value is LocationUiState.Idle) {
                    _uiState.value = LocationUiState.IdGenerated(savedId)
                }
            }
        }
    }


    // Función mejorada para iniciar rastreo
    fun startTrackingWithValidId() {
        viewModelScope.launch {
            try {
                when (val currentState = _uiState.value) {
                    is LocationUiState.IdGenerated -> {
                        if (checkLocationPermissions()) {
                            startTracking(currentState.id)
                        } else {
                            _uiState.value = LocationUiState.IdGenerated(currentState.id) // Mantenemos el estado actual
                            // Aquí deberías solicitar los permisos
                        }
                    }
                    else -> {
                        _uiState.value = LocationUiState.Idle // En lugar de mostrar error
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar rastreo", e)
                _uiState.value = LocationUiState.Idle // Volvemos a estado Idle
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

                _uiState.value = LocationUiState.TrackingActive(deviceId)
                Log.i(TAG, "Rastreo iniciado exitosamente para $deviceId")

            } catch (e: SecurityException) {
                Log.e(TAG, "Permisos insuficientes", e)
                _uiState.value = LocationUiState.IdGenerated(deviceId) // Mantenemos el ID generado
            } catch (e: Exception) {
                Log.e(TAG, "Error en rastreo", e)
                _uiState.value = LocationUiState.IdGenerated(deviceId) // Mantenemos el ID generado
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
                Log.e(TAG, "Error al detener rastreo", e)
                _uiState.value = LocationUiState.Idle // Volvemos a estado Idle
            }
        }
    }

    // Resto del código permanece igual...
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

    fun setErrorState(message: String) {
        viewModelScope.launch {
            // Solo mostramos error si no estamos ya en estado Idle
            if (_uiState.value !is LocationUiState.Idle) {
                _uiState.value = LocationUiState.Error(message)
            }
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