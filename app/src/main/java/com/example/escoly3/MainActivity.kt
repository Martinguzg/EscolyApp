@file:Suppress("DEPRECATION") // Si es necesario por alguna dependencia o uso específico

package com.example.escoly3

// Asegúrate que IdManager y LocationViewModelFactory estén definidos y accesibles.
// Asumo que IdManager tiene un constructor que toma Context.
import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// import com.example.escoly3.LocationViewModelFactory
// import com.example.escoly3.LocationManagerSingleton
// import com.example.escoly3.WelcomeScreen // Si está en otro archivo
// import com.example.escoly3.AppScreen // Si está en otro archivo

class MainActivity : ComponentActivity() {
    private val auth by lazy { Firebase.auth }

    // ViewModel Initialization con LocationManagerSingleton y IdManager
    private val viewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(
            locationManager = LocationManagerSingleton.get(), // Usa el Singleton para tu LocationManager personalizado
            context = applicationContext
        )
    }

    private var isCheckingPermissions = false
    private var isDialogShowing = false

    private val basePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val backgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else {
        null
    }

    private val allRequiredPermissions = basePermissions + listOfNotNull(backgroundPermission)

    private val basePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult -> handleBasePermissionResult(permissionsResult) } // Llama al handler

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> handleBackgroundPermissionResult(granted) }

    private val deviceSettingsHandler by lazy {
        DeviceSettingsHandler(
            context = this,
            onShowBatteryDialog = { if (!isDialogShowing) showBatteryOptimizationDialog() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocationManagerSingleton.initialize(applicationContext) // Inicializa tu LocationManager personalizado
        createFrameworkNotificationChannel() // Renombrado para evitar confusión si tienes otro "location_channel"

        setContent {
            var showWelcomeScreen by remember { mutableStateOf(true) }

            if (showWelcomeScreen) {
                WelcomeScreen( // Asegúrate que WelcomeScreen esté definido
                    onTimeout = {
                        showWelcomeScreen = false
                        viewModel.loadSavedId()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val uiState by viewModel.uiState.collectAsState()
                AppScreen( // Asegúrate que AppScreen esté definido
                    uiState = uiState,
                    onGenerateId = ::handleGenerateId,
                    onStartTracking = ::startTrackingWithChecks,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }

        deviceSettingsHandler.checkManufacturerSettings() // Comprobar optimizaciones específicas del fabricante
        // checkBatteryOptimization se llama desde checkManufacturerSettings o periódicamente
        startOptimizationChecker()
    }

    private fun createFrameworkNotificationChannel() { // Renombrado
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "framework_location_channel", // ID de canal diferente si es necesario
                "Notificaciones del Sistema de Ubicación",
                NotificationManager.IMPORTANCE_HIGH // O la importancia que consideres
            ).apply {
                description = "Canal para notificaciones relacionadas con el framework de ubicación y permisos."
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startOptimizationChecker() {
        lifecycleScope.launch {
            while (true) {
                delay(60_000) // Chequear cada minuto, por ejemplo
                if (isTrackingActive() && !isDialogShowing && deviceSettingsHandler.isAppOptimized()) {
                    Log.d(TAG, "Optimización de batería detectada activa durante el rastreo.")
                    deviceSettingsHandler.checkBatteryOptimization() // Esto llamará a onShowBatteryDialog
                }
            }
        }
    }

    private fun isTrackingActive(): Boolean {
        return when (viewModel.uiState.value) {
            is LocationViewModel.LocationUiState.TrackingActive -> true
            // LocationUpdated ya no se usa directamente en AppScreen para este propósito
            // is LocationViewModel.LocationUiState.LocationUpdated -> true
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        // Evitar múltiples chequeos si ya hay un diálogo o chequeo en curso.
        if (!isCheckingPermissions && !isDialogShowing) {
            Log.d(TAG, "onResume: Verificando permisos e iniciando si es necesario.")
            checkPermissionsAndStart()
        } else {
            Log.d(TAG, "onResume: Chequeo de permisos omitido (isCheckingPermissions=$isCheckingPermissions, isDialogShowing=$isDialogShowing).")
        }
    }

    private fun checkPermissionsAndStart() {
        if (isCheckingPermissions || isDialogShowing) { // Doble chequeo para evitar concurrencia
            Log.d(TAG, "checkPermissionsAndStart: Ya en chequeo o mostrando diálogo.")
            return
        }
        isCheckingPermissions = true
        Log.d(TAG, "Iniciando checkPermissionsAndStart.")

        when {
            hasAllRequiredPermissions() -> { // Renombrado para claridad
                Log.d(TAG, "Todos los permisos concedidos.")
                if (isLocationServiceEnabled()) { // Renombrado para claridad
                    Log.d(TAG, "Servicios de ubicación activados.")
                    safelyStartTracking() // Intenta iniciar el rastreo del ForegroundService
                    viewModel.startInternalLocationUpdates() // Inicia las actualizaciones internas del ViewModel
                } else {
                    Log.d(TAG, "Servicios de ubicación desactivados, mostrando diálogo.")
                    showEnableLocationDialog()
                }
                isCheckingPermissions = false // Importante resetear aquí
            }
            // Comprobar primero el background si los base ya están, antes del rationale general
            // Esta lógica puede necesitar ajustarse según el flujo deseado
            shouldShowBasePermissionRationale() -> { // Renombrado y lógica específica
                Log.d(TAG, "Debería mostrar rationale para permisos base.")
                showRationaleDialog() // Se encargará de llamar a requestBasePermissions o finalizar
                // isCheckingPermissions se maneja dentro de los callbacks del diálogo
            }
            else -> { // No hay permisos, ni rationale que mostrar -> solicitar directamente
                Log.d(TAG, "Permisos no concedidos, solicitando permisos base.")
                requestBasePermissions() // isCheckingPermissions se maneja en el callback del launcher
            }
        }
        // No resetear isCheckingPermissions aquí si requestBasePermissions o showRationaleDialog
        // lo van a manejar en sus callbacks/dismiss listeners.
    }

    private fun safelyStartTracking() { // Inicia el ForegroundService a través del ViewModel
        Log.d(TAG, "safelyStartTracking llamado.")
        try {
            viewModel.startTrackingWithValidId()
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en safelyStartTracking", e)
            showError("Error crítico al iniciar rastreo: ${e.localizedMessage}")
        }
    }

    private fun requestBasePermissions() {
        Log.d(TAG, "Solicitando permisos base: ${basePermissions.joinToString()}")
        try {
            basePermissionLauncher.launch(basePermissions)
        } catch (e: Exception) {
            Log.e(TAG, "Error al lanzar solicitud de permisos base", e)
            showError("Error al solicitar permisos")
            isCheckingPermissions = false // Asegurar que se resetea en caso de error al lanzar
        }
    }

    private fun handleBasePermissionResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "Resultado de permisos base: $permissions")
        if (permissions.all { it.value }) {
            Log.i(TAG, "Permisos base concedidos.")
            // Ahora, si es necesario, verificar y solicitar permiso de fondo
            if (backgroundPermission != null && !hasBackgroundPermission()) {
                Log.d(TAG, "Permisos base OK, solicitando permiso de fondo.")
                requestBackgroundPermission()
            } else { // Permiso de fondo no necesario o ya concedido
                Log.d(TAG, "Permisos base OK, permiso de fondo OK o no necesario.")
                if (isLocationServiceEnabled()) {
                    safelyStartTracking()
                    viewModel.startInternalLocationUpdates() // También iniciar las internas del VM
                } else {
                    showEnableLocationDialog()
                }
                isCheckingPermissions = false
            }
        } else {
            Log.w(TAG, "Al menos un permiso base fue denegado.")
            showError("Los permisos de ubicación son esenciales para el rastreo.")
            isCheckingPermissions = false
        }
    }

    private fun requestBackgroundPermission() {
        Log.d(TAG, "Solicitando permiso de ubicación en segundo plano.")
        if (backgroundPermission == null) {
            Log.d(TAG, "No se necesita permiso de fondo (SDK < Q).")
            isCheckingPermissions = false; // Resetear si no hay nada que lanzar
            return
        }
        try {
            // Considerar mostrar un rationale ANTES de lanzar esta petición si es la segunda vez
            backgroundPermissionLauncher.launch(backgroundPermission)
        } catch (e: Exception) {
            Log.e(TAG, "Error al lanzar solicitud de permiso de fondo", e)
            showBackgroundPermissionRationaleDialog() // Renombrado
            // isCheckingPermissions se manejaría en el dismiss del diálogo
        }
    }

    private fun handleBackgroundPermissionResult(granted: Boolean) {
        Log.d(TAG, "Resultado del permiso de fondo: concedido=$granted")
        if (granted) {
            Log.i(TAG, "Permiso de fondo CONCEDIDO.")
            if (isLocationServiceEnabled()) {
                safelyStartTracking()
                viewModel.startInternalLocationUpdates()
            } else {
                showEnableLocationDialog()
            }
        } else {
            Log.w(TAG, "Permiso de fondo DENEGADO.")
            showBackgroundPermissionRationaleDialog() // Renombrado
        }
        isCheckingPermissions = false // Se completa el flujo de permisos aquí
    }

    private fun showBackgroundPermissionRationaleDialog() { // Renombrado
        if (isDialogShowing) return
        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permiso de Ubicación en Segundo Plano")
            setMessage("Para un rastreo confiable, incluso cuando la app no está visible, necesitamos que habilites el permiso de ubicación para 'Permitir todo el tiempo' desde los ajustes de la aplicación.")
            setPositiveButton("Abrir Ajustes") { _, _ ->
                openAppSettings()
                isDialogShowing = false
                isCheckingPermissions = false // Permite re-evaluar en onResume
            }
            setNegativeButton("Ahora No") { _, _ -> // Cambiado de "Continuar sin fondo"
                // Si continúan sin fondo, el rastreo puede ser limitado.
                // Aún así, si tienen permisos base, pueden iniciar el rastreo limitado.
                if (hasBasePermissions() && isLocationServiceEnabled()) {
                    safelyStartTracking()
                    viewModel.startInternalLocationUpdates()
                } else if (!isLocationServiceEnabled()) {
                    showEnableLocationDialog()
                } else {
                    showError("Funcionalidad de rastreo limitada sin permiso de fondo.")
                }
                isDialogShowing = false
                isCheckingPermissions = false
            }
            setOnDismissListener {
                isDialogShowing = false
                // Si el usuario simplemente descarta el diálogo, debemos resetear isCheckingPermissions
                // para permitir que el flujo se reintente en el próximo onResume si es necesario.
                if (isCheckingPermissions) isCheckingPermissions = false
            }
            setCancelable(false)
            show()
        }
    }

    private fun showRationaleDialog() { // Para permisos base
        if (isDialogShowing) return
        isCheckingPermissions = true // Mantenemos true, se resuelve en el callback del botón
        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Permisos de Ubicación Necesarios")
            setMessage("Escoly necesita acceso a tu ubicación para poder compartirla con tus contactos de seguridad. Por favor, concede los permisos cuando se te soliciten.")
            setPositiveButton("Entendido y Continuar") { _, _ ->
                isDialogShowing = false
                requestBasePermissions() // isCheckingPermissions se resetea en handleBasePermissionResult
            }
            setNegativeButton("Cancelar") { _, _ ->
                showError("Funcionalidad de ubicación desactivada.")
                isDialogShowing = false
                isCheckingPermissions = false // Reseteado porque el flujo de permisos termina aquí
            }
            setOnDismissListener {
                isDialogShowing = false
                if (isCheckingPermissions) isCheckingPermissions = false
            }
            setCancelable(false)
            show()
        }
    }

    private fun showEnableLocationDialog() {
        if (isDialogShowing) return
        isCheckingPermissions = true // Se resolverá en onResume o interacción
        isDialogShowing = true
        AlertDialog.Builder(this).apply {
            setTitle("Activar Ubicación")
            setMessage("Los servicios de ubicación de tu dispositivo están desactivados. Escoly necesita que los actives para funcionar.")
            setPositiveButton("Abrir Ajustes de Ubicación") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    Log.e(TAG, "Error al abrir ACTION_LOCATION_SOURCE_SETTINGS", e)
                    showError("No se pudo abrir la configuración de ubicación.")
                }
                isDialogShowing = false
                // isCheckingPermissions se mantiene, se re-evaluará en onResume
            }
            setNegativeButton("Cancelar") { _, _ ->
                showError("El rastreo no puede iniciar sin servicios de ubicación activos.")
                isDialogShowing = false
                isCheckingPermissions = false
            }
            setOnDismissListener {
                isDialogShowing = false
                if (isCheckingPermissions) isCheckingPermissions = false
            }
            setCancelable(false)
            show()
        }
    }

    private fun showBatteryOptimizationDialog() {
        if (isDialogShowing) return // Prevenir múltiples dialogos
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("Optimización de Batería Activada")
            .setMessage("Para asegurar que Escoly pueda compartir tu ubicación de manera continua y confiable, por favor deshabilita las optimizaciones de batería para la aplicación.")
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al abrir ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", e)
                    // Fallback a los detalles de la aplicación si la acción directa falla
                    openAppSettings()
                }
                isDialogShowing = false
            }
            .setNegativeButton("Más Tarde") { _, _ -> isDialogShowing = false }
            .setOnDismissListener { isDialogShowing = false }
            .setCancelable(false)
            .show()
    }

    private fun isLocationServiceEnabled(): Boolean { // Renombrado
        return try {
            val systemService = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager
            if (systemService == null) {
                Log.e(TAG, "LocationManager del sistema no disponible.")
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                systemService.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                systemService.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                        systemService.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al verificar estado de servicios de ubicación", e)
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
            Log.e(TAG, "Error al abrir ajustes de la aplicación", e)
            showError("No se pudo abrir la configuración de la aplicación.")
        }
    }

    private fun handleGenerateId() {
        Log.d(TAG, "handleGenerateId llamado.")
        auth.currentUser?.uid?.let { uid ->
            Log.d(TAG, "Usuario existente, generando ID con UID: $uid")
            viewModel.generateDeviceId(uid)
        } ?: run {
            Log.d(TAG, "Sin usuario actual, intentando login anónimo.")
            auth.signInAnonymously().addOnCompleteListener(this) { task -> // Añadir this para ciclo de vida
                if (task.isSuccessful) {
                    val user = task.result?.user
                    Log.d(TAG, "Login anónimo exitoso, UID: ${user?.uid}")
                    user?.uid?.let { uid ->
                        viewModel.generateDeviceId(uid)
                    } ?: run {
                        Log.e(TAG, "Login anónimo exitoso pero UID es nulo.")
                        showError("Error al obtener ID de usuario anónimo.")
                    }
                } else {
                    Log.e(TAG, "Error en login anónimo.", task.exception)
                    showError("Error de autenticación: ${task.exception?.localizedMessage ?: "Desconocido"}")
                }
            }
        }
    }

    private fun startTrackingWithChecks() { // Este es el que llama el botón "Iniciar" de la UI
        Log.d(TAG, "startTrackingWithChecks llamado.")
        // Re-evaluar permisos y estado de ubicación siempre antes de intentar iniciar.
        checkPermissionsAndStart()
    }

    private fun showError(message: String) {
        // Podrías mostrar un Toast o un Snackbar además de actualizar el ViewModel
        // Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Mostrando error a través del ViewModel: $message")
        viewModel.setErrorState(message) // El ViewModel ya lo maneja en viewModelScope
    }

    private fun hasAllRequiredPermissions(): Boolean { // Renombrado
        return allRequiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBasePermissions(): Boolean {
        return basePermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun hasBackgroundPermission(): Boolean {
        return if (backgroundPermission != null) {
            ContextCompat.checkSelfPermission(this, backgroundPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No se requiere en versiones antiguas de Android
        }
    }

    private fun shouldShowBasePermissionRationale(): Boolean { // Renombrado
        return basePermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Deteniendo rastreo.")
        viewModel.stopTracking() // Esto debería detener el servicio y el LocationManager del ViewModel
    }

    companion object {
        private const val TAG = "MainActivityEscoly" // Tag único para esta clase
    }
}

// --- DeviceSettingsHandler ---
// (Esta clase parece no tener diferencias lógicas significativas entre tus versiones,
// más allá del formato. Se presenta la versión con formato limpio.)

class DeviceSettingsHandler(
    private val context: Context,
    private val onShowBatteryDialog: () -> Unit
) {
    private var dialogShownForCurrentManufacturer = false // Para evitar mostrar múltiples veces por fabricante

    fun checkManufacturerSettings() {
        // Solo mostrar diálogos específicos del fabricante una vez por sesión de la app o según se necesite
        if (dialogShownForCurrentManufacturer) return

        val manufacturer = Build.MANUFACTURER.lowercase()
        var handledByManufacturerLogic = true
        when (manufacturer) {
            "xiaomi" -> setupXiaomi()
            "huawei" -> setupHuawei()
            "oppo", "realme", "oneplus" -> setupOppoFamily()
            "vivo" -> setupVivo()
            "samsung" -> setupSamsung()
            else -> {
                handledByManufacturerLogic = false
                checkBatteryOptimization() // Para otros fabricantes, chequear directamente optimización
            }
        }
        if (handledByManufacturerLogic) {
            dialogShownForCurrentManufacturer = true
        }
    }

    fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isAppOptimized()) {
            Log.d(TAG_HANDLER, "App está optimizada, mostrando diálogo de optimización de batería.")
            onShowBatteryDialog()
        } else {
            Log.d(TAG_HANDLER, "App no está optimizada o SDK < M.")
        }
    }

    fun isAppOptimized(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false // true si está optimizada (NO ignorando)
            } catch (e: Exception) {
                Log.e(TAG_HANDLER, "Error al verificar isIgnoringBatteryOptimizations", e)
                true // Asumir que está optimizada si hay error, para ser cautelosos
            }
        } else {
            false // No hay optimizaciones de este tipo antes de Marshmallow
        }
    }

    private fun setupXiaomi() {
        // Intenta abrir la pantalla específica de Xiaomi. Si falla, abre los ajustes generales de la app.
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
            }
            context.startActivity(intent)
            // Mostrar un diálogo guía después de intentar abrir los ajustes
            showGuidanceDialog(
                "Ajustes de Batería en Xiaomi",
                "Para asegurar el funcionamiento correcto de Escoly:\n\n1. Busca Escoly en la lista.\n2. Configura 'Sin restricciones' para la optimización de batería.\n3. Adicionalmente, activa el 'Inicio automático' si está disponible."
            )
        } catch (e: Exception) {
            Log.e(TAG_HANDLER, "No se pudo abrir la pantalla específica de Xiaomi. Abriendo ajustes generales.", e)
            showGuidanceDialog(
                "Ajustes de Batería en Xiaomi",
                "No pudimos abrir la pantalla específica. Por favor, ve a los Ajustes de Batería de tu Xiaomi:\n\n1. Busca Escoly.\n2. Configura 'Sin restricciones'.\n3. Activa 'Inicio automático'.",
                true // Indicar que abra los ajustes generales
            )
        }
    }

    private fun setupHuawei() {
        showGuidanceDialog(
            "Ajustes de Batería en Huawei/Honor",
            "Para asegurar el funcionamiento correcto de Escoly:\n\n1. Ve a Ajustes > Batería > Inicio de aplicaciones.\n2. Busca Escoly y desactiva la gestión automática.\n3. En la ventana emergente, asegúrate de que 'Ejecutar en segundo plano' esté activado."
        )
    }

    private fun setupOppoFamily() { // OPPO, Realme, OnePlus
        showGuidanceDialog(
            "Ajustes de Batería en ${Build.MANUFACTURER}",
            "Para asegurar el funcionamiento correcto de Escoly:\n\n1. Ve a Ajustes > Batería > Más ajustes de batería (o similar) > Optimizar uso de batería.\n2. Selecciona 'Todas las apps', busca Escoly y elige 'No optimizar'.\n3. Adicionalmente, busca 'Administrador de inicio' o 'Aplicaciones de inicio automático' y permite que Escoly se inicie automáticamente."
        )
    }

    private fun setupVivo() {
        showGuidanceDialog(
            "Ajustes de Batería en Vivo",
            "Para asegurar el funcionamiento correcto de Escoly:\n\n1. Ve a Ajustes > Batería > Alto consumo en segundo plano.\n2. Encuentra Escoly y actívalo.\n3. Adicionalmente, en iManager > Administrador de aplicaciones > Administrador de autoinicio, permite que Escoly se inicie automáticamente."
        )
    }

    private fun setupSamsung() {
        showGuidanceDialog(
            "Ajustes de Batería en Samsung",
            "Para asegurar el funcionamiento correcto de Escoly:\n\n1. Ve a Ajustes > Mantenimiento del dispositivo (o Cuidado del dispositivo) > Batería > Límites de uso en segundo plano.\n2. Asegúrate de que Escoly NO esté en 'Aplicaciones en suspensión' ni 'Aplicaciones en suspensión profunda'. Añádela a 'Aplicaciones que nunca se suspenden' si es necesario.\n3. También, en Ajustes > Aplicaciones > Escoly > Batería, selecciona 'Sin restricciones' o 'No optimizada'."
        )
    }

    // Renombrado para mayor claridad
    private fun showGuidanceDialog(title: String, message: String, openGenericSettingsOnError: Boolean = false) {
        if ((context as? ComponentActivity)?.isFinishing == true) return // Evitar mostrar diálogo si la actividad está terminando

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Abrir Ajustes") { _, _ ->
                if (openGenericSettingsOnError) openApplicationSettings() else openBatteryOptimizationSettings()
            }
            .setNegativeButton("Entendido", null) // Cambiado de "Más tarde"
            .setCancelable(false)
            .show()
    }

    private fun openApplicationSettings() { // Renombrado
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG_HANDLER, "Error al abrir ajustes generales de la app.", e)
            Toast.makeText(context, "No se pudieron abrir los ajustes. Inténtalo manualmente.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e(TAG_HANDLER, "Error al abrir ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intentando detalles de app.", e)
                openApplicationSettings() // Fallback
            }
        } else {
            openApplicationSettings() // Para SDKs antiguos, ir a detalles de app
        }
    }

    companion object {
        private const val TAG_HANDLER = "DeviceSettingsHandler" // Tag para esta clase
    }
}