@file:Suppress("DEPRECATION") // Keep if necessary for other parts not shown

package com.example.escoly3

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase // Recommended for newer Firebase SDK
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Data class for SafeZone (from your Version 1)
data class SafeZone(val latitude: Double, val longitude: Double, val radius: Float)

class LocationViewModel(
    private val locationManager: LocationManager, // Injected, used by ForegroundService
    private val context: Context, // Application context is generally safer for ViewModels
    private val idManager: IdManager
) : ViewModel() {

    private var currentDeviceId: String? = idManager.getId()

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var safeZones: List<SafeZone> = emptyList()
    private lateinit var fusedLocationClientInternal: FusedLocationProviderClient // For ViewModel's own use
    private var internalLocationCallback: LocationCallback? = null // To store the callback for removal

    init {
        // Use applicationContext to avoid leaks if 'context' is an Activity context
        fusedLocationClientInternal = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        loadSafeZones()
        // Start internal updates for safe zone checking when ViewModel is created
        startInternalLocationUpdates()
        loadSavedId() // Load saved ID during initialization
    }

    fun generateDeviceId(firebaseUid: String?) {
        viewModelScope.launch {
            _uiState.value = LocationUiState.Loading
            try {
                val id = firebaseUid?.let { uid ->
                    if (uid.length >= 5) uid.takeLast(5).uppercase()
                    else uid.uppercase().padStart(5, '0')
                } ?: generateRandomId()

                currentDeviceId = id
                idManager.saveId(id)
                _uiState.value = LocationUiState.IdGenerated(id)
            } catch (e: Exception) {
                _uiState.value = LocationUiState.Error("Error generando ID")
                Log.e(TAG, "generateDeviceId error", e)
            }
        }
    }

    fun loadSavedId() {
        viewModelScope.launch {
            val savedId = idManager.getId()
            if (savedId != null) {
                currentDeviceId = savedId
                if (_uiState.value is LocationUiState.Idle || _uiState.value is LocationUiState.Error) {
                    _uiState.value = LocationUiState.IdGenerated(savedId)
                }
            } else {
                // If no ID is saved, and current state is Idle, remain Idle or handle as new user.
                if (_uiState.value is LocationUiState.Idle) {
                    // Optionally, trigger ID generation or prompt user if needed
                    Log.d(TAG, "No saved ID found, ViewModel remains Idle or awaits ID generation.")
                }
            }
        }
    }

    fun startTrackingWithValidId() {
        viewModelScope.launch {
            val currentId = currentDeviceId
            if (currentId == null) {
                Log.w(TAG, "startTrackingWithValidId called but currentDeviceId is null. Attempting to load or generate.")
                // Attempt to load or prompt for ID generation if appropriate for your UI flow
                // For now, setting to Idle if ID isn't available.
                _uiState.value = LocationUiState.Idle
                return@launch
            }

            if (checkOverallLocationPermissions()) { // Use a consistent permission check
                startTracking(currentId)
            } else {
                Log.w(TAG, "Permisos de ubicación no concedidos al intentar iniciar rastreo.")
                _uiState.value = LocationUiState.IdGenerated(currentId) // Stay in IdGenerated, UI should prompt for perms
                // The UI observing this state should trigger the permission request flow.
            }
        }
    }

    private fun loadSafeZones() {
        val safeZonesRef = Firebase.database.reference.child("safe_zones")
        safeZonesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val safeZonesList = mutableListOf<SafeZone>()
                try {
                    snapshot.child("houses").children.forEach { house ->
                        val latitude = house.child("latitude").getValue(Double::class.java)
                        val longitude = house.child("longitude").getValue(Double::class.java)
                        val radius = house.child("radius").getValue(Double::class.java)
                        if (latitude != null && longitude != null && radius != null) {
                            safeZonesList.add(SafeZone(latitude, longitude, radius.toFloat()))
                        } else {
                            Log.w(TAG, "Datos de 'house' incompletos o nulos: ${house.key}")
                        }
                    }
                    val school = snapshot.child("school")
                    val schoolLat = school.child("lat").getValue(Double::class.java)
                    val schoolLng = school.child("lng").getValue(Double::class.java)
                    val schoolRadius = school.child("radius").getValue(Double::class.java)
                    if (schoolLat != null && schoolLng != null && schoolRadius != null) {
                        safeZonesList.add(SafeZone(schoolLat, schoolLng, schoolRadius.toFloat()))
                    } else {
                        Log.w(TAG, "Datos de 'school' incompletos o nulos.")
                    }
                    safeZones = safeZonesList
                    Log.d(TAG, "Zonas seguras cargadas: ${safeZones.size} zonas.")
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción al procesar datos de zonas seguras", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al cargar zonas seguras: ${error.message}")
            }
        })
    }

    // Renamed for clarity: ViewModel's internal location updates for safe zones
    @SuppressLint("MissingPermission") // Permissions should be checked before calling
    fun startInternalLocationUpdates() {
        if (!checkOverallLocationPermissions()) {
            Log.e(TAG, "Permisos de ubicación no concedidos para actualizaciones internas.")
            // UI should reflect that permissions are needed for this functionality
            return
        }

        // Si ya hay un callback, detenerlo antes de iniciar uno nuevo para evitar duplicados
        if (internalLocationCallback != null) {
            stopInternalLocationUpdates()
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        internalLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.lastOrNull()?.let { location ->
                    // Log.d(TAG, "InternalLocationUpdate: ${location.latitude}, ${location.longitude}")
                    checkIfInSafeZone(location)
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "Ubicación no disponible para actualizaciones internas.")
                }
            }
        }

        try {
            fusedLocationClientInternal.requestLocationUpdates(
                locationRequest,
                internalLocationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Actualizaciones internas de ubicación iniciadas.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al solicitar actualizaciones internas de ubicación: ${e.message}")
        }
    }

    private fun checkIfInSafeZone(location: Location) {
        if (safeZones.isEmpty()) {
            // Log.d(TAG, "No hay zonas seguras cargadas para verificar.")
            // Decide default behavior: if no zones, is it "in" or "out"?
            // Assuming "out" if no zones are defined, so updates continue.
            updateFirebaseSafeZoneStatus(false)
            return
        }

        val currentLat = location.latitude
        val currentLng = location.longitude
        var deviceIsInAnySafeZone = false

        for (zone in safeZones) {
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLng, zone.latitude, zone.longitude, results)
            val distanceInMeters = results[0]
            if (distanceInMeters <= zone.radius) {
                deviceIsInAnySafeZone = true
                break // Found in a safe zone
            }
        }
        // Log.d(TAG, "Device in safe zone: $deviceIsInAnySafeZone")
        updateFirebaseSafeZoneStatus(deviceIsInAnySafeZone)
    }

    private fun updateFirebaseSafeZoneStatus(inSafeZone: Boolean) {
        val deviceId = currentDeviceId
        if (deviceId == null || deviceId.isBlank()) {
            Log.e(TAG, "ID del dispositivo no disponible para actualizar estado de zona segura.")
            return
        }

        val deviceRef = Firebase.database.reference.child("devices").child(deviceId)
        deviceRef.child("in_safe_zone").setValue(inSafeZone)
            .addOnSuccessListener {
                // Log.d(TAG, "Estado in_safe_zone actualizado a $inSafeZone para $deviceId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar in_safe_zone para $deviceId", e)
            }

        // Esta lógica puede causar ciclos si start/stop no se manejan con cuidado.
        // Si está en zona segura, detiene las actualizaciones internas (de este ViewModel).
        // Si está fuera, las inicia (o asegura que estén iniciadas).
        if (inSafeZone) {
            if (internalLocationCallback != null) { // Solo detener si estaban activas
                Log.d(TAG, "Dispositivo en zona segura. Deteniendo actualizaciones internas del ViewModel.")
                stopInternalLocationUpdates()
            }
        } else {
            if (internalLocationCallback == null) { // Solo iniciar si estaban detenidas
                Log.d(TAG, "Dispositivo fuera de zona segura. Iniciando/asegurando actualizaciones internas del ViewModel.")
                startInternalLocationUpdates() // Esto verificará permisos de nuevo
            }
        }
    }

    // Renamed for clarity and corrected
    fun stopInternalLocationUpdates() {
        internalLocationCallback?.let {
            fusedLocationClientInternal.removeLocationUpdates(it)
            Log.d(TAG, "Actualizaciones internas de ubicación detenidas.")
        }
        internalLocationCallback = null // Muy importante para limpiar la referencia
    }

    // Consolidate permission checking
    private fun checkOverallLocationPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        // Coarse location is implied if fine is granted, but checking both is okay.
        // For simplicity, if only fine is strictly needed for PRIORITY_HIGH_ACCURACY:
        return fineLocationGranted
    }

    private fun startTracking(deviceId: String) { // This starts the Foreground Service
        viewModelScope.launch {
            if (!checkOverallLocationPermissions()) {
                Log.e(TAG, "startTracking: Permisos insuficientes.")
                _uiState.value = LocationUiState.IdGenerated(deviceId) // Revert to IdGenerated to prompt for permissions
                return@launch
            }
            _uiState.value = LocationUiState.Loading
            try {
                startForegroundService(deviceId)
                // Proporciona el valor inicial para inSafeZone
                _uiState.value = LocationUiState.TrackingActive(deviceId, false) // O true, según tu lógica inicial
                Log.i(TAG, "Rastreo (servicio foreground) iniciado para $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error en startTracking (al iniciar servicio)", e)
                _uiState.value = LocationUiState.Error("Error al iniciar rastreo: ${e.message}")
            }
        }
    }

    fun stopTracking() { // This stops the Foreground Service
        viewModelScope.launch {
            val currentId = currentDeviceId // Use the stored ID
            Log.d(TAG, "Solicitando detener rastreo para el dispositivo: $currentId")
            try {
                // 1. Detener LocationManager (que es usado por el servicio)
                locationManager.stopLocationUpdates() // LocationManager es el inyectado

                // 2. Enviar comando para detener el servicio foreground
                val stopIntent = Intent(context.applicationContext, LocationForegroundService::class.java).apply {
                    action = "STOP_TRACKING_ACTION"
                    // No es necesario pasar deviceId para detener, el servicio debe manejar su estado.
                }
                ContextCompat.startForegroundService(context.applicationContext, stopIntent)

                // 3. Detener las actualizaciones internas del ViewModel si estaban activas
                stopInternalLocationUpdates()

                _uiState.value = if (currentId != null) LocationUiState.IdGenerated(currentId) else LocationUiState.Idle
                Log.d(TAG, "Rastreo (servicio foreground) detenido solicitado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener rastreo", e)
                _uiState.value = LocationUiState.Error("Error al detener rastreo: ${e.message}")
            }
        }
    }

    private fun startForegroundService(deviceId: String) {
        val intent = Intent(context.applicationContext, LocationForegroundService::class.java).apply {
            putExtra("DEVICE_ID", deviceId)
            action = "START_TRACKING_ACTION"
        }
        ContextCompat.startForegroundService(context.applicationContext, intent)
        Log.d(TAG, "Solicitud para iniciar servicio foreground enviada para $deviceId")
    }

    private fun generateRandomId(): String {
        val chars = ('A'..'Z') + ('0'..'9') - listOf('I', 'O', '0', '1')
        return (1..5).joinToString("") { chars.random().toString() }
    }

    fun setErrorState(message: String) { // Not currently used, but kept from original
        viewModelScope.launch {
            if (_uiState.value !is LocationUiState.Idle && _uiState.value !is LocationUiState.IdGenerated) {
                _uiState.value = LocationUiState.Error(message)
            }
        }
    }

    // Asegurarse que el ViewModel limpie sus propios recursos de ubicación
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared. Deteniendo actualizaciones internas.")
        stopInternalLocationUpdates()
        // El locationManager inyectado debe ser manejado por su propio ciclo de vida o el de su Singleton.
        // El fusedLocationClientInternal no necesita un 'shutdown' explícito como un executor.
    }

    sealed class LocationUiState {
        object Idle : LocationUiState() // Estado inicial o después de detener.
        object Loading : LocationUiState() // Cargando ID o iniciando rastreo.
        data class IdGenerated(val id: String) : LocationUiState() // ID listo, rastreo no activo.
        data class TrackingActive(val id: String, val inSafeZone: Boolean) : LocationUiState() // Rastreo activo vía servicio.

        // LocationUpdated no parece usarse para emitir al UI aquí, las actualizaciones van a Firebase.
        // data class LocationUpdated(val location: Location) : LocationUiState()
        data class Error(val message: String) : LocationUiState()
    }

    companion object {
        private const val TAG = "LocationViewModel"
    }
}