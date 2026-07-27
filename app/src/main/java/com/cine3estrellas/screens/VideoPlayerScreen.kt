package com.cine3estrellas.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.cine3estrellas.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val user = DataCache.currentUser
    val savedProgressSec = remember(movieId, user) {
        user?.watchProgress?.get(movieId.toString())?.time ?: 0.0
    }
    val inHistory = remember(movieId, user) {
        user?.watchHistory?.contains(movieId) == true
    }
    val shouldShowPromptInitially = inHistory && savedProgressSec > 5.0
    var showHistoryPrompt by remember { mutableStateOf(shouldShowPromptInitially) }
    var selectedPromptOption by remember { mutableIntStateOf(0) } // 0 = Continuar, 1 = Comenzar de 0
    var userChoice by remember { mutableStateOf<Boolean?>(null) } // true = resume, false = restart

    // Control de "¿Te gustó la película?" para agregar a favoritos
    var showLikePrompt by remember { mutableStateOf(false) }
    var hasShownLikePrompt by remember { mutableStateOf(false) }
    var selectedLikeOption by remember { mutableIntStateOf(0) } // 0 = Sí, 1 = No

    // Estados de reproducción nativos para sincronizar con controles de Compose
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var currentTime by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Estados para búsqueda virtual acelerada (D-pad holding)
    var isSeekingByUser by remember { mutableStateOf(false) }
    var virtualTime by remember { mutableLongStateOf(0L) }
    var seekClickCount by remember { mutableIntStateOf(0) }
    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    var showAccumulatedIndicator by remember { mutableStateOf(false) }

    val backdropImages = remember { mutableStateListOf<String>() }
    val startTime = remember { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    // Obtener imágenes de fondo de la película en reproducción a través de TMDB
    LaunchedEffect(movieId, movie) {
        val list = mutableListOf<String>()
        movie?.backdrop_path?.let { list.add("https://image.tmdb.org/t/p/original$it") }
        backdropImages.clear()
        backdropImages.addAll(list)
        
        try {
            val tmdbBackdrops = TmdbManager.getMovieImages(movieId)
            val fullUrls = tmdbBackdrops.map { "https://image.tmdb.org/t/p/original$it" }
            val combined = (list + fullUrls).distinct()
            backdropImages.clear()
            backdropImages.addAll(combined)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val movieTitle = movie?.title ?: "Película"

    // Obtener la URL de reproducción del embed original
    val versions = movie?.versions
    val selectedVersion = versions?.get(version) ?: versions?.values?.firstOrNull()
    val embedUrl = selectedVersion?.url

    var cleanUrl by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wasUsingDbCache by remember { mutableStateOf(false) }

    // Inicializar ExoPlayer de forma segura con cabeceras HTTP optimizadas para evadir bloqueos
    val player = remember(embedUrl) {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        
        val referer = when {
            embedUrl == null -> null
            embedUrl.contains("ok.ru") || embedUrl.contains("odnoklassniki") -> "https://ok.ru/"
            embedUrl.contains("minochinos.com") -> "https://minochinos.com/"
            embedUrl.contains("tiktokshoppig.xyz") -> "https://tiktokshopping.xyz/"
            embedUrl.contains("callistanise.com") -> "https://callistanise.com/"
            embedUrl.contains("morencius.com") -> "https://morencius.com/"
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
            .build()
    }

    // Extraer enlace directo libre de anuncios de forma asíncrona o usar caché de la base de datos
    LaunchedEffect(embedUrl) {
        if (embedUrl != null) {
            isExtracting = true
            errorMessage = null
            
            val currentCleanUrl = selectedVersion?.cleanUrl
            val currentExpiresAt = selectedVersion?.cleanUrlExpiresAt
            if (!currentCleanUrl.isNullOrBlank() && currentExpiresAt != null && currentExpiresAt > System.currentTimeMillis() + 120000L) {
                android.util.Log.d("VideoPlayerScreen", "Using cached cleanUrl from database: $currentCleanUrl")
                cleanUrl = currentCleanUrl
                wasUsingDbCache = true
                isExtracting = false
            } else {
                wasUsingDbCache = false
                val clean = VideoExtractor.extractCleanUrl(embedUrl, movieId, version)
                if (clean != null) {
                    cleanUrl = clean
                } else {
                    errorMessage = "No se pudo extraer el enlace de video. Por favor, intenta de nuevo o prueba con otro idioma."
                }
                isExtracting = false
            }
        } else {
            isExtracting = false
            onBack()
        }
    }

    // Preparar el reproductor nativo y buscar inmediatamente el progreso guardado para cargar el búfer desde allí
    LaunchedEffect(cleanUrl) {
        cleanUrl?.let { url ->
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

            val isHls = directUrl.contains(".m3u8", ignoreCase = true) || directUrl.contains("/hls/", ignoreCase = true)
            val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                .setUri(directUrl)
            if (isHls) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            }
            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            
            // Buscar de inmediato al historial si existe, para que comience a bufferizar desde allí mientras se muestra el modal
            if (savedProgressSec > 5.0) {
                player.seekTo((savedProgressSec * 1000).toLong())
            }
            player.playWhenReady = !showHistoryPrompt
            player.prepare()
        }
    }

    var isExiting by remember { mutableStateOf(false) }

    suspend fun saveProgress(movieId: Int, currentPositionMs: Long) {
        val userObj = DataCache.currentUser ?: return
        val currentPositionSec = currentPositionMs / 1000.0
        if (currentPositionSec < 5.0) return

        val nowIso = SupabaseManager.getFormattedTimestamp()
        val updatedProgress = userObj.watchProgress.toMutableMap().apply {
            put(movieId.toString(), WatchProgressVal(time = currentPositionSec, updatedAt = nowIso))
        }

        val updatedUser = userObj.copy(watchProgress = updatedProgress)
        val savedUser = SupabaseManager.upsertUser(updatedUser)
        if (savedUser != null) {
            DataCache.currentUser = savedUser
        }
    }

    fun handleExit() {
        if (isExiting) return
        isExiting = true
        scope.launch {
            try {
                val currentPos = player.currentPosition
                saveProgress(movieId, currentPos)
                
                val minutesWatched = ((System.currentTimeMillis() - startTime) / 60000).toInt()
                val userObj = DataCache.currentUser
                if (userObj != null) {
                    SupabaseManager.logEvent(
                        DbEvent(
                            user_id = userObj.telegramId,
                            first_name = userObj.firstName,
                            event_name = "▶️ Vista ($minutesWatched min)",
                            movie_id = movieId.toLong(),
                            movie_title = movieTitle,
                            language = version
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onBack()
            }
        }
    }

    LaunchedEffect(cleanUrl) {
        if (cleanUrl != null) {
            val userObj = DataCache.currentUser
            if (userObj != null) {
                scope.launch {
                    val currentHistory = userObj.watchHistory.filter { it != movieId }
                    val newHistory = (listOf(movieId) + currentHistory).take(40)
                    val newHidden = userObj.hiddenHistory.filter { it != movieId }
                    val updatedUser = userObj.copy(watchHistory = newHistory, hiddenHistory = newHidden)
                    val savedUser = SupabaseManager.upsertUser(updatedUser)
                    if (savedUser != null) {
                        DataCache.currentUser = savedUser
                    }
                    SupabaseManager.logEvent(
                        DbEvent(
                            user_id = userObj.telegramId,
                            first_name = userObj.firstName,
                            event_name = "▶️ Vista",
                            movie_id = movieId.toLong(),
                            movie_title = movieTitle,
                            language = version
                        )
                    )
                }
            }
        }
    }

    // Asegurar liberación de recursos al salir de la pantalla o si el player cambia
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    // Temporizador de 10 segundos para el prompt de historial
    LaunchedEffect(showHistoryPrompt) {
        if (showHistoryPrompt) {
            delay(10000)
            if (showHistoryPrompt) {
                showHistoryPrompt = false
                player.playWhenReady = true
                player.play()
            }
        }
    }

    fun makeChoice(resume: Boolean) {
        userChoice = resume
        showHistoryPrompt = false
        if (resume) {
            player.playWhenReady = true
            player.play()
        } else {
            player.seekTo(0L)
            player.playWhenReady = true
            player.play()
        }
    }

    // Lógica para desvanecer el indicador acumulativo y aplicar el seek real
    LaunchedEffect(lastSeekTime) {
        if (lastSeekTime > 0) {
            showAccumulatedIndicator = true
            delay(800) // Tiempo de espera para detectar si el usuario deja de presionar
            if (System.currentTimeMillis() - lastSeekTime >= 800) {
                player.seekTo(virtualTime)
                currentTime = virtualTime
                isSeekingByUser = false
                showAccumulatedIndicator = false
                accumulatedSeekSeconds = 0
                seekClickCount = 0
            }
        }
    }

    // Interceptar los cambios de estado y errores de ExoPlayer
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0L)
                
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    scope.launch {
                        val u = DataCache.currentUser
                        if (u != null) {
                            val updatedProgress = u.watchProgress.toMutableMap().apply {
                                remove(movieId.toString())
                            }
                            val updatedUser = u.copy(watchProgress = updatedProgress)
                            val savedUser = SupabaseManager.upsertUser(updatedUser)
                            if (savedUser != null) {
                                DataCache.currentUser = savedUser
                            }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    val pos = player.currentPosition
                    scope.launch {
                        saveProgress(movieId, pos)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                
                if (wasUsingDbCache && embedUrl != null) {
                    android.util.Log.w("VideoPlayerScreen", "Cached DB URL failed playback. Retrying with fresh extraction...")
                    wasUsingDbCache = false
                    isExtracting = true
                    errorMessage = null
                    
                    // Clear local memory cache
                    DataCache.extractedUrlsMap.remove(embedUrl)
                    
                    scope.launch {
                        // Clear database cache in Supabase
                        SupabaseManager.clearMovieVersionCleanUrl(movieId, version)
                        
                        // Force a fresh extraction
                        val clean = VideoExtractor.extractCleanUrl(embedUrl, movieId, version)
                        if (clean != null) {
                            cleanUrl = clean
                        } else {
                            errorMessage = "Error de transmisión: ${error.localizedMessage ?: "Error en el servidor de video"}"
                        }
                        isExtracting = false
                    }
                } else {
                    errorMessage = "Error de transmisión: ${error.localizedMessage ?: "Error en el servidor de video"}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Bucle para actualizar la barra de progreso en tiempo real y chequear el prompt de favoritos
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isSeekingByUser) {
                currentTime = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
            }
            
            // Si faltan menos de 5 minutos para terminar el video, y ha visto más de la mitad
            if (duration > 0 && duration - currentTime <= 5 * 60 * 1000 && currentTime > duration / 2 && !hasShownLikePrompt) {
                showLikePrompt = true
                hasShownLikePrompt = true
            }
            
            delay(500)
        }
    }

    // Guardado de progreso periódico en Supabase cada 60 segundos de reproducción activa
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(60000)
            if (isPlaying) {
                val currentPos = player.currentPosition
                scope.launch {
                    saveProgress(movieId, currentPos)
                }
            }
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
        // Reintentar solicitud de foco para asegurar captura
        repeat(10) {
            delay(100L)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Manejador del botón "Atrás" del sistema
    BackHandler {
        handleExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (showHistoryPrompt) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                selectedPromptOption = 0
                                true
                            }
                            Key.DirectionRight -> {
                                selectedPromptOption = 1
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                makeChoice(selectedPromptOption == 0)
                                true
                            }
                            Key.Back -> {
                                handleExit()
                                true
                            }
                            else -> false
                        }
                    } else if (showLikePrompt) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                selectedLikeOption = 0
                                true
                            }
                            Key.DirectionRight -> {
                                selectedLikeOption = 1
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                val liked = selectedLikeOption == 0
                                if (liked) {
                                    scope.launch {
                                        val u = DataCache.currentUser
                                        if (u != null) {
                                            val alreadyFavorite = u.favorites.any { it.id == movieId }
                                            if (!alreadyFavorite) {
                                                val newFavorites = u.favorites + FavoriteItem(id = movieId, title = movieTitle)
                                                val updatedUser = u.copy(favorites = newFavorites)
                                                val savedUser = SupabaseManager.upsertUser(updatedUser)
                                                if (savedUser != null) {
                                                    DataCache.currentUser = savedUser
                                                }
                                            }
                                        }
                                    }
                                }
                                showLikePrompt = false
                                true
                            }
                            Key.Back -> {
                                showLikePrompt = false
                                true
                            }
                            else -> false
                        }
                    } else {
                        val wasControlsHidden = !showControls
                        
                        // Pausa/Play siempre funciona al primer toque
                        if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.Spacebar) {
                            if (player.isPlaying) player.pause() else player.play()
                            showControls = true
                            return@onPreviewKeyEvent true
                        }

                        if (wasControlsHidden) {
                            showControls = true
                            return@onPreviewKeyEvent true
                        }
                        
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                val now = System.currentTimeMillis()
                                val isRapid = (now - lastSeekTime) < 600
                                val clickCount = if (isRapid) seekClickCount + 1 else 1
                                seekClickCount = clickCount

                                if (!isSeekingByUser) {
                                    isSeekingByUser = true
                                    virtualTime = currentTime
                                }

                                val stepMs = when {
                                    clickCount <= 10 -> 10_000L   // 10s
                                    clickCount <= 25 -> 30_000L   // 30s
                                    clickCount <= 50 -> 60_000L   // 1m
                                    clickCount <= 80 -> 120_000L  // 2m
                                    else -> 300_000L             // 5m
                                }

                                virtualTime = (virtualTime - stepMs).coerceAtLeast(0L)
                                accumulatedSeekSeconds = ((virtualTime - player.currentPosition) / 1000).toInt()
                                lastSeekTime = now
                                currentTime = virtualTime
                                showControls = true
                                true
                            }
                            Key.DirectionRight -> {
                                val now = System.currentTimeMillis()
                                val isRapid = (now - lastSeekTime) < 600
                                val clickCount = if (isRapid) seekClickCount + 1 else 1
                                seekClickCount = clickCount

                                if (!isSeekingByUser) {
                                    isSeekingByUser = true
                                    virtualTime = currentTime
                                }

                                val stepMs = when {
                                    clickCount <= 10 -> 10_000L   // 10s
                                    clickCount <= 25 -> 30_000L   // 30s
                                    clickCount <= 50 -> 60_000L   // 1m
                                    clickCount <= 80 -> 120_000L  // 2m
                                    else -> 300_000L             // 5m
                                }

                                virtualTime = (virtualTime + stepMs).coerceAtMost(duration)
                                accumulatedSeekSeconds = ((virtualTime - player.currentPosition) / 1000).toInt()
                                lastSeekTime = now
                                currentTime = virtualTime
                                showControls = true
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown -> {
                                showControls = true
                                true
                            }
                            Key.Back -> {
                                handleExit()
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
            .focusable()
    ) {
        // Fondo de imágenes persistente (Carga y Buffering inicial)
        val showBackgroundCarousel = isExtracting || (isBuffering && currentTime == 0L) || showHistoryPrompt
        
        if (showBackgroundCarousel) {
            // Pantalla de carga cinematográfica (Persistente hasta que empiece el video)
            Box(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                // Base estática con la imagen de fondo ya descargada/cacheada para evitar pantallas negras
                if (movie?.backdrop_path != null) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/original${movie.backdrop_path}",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (backdropImages.isNotEmpty()) {
                    var currentImageIndex by remember { mutableIntStateOf(0) }
                    
                    // Ciclo de imágenes acelerado (3 segundos por imagen)
                    LaunchedEffect(backdropImages.size) {
                        if (backdropImages.size > 1) {
                            while (true) {
                                delay(3000)
                                currentImageIndex = (currentImageIndex + 1) % backdropImages.size
                            }
                        }
                    }

                    // Transición Ken Burns suave
                    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
                    val animTranslationX by infiniteTransition.animateFloat(
                        initialValue = -50f,
                        targetValue = 50f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 10000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "animTranslationX"
                    )

                    AnimatedContent(
                        targetState = backdropImages[currentImageIndex],
                        transitionSpec = {
                            fadeIn(tween(1500)) togetherWith fadeOut(tween(1500))
                        },
                        label = "loadingImage"
                    ) { imageUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.2f
                                    scaleY = 1.2f
                                    translationX = animTranslationX
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Overlay oscuro para que el texto resalte
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

                if (showHistoryPrompt) {
                    // Diseño premium del prompt
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F0F0F).copy(alpha = 0.9f))
                            .border(2.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(32.dp)
                            .width(420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "¿Deseas continuar viendo?",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Se encontró registro en tu historial a los ${formatTime((savedProgressSec * 1000).toLong())}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Botón Continuar
                            val isResumeSelected = selectedPromptOption == 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        if (isResumeSelected) Gold else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isResumeSelected) Gold else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Continuar",
                                    color = if (isResumeSelected) Color.Black else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Botón Comenzar de 0
                            val isRestartSelected = selectedPromptOption == 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(
                                        if (isRestartSelected) Gold else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isRestartSelected) Gold else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Comenzar de 0",
                                    color = if (isRestartSelected) Color.Black else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Carga normal
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (isExtracting) "Preparando película..." else "Cargando video...",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = movieTitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
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
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay de "¿Te gustó la película?"
            AnimatedVisibility(
                visible = showLikePrompt,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .zIndex(60f)
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F0F0F).copy(alpha = 0.95f))
                        .border(2.dp, Gold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                        .width(360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "¿Te gustó la película?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Selecciona 'Sí' para guardarla en tus favoritos",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Botón Sí
                        val isYesSelected = selectedLikeOption == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(
                                    if (isYesSelected) Gold else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isYesSelected) Gold else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sí",
                                color = if (isYesSelected) Color.Black else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Botón No
                        val isNoSelected = selectedLikeOption == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(
                                    if (isNoSelected) Gold else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isNoSelected) Gold else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No",
                                color = if (isNoSelected) Color.Black else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

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
                            .padding(start = 32.dp, top = 32.dp)
                    ) {
                        Text(
                            text = movieTitle.uppercase(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Reproduciendo ahora • Calidad ${selectedVersion?.formattedQuality ?: "HD"}",
                            color = Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Animación de pulso en lugar del spinner central para el buffering
                    if (isBuffering && currentTime > 0) {
                        // El buffering ahora se representa con un efecto en la barra o simplemente se omite el spinner central
                    }

                    // Gradiente inferior para controles
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                )
                            )
                    )

                    // No hay indicadores fijos en el centro. Se usan indicadores sobre el thumb.

                    // Botón Central de Play/Pause (Perfectamente centrado)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            // Atrasar con animación
                            val rewindScale by animateFloatAsState(
                                targetValue = if (accumulatedSeekSeconds < 0) 1.3f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "rewindScale"
                            )
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = rewindScale
                                        scaleY = rewindScale
                                    }
                            )

                            // Play/Pause Central
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Gold.copy(alpha = 0.2f), CircleShape)
                                    .border(2.dp, Gold.copy(alpha = 0.4f), CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBuffering) {
                                    CircularProgressIndicator(
                                        color = Gold,
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Gold,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            // Adelantar con animación
                            val forwardScale by animateFloatAsState(
                                targetValue = if (accumulatedSeekSeconds > 0) 1.3f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "forwardScale"
                            )
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer {
                                        scaleX = forwardScale
                                        scaleY = forwardScale
                                    }
                            )
                        }
                    }

                    // Barra de progreso y controles (Fila inferior)
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
                    ) {
                        val maxWidth = constraints.maxWidth.toFloat()
                        
                        Column {
                            // Barra de progreso Dorada con Puntero (Thumb)
                            val progressPercentage = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f
                            
                            // Animación de pulso para buffering
                            val infiniteTransition = rememberInfiniteTransition(label = "bufferingPulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp), // Area mayor para el indicador flotante
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Track (Fondo)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                
                                // Active Track (con pulso si hay buffering)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressPercentage)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isBuffering) Gold.copy(alpha = pulseAlpha) else Gold)
                                )

                                // Puntero (Thumb)
                                Box(
                                    modifier = Modifier
                                        .offset { 
                                            IntOffset(
                                                x = (progressPercentage * maxWidth).roundToInt() - 6.dp.toPx().toInt(),
                                                y = 0
                                            )
                                        }
                                        .size(12.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, Gold, CircleShape)
                                )

                                // Indicador acumulativo flotante sobre el thumb
                                val floatOffset by animateDpAsState(
                                    targetValue = if (showAccumulatedIndicator) (-36).dp else (-20).dp,
                                    animationSpec = tween(1200, easing = LinearOutSlowInEasing),
                                    label = "floatOffset"
                                )
                                val floatAlpha by animateFloatAsState(
                                    targetValue = if (showAccumulatedIndicator) 1f else 0f,
                                    animationSpec = tween(1200),
                                    label = "floatAlpha"
                                )

                                if (accumulatedSeekSeconds != 0) {
                                    Text(
                                        text = if (accumulatedSeekSeconds > 0) "+${accumulatedSeekSeconds}s" else "${accumulatedSeekSeconds}s",
                                        color = Gold,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    x = (progressPercentage * maxWidth).roundToInt() - 20.dp.toPx().toInt(),
                                                    y = floatOffset.toPx().toInt()
                                                )
                                            }
                                            .alpha(floatAlpha)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Fila de tiempos y atajos
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "◄ Adelantar / Retroceder ►",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
