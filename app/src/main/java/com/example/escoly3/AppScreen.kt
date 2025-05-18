package com.example.escoly3

import android.location.Location
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    // Esquema de color minimalista con más contraste
    val colorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        secondary = Color(0xFF212121),
        onSecondary = Color.White,
        tertiary = Color.Black,
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF5F5F5),
        onSurface = Color.Black,
        error = Color(0xFFB00020)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            displaySmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            )
        ),
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "ESCOLY",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                fontSize = 27.sp
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color.Black
                        )
                    )
                    Text(
                        text = "Seguridad familiar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Contenido principal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
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

                // Footer con instrucciones
                Text(
                    text = when (uiState) {
                        is LocationViewModel.LocationUiState.IdGenerated -> "Comparte solo con tus padres/tutores"
                        is LocationViewModel.LocationUiState.TrackingActive -> "Ubicación compartida con tus padres/tutores"
                        else -> "Seguridad familiar • Privacidad garantizada"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun IdleStateContent(onGenerateId: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color.Black
            )
            Text(
                text = "Comparte tu ubicación",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Con tus padres o tutores de forma segura",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }

        Button(
            onClick = onGenerateId,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp
            )
        ) {
            Text("CREAR CÓDIGO", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IdGeneratedStateContent(id: String, onStartTracking: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Tu código seguro es:",
                style = MaterialTheme.typography.titleMedium
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = id,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Comparte solo con personas de confianza",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }

        Button(
            onClick = onStartTracking,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("INICIAR COMPARTIR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrackingActiveStateContent(id: String, onStopTracking: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color.Black
            )
            Text(
                text = "Ubicación compartida",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Código: $id",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }

        OutlinedButton(
            onClick = onStopTracking,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.Black
            ),
            border = BorderStroke(
                width = 1.5.dp,
                color = Color.Black
            )
        ) {
            Text("DETENER COMPARTIR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorStateContent(message: String, onGenerateId: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color.Black
            )
            Text(
                text = "Ocurrió un error",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }

        Button(
            onClick = onGenerateId,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("INTENTAR DE NUEVO", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LocationUpdatedStateContent(location: Location) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color.Black
        )

        Text(
            text = "Tus padres pueden verte",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal
        )

        Text(
            text = "Estás siendo localizado de forma segura",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingStateContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = Color.Black
        )
        Text(
            text = "Cargando...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun getStatusText(uiState: LocationViewModel.LocationUiState): String {
    return when (uiState) {
        is LocationViewModel.LocationUiState.Idle -> "Listo"
        is LocationViewModel.LocationUiState.IdGenerated -> "Código generado"
        is LocationViewModel.LocationUiState.TrackingActive -> "Compartiendo ubicación"
        is LocationViewModel.LocationUiState.Error -> "Error"
        is LocationViewModel.LocationUiState.LocationUpdated -> "Ubicación actualizada"
        LocationViewModel.LocationUiState.Loading -> "Cargando"
    }
}