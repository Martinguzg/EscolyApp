package com.example.escoly3

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            is LocationViewModel.LocationUiState.Idle -> {
                Text("Presiona 'Generar ID' para comenzar")
                Button(onClick = onGenerateId) {
                    Text("Generar ID")
                }
            }
            is LocationViewModel.LocationUiState.IdGenerated -> {
                Text("ID Generado:", color = Color.Gray)
                Text(uiState.id, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartTracking) {
                    Text("Iniciar Rastreo")
                }
            }
            is LocationViewModel.LocationUiState.TrackingActive -> {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Ubicación activa",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Rastreo activo para ID: ${uiState.id}")
                Button(
                    onClick = onStopTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Detener Rastreo")
                }
            }
            is LocationViewModel.LocationUiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error: ${uiState.message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onGenerateId) {
                        Text("Reintentar")
                    }
                }
            }
            is LocationViewModel.LocationUiState.LocationUpdated -> {
                Text("Ubicación actualizada: ${uiState.location.latitude}, ${uiState.location.longitude}")
            }
            LocationViewModel.LocationUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Cargando...")
                }
            }
        }
    }
}