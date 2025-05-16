
package com.example.escoly3

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
import android.location.Location

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Escoly3 Tracker",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (uiState) {
                        is LocationViewModel.LocationUiState.Idle -> {
                            IdleStateContent(onGenerateId)
                        }
                        is LocationViewModel.LocationUiState.IdGenerated -> {
                            IdGeneratedStateContent(uiState.id, onStartTracking)
                        }
                        is LocationViewModel.LocationUiState.TrackingActive -> {
                            TrackingActiveStateContent(uiState.id, onStopTracking)
                        }
                        is LocationViewModel.LocationUiState.Error -> {
                            ErrorStateContent(uiState.message, onGenerateId)
                        }
                        is LocationViewModel.LocationUiState.LocationUpdated -> {
                            LocationUpdatedStateContent(uiState.location)
                        }
                        LocationViewModel.LocationUiState.Loading -> {
                            LoadingStateContent()
                        }
                    }
                }
            }

            // Footer informativo
            Text(
                text = "Estado: ${getStatusText(uiState)}",
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun IdleStateContent(onGenerateId: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Presiona para comenzar",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGenerateId,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(200.dp)
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = id,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartTracking,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(200.dp)
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
            tint = MaterialTheme.colorScheme.primary
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
            shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(200.dp)
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
            tint = MaterialTheme.colorScheme.primary
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
            strokeWidth = 4.dp
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







