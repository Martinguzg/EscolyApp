package com.example.escoly3

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.escoly3.ui.theme.Escoly3Theme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val auth by lazy { Firebase.auth }
    private val viewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(
            locationManager = LocationManager(applicationContext),
            context = applicationContext
        )
    }

    // Control de estado
    private var isCheckingPermissions = false
    private var isDialogShowing = false

    // Permisos básicos
    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Permiso de fondo (Android 10+)
    private val backgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    // Todos los permisos requeridos
    private val allRequiredPermissions = basePermissions + backgroundPermission

    // Contratos para permisos
    private val basePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { handleBasePermissionResult(it) }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { handleBackgroundPermissionResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Escoly3Theme {
                val uiState by viewModel.uiState.collectAsState()
                AppScreen(
                    uiState = uiState,
                    onGenerateId = ::handleGenerateId,
                    onStartTracking = ::startTrackingWithChecks,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }

        checkBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        if (!isCheckingPermissions && !isDialogShowing) {
            checkPermissionsAndStart()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                lifecycleScope.launch {
                    delay(2000) // Espera para no interrumpir flujo inicial

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Optimización de Batería")
                        .setMessage("Para que la ubicación se comparta continuamente, necesitamos que desactives la optimización de batería para esta aplicación")
                        .setPositiveButton("Configurar") { _, _ ->
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Más tarde", null)
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        isCheckingPermissions = true

        when {
            hasAllPermissions() -> {
                if (isLocationEnabled()) {
                    Log.d(TAG, "Todos los permisos concedidos y ubicación activada")
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
                isCheckingPermissions = false
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showRationaleDialog()
            }
            else -> {
                requestBasePermissions()
            }
        }
    }

    private fun safelyStartTracking() {
        try {
            viewModel.startTrackingWithValidId()
        } catch (e: SecurityException) {
            showError("Error de seguridad: ${e.message}")
            Log.e(TAG, "SecurityException al iniciar rastreo", e)
        } catch (e: Exception) {
            showError("Error al iniciar rastreo: ${e.message}")
            Log.e(TAG, "Exception al iniciar rastreo", e)
        }
    }

    private fun requestBasePermissions() {
        try {
            basePermissionLauncher.launch(basePermissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar permisos base", e)
            showError("Error al solicitar permisos")
            isCheckingPermissions = false
        }
    }

    private fun handleBasePermissionResult(permissions: Map<String, Boolean>) {
        if (permissions.all { it.value }) {
            if (backgroundPermission.isNotEmpty() && !hasBackgroundPermission()) {
                requestBackgroundPermission()
            } else {
                if (isLocationEnabled()) {
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
            }
        } else {
            showError("Permisos esenciales denegados")
        }
        isCheckingPermissions = false
    }

    private fun requestBackgroundPermission() {
        try {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar permiso de fondo", e)
            showBackgroundPermissionRationale()
        }
    }

    private fun handleBackgroundPermissionResult(granted: Boolean) {
        if (granted) {
            if (isLocationEnabled()) {
                safelyStartTracking()
            } else {
                showEnableLocationDialog()
            }
        } else {
            showBackgroundPermissionRationale()
        }
    }

    private fun showBackgroundPermissionRationale() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permiso de fondo requerido")
            setMessage("Para rastreo continuo, active manualmente 'Permitir todo el tiempo' en Ajustes > Ubicación")
            setPositiveButton("Abrir Ajustes") { _, _ ->
                openAppSettings()
                isDialogShowing = false
            }
            setNegativeButton("Continuar sin fondo") { _, _ ->
                if (isLocationEnabled()) {
                    safelyStartTracking()
                } else {
                    showEnableLocationDialog()
                }
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun showRationaleDialog() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permisos necesarios")
            setMessage("Esta app necesita acceso a tu ubicación para funcionar correctamente")
            setPositiveButton("Entendido") { _, _ ->
                requestBasePermissions()
                isDialogShowing = false
            }
            setNegativeButton("Cancelar") { _, _ ->
                showError("Funcionalidad limitada sin permisos")
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun showEnableLocationDialog() {
        if (isDialogShowing) return

        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Ubicación desactivada")
            setMessage("Active los servicios de ubicación para continuar")
            setPositiveButton("Abrir Configuración") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                isDialogShowing = false
            }
            setNegativeButton("Cancelar") { _, _ ->
                isDialogShowing = false
            }
            setOnDismissListener { isDialogShowing = false }
            show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar estado de ubicación", e)
            false
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir ajustes", e)
            showError("No se pudo abrir la configuración")
        }
    }

    private fun handleGenerateId() {
        auth.currentUser?.let { user ->
            viewModel.generateDeviceId(user.uid)
        } ?: run {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    viewModel.generateDeviceId(task.result.user?.uid)
                } else {
                    showError("Error de autenticación: ${task.exception?.message ?: "Desconocido"}")
                }
            }
        }
    }

    private fun startTrackingWithChecks() {
        if (hasAllPermissions()) {
            if (isLocationEnabled()) {
                safelyStartTracking()
            } else {
                showEnableLocationDialog()
            }
        } else {
            checkPermissionsAndStart()
        }
    }

    private fun showError(message: String) {
        lifecycleScope.launch {
            viewModel.setErrorState(message)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return allRequiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopTracking()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}