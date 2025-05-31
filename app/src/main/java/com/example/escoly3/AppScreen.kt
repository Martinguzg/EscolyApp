package com.example.escoly3 // Asegúrate que el package sea el correcto

// import android.location.Location // No se usa directamente en este Composable
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    uiState: LocationViewModel.LocationUiState,
    onGenerateId: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    // Definición del esquema de colores (como lo tenías)
    val colorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        secondary = Color(0xFF212121), // Un gris oscuro casi negro
        onSecondary = Color.White,
        tertiary = Color.Black,
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF5F5F5), // Un gris muy claro para superficies sutiles
        onSurface = Color.Black,
        error = Color(0xFFB00020) // Rojo estándar para errores
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography( // Tipografía personalizada (como la tenías)
            displaySmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            )
            // Puedes definir más estilos aquí si es necesario
        ),
        shapes = Shapes( // Formas personalizadas (como las tenías)
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        Scaffold(
            containerColor = Color.White, // Fondo principal del Scaffold
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "ESCOLY",
                                fontWeight = FontWeight.Black, // Muy negrita
                                letterSpacing = 3.sp,
                                fontSize = 27.sp
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White, // Fondo de la TopAppBar
                            titleContentColor = Color.Black // Color del título
                        )
                    )
                    Text( // Subtítulo
                        text = "Seguridad familiar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f), // Color con transparencia
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center // Centrado
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // Padding del Scaffold
            ) {
                Box( // Contenedor principal para los diferentes estados de la UI
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Ocupa el espacio disponible
                        .padding(24.dp),
                    contentAlignment = Alignment.Center // Centra el contenido
                ) {
                    when (uiState) {
                        is LocationViewModel.LocationUiState.Idle ->
                            IdleStateContent(onGenerateId = onGenerateId)

                        is LocationViewModel.LocationUiState.IdGenerated ->
                            IdGeneratedStateContent(
                                id = uiState.id,
                                onStartTracking = onStartTracking
                            )

                        is LocationViewModel.LocationUiState.TrackingActive ->
                            TrackingActiveStateContent(
                                id = uiState.id,
                                inSafeZone = uiState.inSafeZone, // Pasar el estado de zona segura
                                onStopTracking = onStopTracking
                            )

                        is LocationViewModel.LocationUiState.Error ->
                            ErrorStateContent(
                                message = uiState.message,
                                onGenerateId = onGenerateId // Podría ser reintentar u otra acción
                            )
                        // El estado LocationUpdated no se maneja aquí porque TrackingActive ya incluye la info necesaria
                        // Si LocationUpdated tuviera un propósito UI diferente, se añadiría su propio Composable.
                        // is LocationViewModel.LocationUiState.LocationUpdated -> { /* Manejo específico si es necesario */ }


                        LocationViewModel.LocationUiState.Loading ->
                            LoadingStateContent()
                    }
                }

                // Texto inferior informativo
                Text(
                    text = when (uiState) {
                        is LocationViewModel.LocationUiState.IdGenerated ->
                            "Comparte este código solo con tus padres o tutores."
                        is LocationViewModel.LocationUiState.TrackingActive ->
                            if (uiState.inSafeZone) "Estás en una zona segura."
                            else "Ubicación compartida. Fuera de zona segura."
                        else -> "Seguridad y privacidad para tu familia."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp), // Más padding vertical
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
        verticalArrangement = Arrangement.spacedBy(32.dp) // Espacio entre elementos
    ) {
        Column( // Contenedor para ícono y textos descriptivos
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Icono de ubicación",
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary // Usar color primario del tema
            )
            Text(
                text = "Comparte tu Ubicación",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Con tus padres o tutores de forma segura.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Button( // Botón para generar código
            onClick = onGenerateId,
            shape = MaterialTheme.shapes.large, // Bordes redondeados
            modifier = Modifier.width(240.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Sin sombra
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface( // Contenedor del código con fondo
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary, // Fondo negro
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), // Borde sutil
                modifier = Modifier
                    .padding(horizontal = 16.dp) // Menos padding para que no sea tan ancho
                    .clickable {
                        clipboardManager.setText(AnnotatedString(id))
                        Toast.makeText(context, "Código '$id' copiado", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row( // Para alinear texto e ícono de copiar
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp) // Más espacio
                ) {
                    Text(
                        text = id,
                        color = MaterialTheme.colorScheme.onPrimary, // Texto blanco
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp // Más espaciado entre letras
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar código",
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), // Icono blanco con transparencia
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "Toca el código para copiarlo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Button( // Botón para iniciar rastreo
            onClick = onStartTracking,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("INICIAR COMPARTIR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrackingActiveStateContent(
    id: String,
    inSafeZone: Boolean, // Nuevo parámetro
    onStopTracking: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween, // Para empujar botón abajo
        modifier = Modifier
            .fillMaxSize() // Ocupar todo el espacio del Box
            .padding(bottom = 16.dp) // Padding inferior para separar el botón
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp), // Espacio entre elementos superiores
            modifier = Modifier.padding(top = 32.dp) // Padding superior
        ) {
            Box( // Contenedor del ícono de estado de ubicación/zona segura
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp) // Más grande
                    .background(
                        // Cambia color de fondo según si está en zona segura
                        color = if (inSafeZone) Color(0xFFE8F5E9) /* Verde claro */ else Color(0xFFFFF8E1) /* Amarillo claro */,
                        shape = CircleShape
                    )
                    .padding(16.dp) // Padding interno
            ) {
                Icon(
                    imageVector = if (inSafeZone) Icons.Default.Shield else Icons.Default.LocationOn,
                    contentDescription = if (inSafeZone) "En zona segura" else "Ubicación activa",
                    modifier = Modifier.size(48.dp), // Icono más grande
                    tint = if (inSafeZone) Color(0xFF388E3C) /* Verde oscuro */ else MaterialTheme.colorScheme.primary
                )
            }

            Column( // Textos descriptivos
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (inSafeZone) "Estás en una Zona Segura" else "Compartiendo Ubicación",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, // Un poco menos negrita que Bold
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (inSafeZone) "Tu ubicación actual es reconocida como segura."
                    else "Tu ubicación se comparte de forma segura.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Text( // Muestra el ID del dispositivo
                    text = "ID: $id",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Botón para detener el rastreo, colocado al final
        Button(
            onClick = onStopTracking,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB00020), // Rojo para detener
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = "Detener",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("DETENER RASTREO", fontWeight = FontWeight.Bold)
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
                imageVector = Icons.Default.Warning, // Icono más apropiado para error
                contentDescription = "Error",
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error // Usar color de error del tema
            )
            Text(
                text = "Ocurrió un Error",
                style = MaterialTheme.typography.titleLarge, // Más prominente
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Button( // Botón para reintentar (o generar ID nuevo)
            onClick = onGenerateId, // La acción podría ser más específica que solo generar ID
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.width(240.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary, // Usar color secundario
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reintentar",
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("REINTENTAR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingStateContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio entre indicador y texto
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp, // Grosor del indicador
            color = MaterialTheme.colorScheme.primary // Usar color primario
        )
        Text(
            text = "Cargando...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}