package com.example.escoly3

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.escoly3.ui.theme.AppShapes
import com.example.escoly3.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    // Definición del tema de color monocromático
    val monochromeColors = darkColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        primaryContainer = Color.Black,
        onPrimaryContainer = Color.White,
        inversePrimary = Color.White,
        secondary = Color.DarkGray,
        onSecondary = Color.White,
        secondaryContainer = Color.DarkGray,
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF4CAF50), // Color de acento verde
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF4CAF50),
        onTertiaryContainer = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color.Black,
        inverseSurface = Color.Black,
        inverseOnSurface = Color.White,
        error = Color.Red,
        onError = Color.White,
        errorContainer = Color(0xFFFFCDD2),
        onErrorContainer = Color.Black,
        outline = Color.LightGray,
        outlineVariant = Color(0xFFEEEEEE),
        scrim = Color.Black.copy(alpha = 0.5f)
    )

    // Usamos el MaterialTheme con nuestras definiciones personalizadas
    MaterialTheme(
        colorScheme = monochromeColors,
        typography = AppTypography, // Usando la tipografía definida en Theme.kt
        shapes = AppShapes // Usando las formas definidas en Theme.kt
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Escoly",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large, // Usando la forma grande definida
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (uiState) {
                            is LocationViewModel.LocationUiState.Idle -> IdleStateContent(onGenerateId)
                            is LocationViewModel.LocationUiState.IdGenerated -> IdGeneratedStateContent(uiState.id, onStartTracking)
                            is LocationViewModel.LocationUiState.TrackingActive -> TrackingActiveStateContent(uiState.id, onStopTracking)
                            is LocationViewModel.LocationUiState.Error -> ErrorStateContent(uiState.message, onGenerateId)
                            is LocationViewModel.LocationUiState.LocationUpdated -> LocationUpdatedStateContent(uiState.location)
                            LocationViewModel.LocationUiState.Loading -> LoadingStateContent()
                        }
                    }
                }

                // Footer informativo
                Text(
                    text = "Estado: ${getStatusText(uiState)}",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Componentes de estado (sin cambios en su lógica, pero usando MaterialTheme)
@Composable
private fun IdleStateContent(onGenerateId: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Presiona para comenzar",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGenerateId,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("Generar ID")
        }
    }
}

@Composable
private fun IdGeneratedStateContent(id: String, onStartTracking: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "ID Generado:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = id,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartTracking,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("Iniciar Rastreo")
        }
    }
}

@Composable
private fun TrackingActiveStateContent(id: String, onStopTracking: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Rastreo Activo",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ID: $id",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStopTracking,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(200.dp)
        ) {
            Text("Detener Rastreo")
        }
    }
}

@Composable
private fun ErrorStateContent(message: String, onGenerateId: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ocurrió un error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGenerateId,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun LocationUpdatedStateContent(location: Location) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ubicación actualizada",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Lat: ${"%.6f".format(location.latitude)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Lng: ${"%.6f".format(location.longitude)}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LoadingStateContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Cargando...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun getStatusText(uiState: LocationViewModel.LocationUiState): String {
    return when (uiState) {
        is LocationViewModel.LocationUiState.Idle -> "Listo"
        is LocationViewModel.LocationUiState.IdGenerated -> "ID Generado"
        is LocationViewModel.LocationUiState.TrackingActive -> "Rastreando"
        is LocationViewModel.LocationUiState.Error -> "Error"
        is LocationViewModel.LocationUiState.LocationUpdated -> "Actualizando"
        LocationViewModel.LocationUiState.Loading -> "Cargando"
    }
}