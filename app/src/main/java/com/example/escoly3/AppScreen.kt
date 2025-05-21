package com.example.escoly3

import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (uiState) {
                        is LocationViewModel.LocationUiState.Idle ->
                            IdleStateContent(
                                onGenerateId = onGenerateId
                            )

                        is LocationViewModel.LocationUiState.IdGenerated ->
                            IdGeneratedStateContent(
                                id = uiState.id,
                                onStartTracking = onStartTracking
                            )

                        is LocationViewModel.LocationUiState.TrackingActive ->
                            TrackingActiveStateContent(
                                id = uiState.id,
                                onStopTracking = onStopTracking
                            )

                        is LocationViewModel.LocationUiState.Error ->
                            ErrorStateContent(
                                message = uiState.message,
                                onGenerateId = onGenerateId
                            )

                        is LocationViewModel.LocationUiState.LocationUpdated ->
                            TrackingActiveStateContent(
                                id = (uiState as? LocationViewModel.LocationUiState.TrackingActive)?.id ?: "",
                                onStopTracking = onStopTracking
                            )

                        LocationViewModel.LocationUiState.Loading ->
                            LoadingStateContent()
                    }
                }

                Text(
                    text = when (uiState) {
                        is LocationViewModel.LocationUiState.IdGenerated ->
                            "Comparte solo con tus padres/tutores"
                        is LocationViewModel.LocationUiState.TrackingActive,
                        is LocationViewModel.LocationUiState.LocationUpdated -> // Añadimos este caso
                            "Ubicación compartida con tus padres/tutores"
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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(id))
                        Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = id,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "Toca el código para copiarlo",
                style = MaterialTheme.typography.labelSmall,
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
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        // Indicador elegante
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color(0xFFF5F5F5),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.Black
            )
        }

        // Contenido textual minimalista
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Ubicación compartida",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Estás siendo localizado de forma segura",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            // Código en tipografía monoespaciada para elegancia
            Text(
                text = id,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                ),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
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