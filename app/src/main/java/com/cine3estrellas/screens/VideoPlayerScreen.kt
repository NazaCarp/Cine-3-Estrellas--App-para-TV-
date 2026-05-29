package com.cine3estrellas.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.cine3estrellas.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@SuppressLint("DefaultLocale")
@Composable
fun VideoPlayerScreen(movieId: Int, version: String, onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Obtener detalles de la película desde la caché local
    val movie = remember(movieId) {
        DataCache.movieDetailsMap[movieId] ?:
        DataCache.homeHeroMovies.find { it.id == movieId } ?: 
        DataCache.homeCategoryMovies.values.flatten().find { it.id == movieId }
    }

    val movieTitle = movie?.title ?: "Película"

    // Obtener la URL de reproducción del embed original
    val versions = movie?.versions
    val selectedVersion = versions?.get(version) ?: versions?.values?.firstOrNull()
    val embedUrl = selectedVersion?.url

    var cleanUrl by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Inicializar ExoPlayer de forma segura con cabeceras HTTP optimizadas para evadir bloqueos
    val player = remember(embedUrl) {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        
        // Determinar Referer según el embedUrl
        val referer = when {
            embedUrl == null -> null
            embedUrl.contains("ok.ru") || embedUrl.contains("odnoklassniki") -> "https://ok.ru/"
            embedUrl.contains("vidsonic") -> "https://vidsonic.net/"
            embedUrl.contains("vidmoly") -> "https://vidmoly.to/"
            else -> null
        }
        
        val headers = mutableMapOf(
            "Accept" to "*/*",
            "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8"
        )
        if (referer != null) {
            headers["Referer"] = referer
        }

        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)
        
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    // Extraer enlace directo libre de anuncios de forma asíncrona
    LaunchedEffect(embedUrl) {
        if (embedUrl != null) {
            isExtracting = true
            errorMessage = null
            val clean = VideoExtractor.extractCleanUrl(embedUrl)
            if (clean != null) {
                cleanUrl = clean
            } else {
                errorMessage = "No se pudo extraer el enlace de video. Por favor, intenta de nuevo o prueba con otro idioma."
            }
            isExtracting = false
        } else {
            isExtracting = false
            onBack() // Regresa si no hay URLs válidas
        }
    }

    // Preparar el reproductor nativo cuando la URL limpia esté disponible
    LaunchedEffect(cleanUrl) {
        cleanUrl?.let { url ->
            // Desencriptar / decodificar el proxy de Cloudflare Worker para reproducir DIRECTAMENTE a máxima velocidad
            val directUrl = if (url.contains("url=")) {
                val key = "url="
                val index = url.indexOf(key)
                if (index != -1) {
                    val encoded = url.substring(index + key.length)
                    try {
                        java.net.URLDecoder.decode(encoded, "UTF-8")
                    } catch (e: Exception) {
                        url
                    }
                } else url
            } else url

            val mediaItem = androidx.media3.common.MediaItem.fromUri(directUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    // Asegurar liberación de recursos al salir de la pantalla o si el player cambia
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    // Estados de reproducción nativos para sincronizar con controles de Compose
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var currentTime by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Interceptar los cambios de estado y errores de ExoPlayer
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                errorMessage = "Error de transmisión: ${error.localizedMessage ?: "Error en el servidor de video"}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Bucle para actualizar la barra de progreso en tiempo real
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentTime = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Ocultar controles automáticamente después de 3.5 segundos de inactividad
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isBuffering) {
            delay(3500)
            showControls = false
        }
    }

    // Captura de foco para interceptar botones del control remoto físico
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Manejador del botón "Atrás" del sistema
    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    showControls = true // Mostrar controles con cualquier interacción
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0L))
                            currentTime = player.currentPosition
                            true
                        }
                        Key.DirectionRight -> {
                            player.seekTo(
                                (player.currentPosition + 10000)
                                    .coerceAtLeast(0L)
                                    .coerceAtMost(player.duration)
                            )
                            currentTime = player.currentPosition
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        if (isExtracting) {
            // Animación premium mientras extrae el enlace directo
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Preparando enlace seguro...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (errorMessage != null) {
            // Mensaje de error premium
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Error de Reproducción".uppercase(),
                        color = Gold,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 500.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Presiona el botón VOLVER de tu control remoto para regresar",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Render del Reproductor de Video Nativo (Media3 PlayerView)
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false // Desactivar controles genéricos para usar Compose Premium
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // CAPA DE CONTROLES PREMIUM (Auto-ocultable)
            AnimatedVisibility(
                visible = showControls || isBuffering || !isPlaying,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Gradiente superior para info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                    )

                    // Título e Info (Esquina superior izquierda)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 48.dp, top = 32.dp)
                    ) {
                        Text(
                            text = movieTitle.uppercase(),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Reproduciendo ahora • Calidad HD",
                            color = Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Loader Premium de buffer en el centro
                    if (isBuffering) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(64.dp))
                        }
                    }

                    // Gradiente inferior para controles
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Barra de progreso y controles (Fila inferior)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 48.dp, end = 48.dp, bottom = 32.dp)
                    ) {
                        // Barra de progreso Dorada
                        val progressPercentage = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressPercentage)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Gold)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fila de controles y tiempos
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Tiempos (Actual / Duración)
                            Text(
                                text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Icono de estado (Reproduciendo / Pausado)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Retroceder 10s",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Gold.copy(alpha = 0.15f), CircleShape)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                        tint = Gold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Adelantar 10s",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Ayuda para control remoto
                            Text(
                                text = "◄◄ Adelantar/Retroceder   ● Pausar",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// Formateador auxiliar de tiempo en formato hh:mm:ss o mm:ss
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
