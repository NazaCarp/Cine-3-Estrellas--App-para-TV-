package com.cine3estrellas.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import android.os.Build

data class PlaybackErrorData(
    val friendlyTitle: String = "Error de Reproducción",
    val friendlyMessage: String,
    val errorCodeName: String? = null,
    val errorCode: Int? = null,
    val httpStatusCode: Int? = null,
    val streamUrl: String? = null,
    val embedUrl: String? = null,
    val movieTitle: String,
    val movieId: Int,
    val version: String,
    val causeMessage: String? = null,
    val exceptionClass: String? = null,
    val failedResourceUrl: String? = null,
    val playbackPosition: String? = null,
    val connectionType: String? = null,
    val deviceModel: String? = null
)

private fun formatMillisToTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSec = millis / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun getDeviceNetworkType(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Desconocido"
        val activeNetwork = cm.activeNetwork ?: return "Sin conexión"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Sin conexión"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles"
            else -> "Conectado"
        }
    } catch (_: Exception) {
        "Desconocido"
    }
}

private fun getDeviceDetailsString(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    val release = Build.VERSION.RELEASE
    val sdk = Build.VERSION.SDK_INT
    return "$manufacturer $model (Android $release - API $sdk)"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
    var errorData by remember { mutableStateOf<PlaybackErrorData?>(null) }

    val errorRetryRequester = remember { FocusRequester() }
    val errorBackRequester = remember { FocusRequester() }

    var isReconnecting by remember { mutableStateOf(false) }
    var lastSavedPlaybackPosition by remember { mutableStateOf(0L) }
    var lastPauseTimestamp by remember { mutableLongStateOf(0L) }
    var autoReconnectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var expiredUrlRecoveryAttempted by remember { mutableStateOf(false) }
    // 5xx sostenidos del CDN (522 de Cloudflare): conmutar a otro CDN candidato de la extracción
    var consecutive5xxFailures by remember { mutableIntStateOf(0) }
    var altCdnAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(errorData) {
        errorData?.let { data ->
            // 1. Log en Android Studio Logcat
            android.util.Log.e("VideoPlayerScreen", """
                =======================================================
                [DEBUG] ERROR DE REPRODUCCIÓN / EXTRACCIÓN
                Película: ${data.movieTitle} (ID: ${data.movieId})
                Versión: ${data.version}
                Error Code: ${data.errorCodeName} (${data.errorCode})
                HTTP Status: ${data.httpStatusCode}
                Embed URL: ${data.embedUrl}
                Stream URL: ${data.streamUrl}
                Recurso Fallido: ${data.failedResourceUrl}
                Posición: ${data.playbackPosition}
                Red: ${data.connectionType}
                Dispositivo Modelo: ${data.deviceModel}
                Causa: ${data.causeMessage} (${data.exceptionClass})
                =======================================================
            """.trimIndent())

            // 2. Auto-reporte silencioso a Telegram
            val deviceType = if (isTvDevice(context)) "Android TV" else "Celular Android"
            val devicePlatform = "$deviceType (v${BuildConfig.VERSION_NAME})"
            SupabaseManager.reportPlaybackErrorAsync(
                movieTitle = "${data.movieTitle} (ID: ${data.movieId})",
                version = data.version,
                server = data.embedUrl,
                streamUrl = data.streamUrl,
                errorCode = "${data.errorCodeName ?: "UNKNOWN"} (${data.errorCode ?: "-"})",
                httpStatus = data.httpStatusCode?.let { "HTTP $it" },
                errorDetail = data.causeMessage ?: data.friendlyMessage,
                user = DataCache.currentUser,
                device = devicePlatform,
                failedResourceUrl = data.failedResourceUrl,
                playbackPosition = data.playbackPosition,
                connectionType = data.connectionType,
                deviceModel = data.deviceModel
            )
        }
    }

    suspend fun saveProgress(movieId: Int, currentPositionMs: Long) {
        val userObj = DataCache.currentUser ?: return
        val currentPositionSec = currentPositionMs / 1000.0
        val nowIso = SupabaseManager.getFormattedTimestamp()
        val updatedProgress = userObj.watchProgress.toMutableMap().apply {
            if (currentPositionSec <= 0.0) {
                remove(movieId.toString())
            } else if (currentPositionSec >= 5.0) {
                put(movieId.toString(), WatchProgressVal(time = currentPositionSec, updatedAt = nowIso))
            }
        }

        val updatedUser = userObj.copy(watchProgress = updatedProgress)
        DataCache.saveSession(context, updatedUser)
        SupabaseManager.updateUserWatchProgress(userObj.telegramId, updatedProgress)
    }

    val retryPlayback: (resetPosition: Boolean) -> Unit = { resetPosition ->
        isExtracting = true
        errorMessage = null
        errorData = null
        cleanUrl = null
        expiredUrlRecoveryAttempted = false
        altCdnAttempted = false
        consecutive5xxFailures = 0
        showHistoryPrompt = false

        if (resetPosition) {
            lastSavedPlaybackPosition = 0L
            scope.launch {
                try {
                    saveProgress(movieId, 0L)
                } catch (_: Exception) {}
            }
        }

        embedUrl?.let { url ->
            VideoExtractor.invalidateCachedUrl(url)
        }

        scope.launch {
            val fresh = embedUrl?.let { VideoExtractor.extractCleanUrl(it, movieId, version, forceRefresh = true) }
            if (fresh != null) {
                cleanUrl = fresh
            } else {
                errorData = PlaybackErrorData(
                    friendlyTitle = "Error de Extracción",
                    friendlyMessage = "No se pudo obtener un nuevo enlace directo para este video.",
                    embedUrl = embedUrl,
                    movieTitle = movieTitle,
                    movieId = movieId,
                    version = version,
                    causeMessage = "Reintento fallido: el servidor proveedor no devolvió stream."
                )
                errorMessage = errorData?.friendlyMessage
            }
            isExtracting = false
        }
    }

    // Fallback a decodificador por software (patrón MovieBox: no confiar solo en el
    // decoder de hardware). Si el decoder OMX/c2 de hardware falla (errores 4001–4005),
    // se recrea el player usando los decoders por software de Android (c2.android.*).
    var softwareDecoderMode by remember { mutableIntStateOf(0) }

    // Inicializar ExoPlayer de forma segura con cabeceras HTTP optimizadas para evadir bloqueos
    val player = remember(embedUrl, softwareDecoderMode) {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        
        val referer = when {
            embedUrl == null -> null
            embedUrl.contains("ok.ru") || embedUrl.contains("odnoklassniki") -> "https://ok.ru/"
            embedUrl.contains("minochinos.com") -> "https://minochinos.com/"
            embedUrl.contains("tiktokshoppig.xyz") -> "https://tiktokshopping.xyz/"
            embedUrl.contains("callistanise.com") -> "https://callistanise.com/"
            embedUrl.contains("vidhidefast.com") -> "https://vidhidefast.com/"
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
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setKeepPostFor302Redirects(true)
            .setAllowCrossProtocolRedirects(true)

        // Los CDN frontados por Cloudflare (redirector.cdnsync.cloud, etc.) devuelven
        // 520/522/524 transitorios bajo carga. Sin reintentos, un solo 522 tumba la
        // reproducción aunque el enlace esté perfectamente funcional (hls.js del
        // navegador reintenta internamente; ExoPlayer no). Política: reintentar errores
        // HTTP 5xx y fallos de conexión/timeout hasta 8 veces con backoff antes de
        // rendirse; el búfer de 50–120 s absorbe la espera sin que el usuario lo note.
        val loadErrorPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(
                loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val ex = loadErrorInfo.exception
                if (ex is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    if (ex.responseCode >= 500) {
                        return if (loadErrorInfo.errorCount <= 8) {
                            (1000L * loadErrorInfo.errorCount)
                        } else {
                            androidx.media3.common.C.TIME_UNSET
                        }
                    }
                    return androidx.media3.common.C.TIME_UNSET // 4xx: no insistir, gestionar arriba
                }
                if (ex is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
                    // time-outs de conexión/lectura, sockets rotos
                    return if (loadErrorInfo.errorCount <= 8) 1500L else androidx.media3.common.C.TIME_UNSET
                }
                return super.getRetryDelayMsFor(loadErrorInfo)
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 8
        }

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        // Hosts de video gratuitos (callistanise, vidhide, etc.) limitan el ancho de banda
        // por conexión: si se arranca a máxima calidad, cada segmento tarda más en bajar
        // de lo que dura y la reproducción se traba cada pocos segundos.
        // Estrategia: calidad adaptativa que arranca bajo, tope de bitrate y un búfer
        // más grande para absorber los segmentos lentos.
        val isSlowHost = embedUrl != null && listOf(
            "callistanise.com", "minochinos.com", "morencius.com",
            "vidhidefast.com", "tiktokshopping.xyz"
        ).any { embedUrl.contains(it) }

        val initialBitrate = if (isSlowHost) 800_000L else 2_500_000L
        val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(initialBitrate)
            .build()

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            if (isSlowHost) {
                setParameters(buildUponParameters().setMaxVideoBitrate(1_800_000))
            }
        }

        val loadControl = if (isSlowHost) {
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    50_000,
                    120_000,
                    androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    6_000
                )
                .build()
        } else {
            androidx.media3.exoplayer.DefaultLoadControl()
        }

        // RenderersFactory con fallback de decoders habilitado y renderers de
        // extensión preferidos (si alguna vez se compila la extensión FFmpeg de
        // Media3, quedará disponible sin tocar código).
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        if (softwareDecoderMode > 0) {
            // Forzar decodificadores por software (c2.android.* / OMX.google.*):
            // los decoders de hardware de algunas TVs fallan con H.264 difíciles.
            renderersFactory.setMediaCodecSelector(object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requiresSecureDecoder: Boolean,
                    requiresTunneling: Boolean
                ): MutableList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val all = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                        mimeType, requiresSecureDecoder, requiresTunneling
                    )
                    val software = all.filter { !it.hardwareAccelerated }
                    return (if (software.isNotEmpty()) software else all).toMutableList()
                }
            })
        }

        androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }

    val togglePlayPause: () -> Unit = {
        if (player.isPlaying) {
            lastPauseTimestamp = System.currentTimeMillis()
            player.pause()
        } else {
            val pauseDurationMs = if (lastPauseTimestamp > 0L) System.currentTimeMillis() - lastPauseTimestamp else 0L
            if (pauseDurationMs > 2 * 60 * 1000L) {
                // Tras una pausa prolongada (> 2 minutos), el socket TCP con el CDN remoto queda inactivo
                // o cerrado por timeout. Un seek a la posición actual fuerza a ExoPlayer a descartar buffers
                // y reconectar de forma limpia antes de reanudar, previniendo errores de socket roto o NAL inválido.
                val resumePos = when {
                    player.currentPosition > 0L -> player.currentPosition
                    lastSavedPlaybackPosition > 0L -> lastSavedPlaybackPosition
                    else -> currentTime
                }
                if (resumePos > 0L) {
                    lastSavedPlaybackPosition = resumePos
                    player.seekTo(resumePos)
                }
            }
            lastPauseTimestamp = 0L
            player.play()
        }
    }

    val autoResumePlayback: () -> Unit = {
        if (isReconnecting || errorMessage != null) {
            autoReconnectJob?.cancel()
            autoReconnectJob = scope.launch {
                isReconnecting = true
                errorMessage = null
                errorData = null
                
                android.util.Log.d("VideoPlayerScreen", "Auto-resuming playback at position $lastSavedPlaybackPosition ms...")
                try {
                    player.prepare()
                    if (lastSavedPlaybackPosition > 0L) {
                        player.seekTo(lastSavedPlaybackPosition)
                    }
                    player.play()
                    delay(2000)
                    if (player.isPlaying || player.playbackState == androidx.media3.common.Player.STATE_READY) {
                        isReconnecting = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerScreen", "Direct prepare failed on network resume: ${e.message}")
                }
            }
        }
    }

    // Listener de conectividad del sistema Android para reanudar automáticamente apenas vuelva el Internet
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                if (isReconnecting || errorMessage != null) {
                    android.util.Log.d("VideoPlayerScreen", "Network connection re-established via NetworkCallback!")
                    scope.launch(Dispatchers.Main) {
                        autoResumePlayback()
                    }
                }
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                cm?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }

    // Extraer enlace directo libre de anuncios de forma asíncrona o usar caché de la base de datos
    LaunchedEffect(embedUrl) {
        if (embedUrl != null) {
            isExtracting = true
            errorMessage = null
            errorData = null
            
            val clean = VideoExtractor.extractCleanUrl(embedUrl, movieId, version)
            if (clean != null) {
                cleanUrl = clean
            } else {
                errorData = PlaybackErrorData(
                    friendlyTitle = "Error de Extracción",
                    friendlyMessage = "No se pudo extraer el enlace de video. El servidor proveedor no respondió o cambió su estructura.",
                    embedUrl = embedUrl,
                    movieTitle = movieTitle,
                    movieId = movieId,
                    version = version,
                    causeMessage = "El extractor de video no pudo resolver la URL reproducible a partir del embed."
                )
                errorMessage = errorData?.friendlyMessage
            }
            isExtracting = false
        } else {
            isExtracting = false
            onBack()
        }
    }

    // Preparar el reproductor nativo y buscar inmediatamente el progreso guardado para cargar el búfer desde allí.
    // Incluye softwareDecoderMode en la clave: al recrear el player en modo software
    // tras un fallo del decoder de hardware, se re-asigna el media y se reanuda en
    // la última posición conocida.
    LaunchedEffect(cleanUrl, softwareDecoderMode) {
        cleanUrl?.let { url ->
            val directUrl = VideoExtractor.normalizeStreamUrl(url)
            val isHls = VideoExtractor.isHlsStreamUrl(directUrl)
            val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                .setUri(directUrl)
            if (isHls) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            }
            val mediaItem = mediaItemBuilder.build()
            player.setMediaItem(mediaItem)
            
            // Buscar de inmediato al progreso actual o guardado si existe, para que comience a bufferizar desde allí
            val targetSeek = when {
                lastSavedPlaybackPosition > 0L -> lastSavedPlaybackPosition
                savedProgressSec > 5.0 -> (savedProgressSec * 1000).toLong()
                else -> 0L
            }
            if (targetSeek > 0L) {
                player.seekTo(targetSeek)
            }
            player.playWhenReady = !showHistoryPrompt
            player.prepare()
        }
    }

    var isExiting by remember { mutableStateOf(false) }

    fun handleExit() {
        if (isExiting) return
        isExiting = true
        scope.launch {
            try {
                val currentPos = player.currentPosition
                // No sobreescribir con 0 si el reproductor falló al iniciar o nunca llegó a reproducir
                val posToSave = when {
                    currentPos > 5000L -> currentPos
                    lastSavedPlaybackPosition > 5000L -> lastSavedPlaybackPosition
                    savedProgressSec > 5.0 -> (savedProgressSec * 1000).toLong()
                    else -> null
                }
                if (posToSave != null) {
                    saveProgress(movieId, posToSave)
                }
                
                val minutesWatched = ((System.currentTimeMillis() - startTime) / 60000).toInt()
                val userObj = DataCache.currentUser
                if (userObj != null && minutesWatched >= 1) {
                    SupabaseManager.logEvent(
                        DbEvent(
                            user_id = userObj.telegramId,
                            first_name = userObj.displayName,
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
                // Invalidar la sesión del stream en caché para que al reingresar se genere un token fresco
                embedUrl?.let { VideoExtractor.invalidateCachedUrl(it) }
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
                    val updatedUser = userObj.copy(watchHistory = newHistory)
                    
                    DataCache.saveSession(context, updatedUser)
                    SupabaseManager.updateUserWatchHistory(userObj.telegramId, newHistory)

                    SupabaseManager.logEvent(
                        DbEvent(
                            user_id = userObj.telegramId,
                            first_name = userObj.displayName,
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
            embedUrl?.let { VideoExtractor.invalidateCachedUrl(it) }
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

    var doubleTapSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(doubleTapTrigger) {
        if (doubleTapSide != null) {
            delay(650)
            doubleTapSide = null
        }
    }

    var isDraggingBar by remember { mutableStateOf(false) }
    var dragSeekPosition by remember { mutableLongStateOf(0L) }

    // Reproducción acelerada al mantener presionado un costado de la pantalla
    var speedBoostSide by remember { mutableStateOf<String?>(null) }
    // Último lado donde corrió el 2x: evita que el indicador "salte" al otro
    // lado de la pantalla durante su animación de salida al soltar el dedo
    var lastBoostSide by remember { mutableStateOf("right") }
    // Durante ~400 ms tras soltar el 2x se ignoran taps: sin esto el soltar se
    // interpreta como un tap simple y los controles aparecen/desaparecen solos
    var suppressTapUntil by remember { mutableLongStateOf(0L) }

    // Indicador flotante de ajuste (Brillo / Volumen) al hacer swipe vertical
    var adjustIndicator by remember { mutableStateOf<Triple<String, Float, androidx.compose.ui.graphics.vector.ImageVector>?>(null) }
    var adjustHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val activity = context as? android.app.Activity
    val audioManager = remember { context.getSystemService(android.media.AudioManager::class.java) }

    fun showAdjustIndicator(label: String, fraction: Float, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        adjustIndicator = Triple(label, fraction.coerceIn(0f, 1f), icon)
        adjustHideJob?.cancel()
        adjustHideJob = scope.launch {
            delay(900)
            adjustIndicator = null
        }
    }

    fun adjustBrightness(fracDelta: Float) {
        val act = activity ?: return
        val lp = act.window.attributes
        val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        val nv = (cur + fracDelta).coerceIn(0.02f, 1f)
        lp.screenBrightness = nv
        act.window.attributes = lp
        showAdjustIndicator("Brillo", nv, Icons.Default.BrightnessMedium)
    }

    // Nivel de volumen como fracción exacta (0..1): el AudioManager de Android
    // solo acepta pasos enteros (p. ej. 15 niveles), así que un swipe lento que
    // sumaba menos de 1 paso se perdía. Acumulando la fracción se siente fluido.
    var volumeLevel by remember { mutableFloatStateOf(-1f) }

    fun adjustVolume(fracDelta: Float) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        if (volumeLevel < 0f) {
            volumeLevel = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat()
        }
        volumeLevel = (volumeLevel + fracDelta).coerceIn(0f, 1f)
        val nv = (volumeLevel * max).roundToInt()
        if (nv != am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) {
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, nv, 0)
        }
        showAdjustIndicator("Volumen", volumeLevel, if (nv == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp)
    }

    // ---------- Gestos táctiles compartidos (video y capa de controles) ----------

    // Doble toque lateral: retrocede/adelanta 10s; en el centro, Play/Pause
    val handleDoubleTap: (Offset, Int) -> Unit = { offset, width ->
        val w = width.toFloat()
        if (offset.x < w * 0.45f) {
            val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
            player.seekTo(target)
            currentTime = target
            doubleTapSide = "left"
            doubleTapTrigger++
            showControls = true
        } else if (offset.x > w * 0.55f) {
            val target = (player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L))
            player.seekTo(target)
            currentTime = target
            doubleTapSide = "right"
            doubleTapTrigger++
            showControls = true
        } else {
            togglePlayPause()
            showControls = true
        }
    }

    // Gesto unificado sobre la pantalla:
    //  - Mantener quieto en un costado  → reproducción 2x mientras se mantiene
    //  - Swipe vertical en la izquierda → brillo
    //  - Swipe vertical en la derecha   → volumen
    val swipeOrBoostModifier = Modifier.pointerInput(player, duration > 0L) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            val onLeftSide = down.position.x < w * 0.45f
            val onRightSide = down.position.x > w * 0.55f
            val isLeftHalf = down.position.x < w / 2f
            val slop = viewConfiguration.touchSlop

            // Fase 1: esperar long-press quieto (2x) o inicio de arrastre (swipe);
            // si el dedo se levanta antes, es un tap y lo maneja detectTapGestures
            var isDrag = false
            var accX = 0f
            var accY = 0f
            val stillPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                    if (ch.changedToUpIgnoreConsumed()) return@withTimeoutOrNull
                    val d = ch.positionChangeIgnoreConsumed()
                    accX += d.x
                    accY += d.y
                    if (kotlin.math.abs(accX) + kotlin.math.abs(accY) > slop) {
                        isDrag = true
                        return@withTimeoutOrNull
                    }
                }
            }

            if (isDrag) {
                // Swipe vertical continuo: izquierda = brillo, derecha = volumen
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (ch.changedToUpIgnoreConsumed()) break
                    val d = ch.positionChangeIgnoreConsumed()
                    if (d.y != 0f) {
                        ch.consume()
                        // Un solo swipe de arriba a abajo recorre todo el rango (~0→100)
                        val frac = -d.y / (size.height.toFloat() * 0.85f)
                        if (isLeftHalf) adjustBrightness(frac) else adjustVolume(frac)
                    }
                }
                suppressTapUntil = System.currentTimeMillis() + 400
            } else if (stillPressed == null) {
                // Timeout con el dedo quieto → long-press lateral: 2x
                if (duration > 0L && !isReconnecting && (onLeftSide || onRightSide)) {
                    lastBoostSide = if (isLeftHalf) "left" else "right"
                    speedBoostSide = lastBoostSide
                    if (!isPlaying) player.play()
                    player.setPlaybackSpeed(2f)
                    showControls = false

                    // Esperar a que suelte el dedo (o se cancele el gesto)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.changedToUpIgnoreConsumed()) break
                        ch.consume()
                    }
                    player.setPlaybackSpeed(1f)
                    speedBoostSide = null
                    // Los controles siguen ocultos y el tap de "soltar el dedo" se ignora
                    showControls = false
                    suppressTapUntil = System.currentTimeMillis() + 400
                }
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
            lastSavedPlaybackPosition = 0L
            scope.launch {
                try {
                    saveProgress(movieId, 0L)
                } catch (_: Exception) {}
            }
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
                
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    if (isReconnecting) {
                        isReconnecting = false
                        autoReconnectJob?.cancel()
                    }
                }

                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    scope.launch {
                        val u = DataCache.currentUser
                        if (u != null) {
                            val updatedProgress = u.watchProgress.toMutableMap().apply {
                                remove(movieId.toString())
                            }
                            val updatedUser = u.copy(watchProgress = updatedProgress)
                            DataCache.saveSession(context, updatedUser)
                            SupabaseManager.updateUserWatchProgress(u.telegramId, updatedProgress)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isReconnecting = false
                    autoReconnectJob?.cancel()
                    consecutive5xxFailures = 0
                    altCdnAttempted = false
                } else {
                    lastPauseTimestamp = System.currentTimeMillis()
                    val pos = player.currentPosition
                    if (pos > 5000L) {
                        lastSavedPlaybackPosition = pos
                        scope.launch {
                            saveProgress(movieId, pos)
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                
                var httpStatus: Int? = null
                var causeMsg: String? = null
                var excClass: String? = null
                var failedUri: String? = null

                var currentCause: Throwable? = error.cause
                while (currentCause != null) {
                    if (currentCause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpStatus = currentCause.responseCode
                        if (failedUri == null) {
                            failedUri = currentCause.dataSpec.uri.toString()
                        }
                    }
                    if (causeMsg == null && !currentCause.message.isNullOrBlank()) {
                        causeMsg = currentCause.message
                        excClass = currentCause.javaClass.simpleName
                    }
                    currentCause = currentCause.cause
                }

                // --- Fallback a decodificador por software (patrón MovieBox) ---
                // Error 4001 (decoder init), 4003 (decoding failed), etc.: el decoder de
                // hardware del dispositivo se rindió. En lugar de mostrar error, recreamos
                // el player forzando c2.android.* y reanudamos en la última posición.
                val isDecoderFailure = error.errorCode in 4001..4005 ||
                    causeMsg?.contains("Decoder failed", ignoreCase = true) == true ||
                    causeMsg?.contains("DecoderInitException", ignoreCase = true) == true

                if (isDecoderFailure && softwareDecoderMode == 0) {
                    android.util.Log.w("VideoPlayerScreen", "Hardware decoder failed (${error.errorCodeName}). Switching to software decoder and resuming...")
                    val resumePos = when {
                        player.currentPosition > 0L -> player.currentPosition
                        lastSavedPlaybackPosition > 0L -> lastSavedPlaybackPosition
                        else -> currentTime
                    }
                    if (resumePos > 0L) {
                        lastSavedPlaybackPosition = resumePos
                    }
                    android.widget.Toast.makeText(
                        context,
                        "Reiniciando reproducción con decodificador alternativo…",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    softwareDecoderMode = 1 // Recrea el player (remember re-ejecuta) y LaunchedEffect re-prepara
                    return
                }

                val currentPos = player.currentPosition.takeIf { it > 0L } ?: lastSavedPlaybackPosition
                val totalDuration = player.duration.takeIf { it > 0L } ?: 0L
                val positionFormatted = buildString {
                    append(formatMillisToTime(currentPos))
                    if (totalDuration > 0L) {
                        append(" / ").append(formatMillisToTime(totalDuration))
                    }
                    if (showHistoryPrompt || (currentPos > 5000L && totalDuration == 0L)) {
                        append(" (Reanudando posición)")
                    } else if (currentPos <= 5000L) {
                        append(" (Al inicio)")
                    } else {
                        append(" (Durante reproducción)")
                    }
                }
                val connType = getDeviceNetworkType(context)
                val devDetails = getDeviceDetailsString()

                // Detectar si el fallo fue por corte o interrupción de conexión a internet
                val isNetworkDrop = !isNetworkAvailable(context) ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED && httpStatus == null) ||
                    causeMsg?.contains("timeout", ignoreCase = true) == true ||
                    causeMsg?.contains("connect", ignoreCase = true) == true ||
                    causeMsg?.contains("network", ignoreCase = true) == true ||
                    causeMsg?.contains("unable to resolve", ignoreCase = true) == true ||
                    causeMsg?.contains("socket", ignoreCase = true) == true ||
                    causeMsg?.contains("broken pipe", ignoreCase = true) == true ||
                    causeMsg?.contains("connection reset", ignoreCase = true) == true

                // Detectar si fue un error de desincronización o corte de socket a mitad de reproducción
                val isMidPlaybackError = (lastSavedPlaybackPosition > 0L || currentTime > 0L || player.currentPosition > 0L) &&
                    (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                     causeMsg?.contains("NAL", ignoreCase = true) == true ||
                     causeMsg?.contains("extractor", ignoreCase = true) == true ||
                     causeMsg?.contains("malformed", ignoreCase = true) == true)

                // Detectar si el token de sesión del stream expiró o fue rechazado como single-session (HTTP 403, 401 o 410)
                val isExpiredOrBlockedUrl = httpStatus == 403 || httpStatus == 401 || httpStatus == 410

                // El CDN (Cloudflare) devolvió 5xx (p. ej. 522 de redirector.cdnsync.cloud):
                // es transitorio, el enlace sigue siendo válido. Entrar en modo
                // auto-reconexión (re-prepare en bucle) en vez de mostrar error al usuario.
                val isServer5xx = httpStatus != null && httpStatus in 500..599
                if (isServer5xx) consecutive5xxFailures++

                val shouldAutoReconnect = isNetworkDrop || isMidPlaybackError ||
                    (isExpiredOrBlockedUrl && !expiredUrlRecoveryAttempted) ||
                    (isServer5xx && !isReconnecting)

                if (shouldAutoReconnect) {
                    android.util.Log.w("VideoPlayerScreen", "Auto-reconnectable playback interruption detected (networkDrop=$isNetworkDrop, midPlayback=$isMidPlaybackError, expiredUrl=$isExpiredOrBlockedUrl). Entering auto-reconnect mode...")
                    val savedPos = when {
                        player.currentPosition > 0L -> player.currentPosition
                        lastSavedPlaybackPosition > 0L -> lastSavedPlaybackPosition
                        savedProgressSec > 5.0 -> (savedProgressSec * 1000).toLong()
                        else -> currentTime
                    }
                    if (savedPos > 0L) {
                        lastSavedPlaybackPosition = savedPos
                    }
                    isReconnecting = true
                    errorMessage = null
                    errorData = null

                    autoReconnectJob?.cancel()
                    autoReconnectJob = scope.launch {
                        // 5xx que llega hasta aquí ya sobrevivió los 8 reintentos del
                        // loader (~40 s): el CDN elegido tiene segmentos caídos (522 de
                        // redirector.cdnsync.cloud). Vidhide sirve el mismo video en
                        // varios CDNs (hls4/hls3/hls2): conmutar al siguiente candidato
                        // y reanudar, en vez de reintentar en bucle el CDN muerto.
                        if (isServer5xx && !altCdnAttempted && embedUrl != null && cleanUrl != null) {
                            altCdnAttempted = true
                            android.util.Log.d("VideoPlayerScreen", "Sustained 5xx. Trying alternative CDN candidate...")
                            val alt = VideoExtractor.extractAlternativeCandidate(embedUrl, cleanUrl ?: "")
                            if (alt != null) {
                                cleanUrl = alt
                                if (userChoice == true || !showHistoryPrompt) {
                                    player.playWhenReady = true
                                }
                                delay(3000)
                                if (player.isPlaying || player.playbackState == androidx.media3.common.Player.STATE_READY) {
                                    android.util.Log.d("VideoPlayerScreen", "Recovered by switching to alternative CDN!")
                                    isReconnecting = false
                                    return@launch
                                }
                            }
                        }

                        // Si fue un error de token expirado o error a mitad de reproducción,
                        // invalidar de inmediato la URL en caché y re-extraer una URL fresca y limpia
                        if ((isMidPlaybackError || isExpiredOrBlockedUrl) && embedUrl != null) {
                            if (isExpiredOrBlockedUrl) {
                                expiredUrlRecoveryAttempted = true
                            }
                            android.util.Log.d("VideoPlayerScreen", "Invalidating cached URL and re-extracting fresh stream after $httpStatus/stream error...")
                            VideoExtractor.invalidateCachedUrl(embedUrl)
                            val fresh = VideoExtractor.extractCleanUrl(embedUrl, movieId, version, forceRefresh = true)
                            if (fresh != null) {
                                cleanUrl = fresh
                                if (userChoice == true || !showHistoryPrompt) {
                                    player.playWhenReady = true
                                }
                                delay(2000)
                                if (player.isPlaying || player.playbackState == androidx.media3.common.Player.STATE_READY) {
                                    android.util.Log.d("VideoPlayerScreen", "Seamlessly recovered from stream error with fresh URL!")
                                    isReconnecting = false
                                    return@launch
                                }
                            }
                        }

                        var retryCount = 0
                        // Reintentar en bucle cada 2.5s mientras dure la desconexión
                        while (isReconnecting && retryCount < 16) {
                            delay(2500)
                            retryCount++
                            if (isNetworkAvailable(context)) {
                                android.util.Log.d("VideoPlayerScreen", "Auto-reconnect attempt #$retryCount at pos: $lastSavedPlaybackPosition ms...")
                                try {
                                    player.prepare()
                                    if (lastSavedPlaybackPosition > 0L) {
                                        player.seekTo(lastSavedPlaybackPosition)
                                    }
                                    player.play()
                                    delay(2000)
                                    if (player.isPlaying || player.playbackState == androidx.media3.common.Player.STATE_READY) {
                                        android.util.Log.d("VideoPlayerScreen", "Auto-reconnect succeeded seamlessly!")
                                        isReconnecting = false
                                        break
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("VideoPlayerScreen", "Auto-reconnect exception: ${e.message}")
                                }
                            }
                        }

                        // Si tras reintentos no volvió o el token expiró, intentar re-extraer o mostrar pantalla de error
                        if (isReconnecting) {
                            if (isNetworkAvailable(context) && embedUrl != null) {
                                VideoExtractor.invalidateCachedUrl(embedUrl)
                                val fresh = VideoExtractor.extractCleanUrl(embedUrl, movieId, version, forceRefresh = true)
                                if (fresh != null) {
                                    cleanUrl = fresh
                                    isReconnecting = false
                                    return@launch
                                }
                            }

                            isReconnecting = false
                            errorData = PlaybackErrorData(
                                friendlyTitle = "Conexión Interrumpida",
                                friendlyMessage = "No se pudo restablecer la conexión al servidor de video tras varios intentos automáticos.",
                                errorCodeName = error.errorCodeName,
                                errorCode = error.errorCode,
                                httpStatusCode = httpStatus,
                                streamUrl = cleanUrl,
                                embedUrl = embedUrl,
                                movieTitle = movieTitle,
                                movieId = movieId,
                                version = version,
                                causeMessage = causeMsg ?: error.message,
                                exceptionClass = excClass ?: error.javaClass.simpleName,
                                failedResourceUrl = failedUri,
                                playbackPosition = positionFormatted,
                                connectionType = connType,
                                deviceModel = devDetails
                            )
                            errorMessage = "El video no se encuentra disponible temporalmente. Por favor, intenta de nuevo o prueba seleccionando otro idioma desde la ficha."
                        }
                    }
                    return
                }

                val friendly = when {
                    httpStatus == 403 -> "El servidor de video bloqueó el acceso (HTTP 403 Forbidden - Enlace expirado o token inválido)."
                    httpStatus == 404 -> "El archivo de video ya no existe en el servidor remoto (HTTP 404 Not Found)."
                    httpStatus != null && httpStatus >= 500 -> "El servidor de video remoto está caído o con fallas internas (HTTP $httpStatus)."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Códec de video no soportado por el hardware de este dispositivo."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "La lista de reproducción (.m3u8) o contenedor de video está corrupto."
                    else -> error.localizedMessage ?: "Falla de reproducción en el origen del video (Source error)."
                }
                
                // El enlace que falló puede seguir cacheado en memoria; si el servidor lo rechazó
                // (código HTTP) o el contenido no se pudo parsear como video, invalidar la caché
                // para que el próximo intento re-extracte en vez de reutilizar el enlace roto.
                val isUnplayable = httpStatus != null ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                if (isUnplayable && embedUrl != null) {
                    VideoExtractor.invalidateCachedUrl(embedUrl)
                }
                errorData = PlaybackErrorData(
                    friendlyTitle = "Error en Servidor de Video",
                    friendlyMessage = friendly,
                    errorCodeName = error.errorCodeName,
                    errorCode = error.errorCode,
                    httpStatusCode = httpStatus,
                    streamUrl = cleanUrl,
                    embedUrl = embedUrl,
                    movieTitle = movieTitle,
                    movieId = movieId,
                    version = version,
                    causeMessage = causeMsg ?: error.message,
                    exceptionClass = excClass ?: error.javaClass.simpleName,
                    failedResourceUrl = failedUri,
                    playbackPosition = positionFormatted,
                    connectionType = connType,
                    deviceModel = devDetails
                )
                errorMessage = friendly
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
        // Reintentar solicitud de foco para asegurar captura (abortar si aparece la pantalla
        // de error: el foco debe quedarse en los botones del overlay)
        repeat(10) {
            delay(100L)
            if (errorMessage != null) return@LaunchedEffect
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
                                                DataCache.saveSession(context, updatedUser)
                                                SupabaseManager.updateUserFavorites(u.telegramId, newFavorites)
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
                    } else if (errorMessage != null) {
                        // Pantalla de error activa: los únicos interactivos son los botones del
                        // overlay, así que no consumir las teclas (D-pad/OK) para que el foco pueda
                        // llegar a ellos y activarlos con el control remoto.
                        when (keyEvent.key) {
                            Key.Back -> {
                                handleExit()
                                true
                            }
                            else -> false
                        }
                    } else {
                        // Las teclas de volumen las maneja el sistema: dejarlas pasar sin revelar los controles
                        if (keyEvent.key == Key.VolumeUp || keyEvent.key == Key.VolumeDown || keyEvent.key == Key.VolumeMute) {
                            return@onPreviewKeyEvent false
                        }

                        val wasControlsHidden = !showControls
                        
                        // Pausa/Play siempre funciona al primer toque
                        if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.Spacebar) {
                            togglePlayPause()
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
                                    .clickable {
                                        selectedPromptOption = 0
                                        makeChoice(true)
                                    }
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
                                    .clickable {
                                        selectedPromptOption = 1
                                        makeChoice(false)
                                    }
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
            LaunchedEffect(errorMessage) {
                delay(200)
                try {
                    errorRetryRequester.requestFocus()
                } catch (_: Exception) {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Imagen de fondo desenfocada
                if (backdropImages.isNotEmpty()) {
                    AsyncImage(
                        model = backdropImages.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp)
                            .alpha(0.2f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.8f),
                                    Color(0xFF0C0E14).copy(alpha = 0.94f),
                                    Color.Black.copy(alpha = 0.98f)
                                )
                            )
                        )
                )

                // Tarjeta de Error Elegante para el Usuario
                Column(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .padding(horizontal = 24.dp)
                        .background(Color(0xFF141722), RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFEF4444).copy(alpha = 0.6f),
                                    Gold.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabecera: Icono, Título y Versión
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NO SE PUDO REPRODUCIR",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Gold.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = version.uppercase(),
                                        color = Gold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = movieTitle,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Mensaje comprensible y amigable para el usuario
                    Text(
                        text = "El video no se encuentra disponible temporalmente. Por favor, intenta de nuevo o prueba seleccionando otro idioma desde la ficha.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Botones de acción enfocables para TV y táctiles para móvil
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        var isRetryFocused by remember { mutableStateOf(false) }
                        var isBackFocused by remember { mutableStateOf(false) }

                        // Botón Reintentar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(errorRetryRequester)
                                .focusProperties { right = errorBackRequester }
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .focusable()
                                .touchable { retryPlayback(false) }
                                .background(
                                    if (isRetryFocused) Gold else Color(0xFF1E2230),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isRetryFocused) Gold else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { retryPlayback(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (isRetryFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "REINTENTAR",
                                    color = if (isRetryFocused) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Botón Volver
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(errorBackRequester)
                                .focusProperties { left = errorRetryRequester }
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .focusable()
                                .touchable { onBack() }
                                .background(
                                    if (isBackFocused) Gold else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isBackFocused) Gold else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = if (isBackFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VOLVER A LA FICHA",
                                    color = if (isBackFocused) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Render del Reproductor de Video Nativo con soporte de gestos táctiles
            // (Tap: mostrar controles; Doble Tap lateral: ±10s; mantener costado: 2x)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(duration, player) {
                        detectTapGestures(
                            onTap = {
                                if (System.currentTimeMillis() >= suppressTapUntil) {
                                    showControls = !showControls
                                }
                            },
                            onDoubleTap = { offset ->
                                handleDoubleTap(offset, size.width)
                            }
                        )
                    }
                    .then(swipeOrBoostModifier)
            ) {
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
                    // Si el player se recrea (fallback a decoder por software), reenganchar
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize()
                )

                // Indicador visual de Doble Tap Izquierdo (-10s)
                AnimatedVisibility(
                    visible = doubleTapSide == "left",
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.5.dp, Gold.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "-10s",
                                tint = Gold,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "-10s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Indicador visual de Doble Tap Derecho (+10s)
                AnimatedVisibility(
                    visible = doubleTapSide == "right",
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.5.dp, Gold.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "+10s",
                                tint = Gold,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "+10s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // Indicador de reproducción acelerada 2x (mantener presionado un costado)
                AnimatedVisibility(
                    visible = speedBoostSide != null,
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        // lastBoostSide: sin esto, al soltar el dedo el indicador
                        // "saltaba" al otro lado de la pantalla mientras desaparecía
                        .align(if ((speedBoostSide ?: lastBoostSide) == "left") Alignment.TopStart else Alignment.TopEnd)
                        .padding(top = 110.dp, start = 40.dp, end = 40.dp)
                        .zIndex(120f)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.5.dp, Gold.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2x",
                            color = Gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Indicador flotante de Brillo / Volumen (swipe vertical lateral)
                AnimatedVisibility(
                    visible = adjustIndicator != null,
                    enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-60).dp)
                        .zIndex(125f)
                ) {
                    adjustIndicator?.let { (label, fraction, icon) ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(1.5.dp, Gold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .width(190.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Gold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "${(fraction * 100).roundToInt()}%",
                                    color = Gold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // Minibarra de nivel
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Gold)
                                )
                            }
                        }
                    }
                }
            }
            val reconnectTransition = rememberInfiniteTransition(label = "reconnectBorder")
            val reconnectAngle by reconnectTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "reconnectAngle"
            )

            AnimatedVisibility(
                visible = isReconnecting,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(100f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Borde animado giratorio (Efecto de carga continua)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { rotationZ = reconnectAngle }
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Gold,
                                        Color(0xFFFFA000),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0xFFFFA000),
                                        Gold
                                    )
                                )
                            )
                    )

                    // Contenido interno con cápsula oscura
                    Row(
                        modifier = Modifier
                            .padding(1.8.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF0E111A).copy(alpha = 0.96f))
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Gold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Conexión interrumpida. Intentando restablecerla...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

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
                                .clickable {
                                    selectedLikeOption = 0
                                    val u = DataCache.currentUser
                                    if (u != null && movie != null) {
                                        val nf = if (u.favorites.any { it.id == movie.id }) u.favorites else u.favorites + FavoriteItem(id = movie.id, title = movie.title)
                                        val updated = u.copy(favorites = nf)
                                        DataCache.currentUser = updated
                                        DataCache.saveSession(context, updated)
                                        scope.launch {
                                            SupabaseManager.updateUserFavorites(u.telegramId, nf)
                                        }
                                    }
                                    showLikePrompt = false
                                }
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
                                .clickable {
                                    selectedLikeOption = 1
                                    showLikePrompt = false
                                }
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

            // CAPA DE CONTROLES PREMIUM (Auto-ocultable al pulsar en el espacio vacío)
            AnimatedVisibility(
                // No componer los controles mientras la pantalla de error está activa: sus
                // botones y la barra de seek son focusables y compiten por el foco del overlay
                visible = (showControls || isBuffering || !isPlaying) && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(duration, player) {
                            detectTapGestures(
                                onTap = {
                                    if (System.currentTimeMillis() >= suppressTapUntil) {
                                        showControls = false
                                    }
                                },
                                onDoubleTap = { offset ->
                                    handleDoubleTap(offset, size.width)
                                }
                            )
                        }
                        .then(swipeOrBoostModifier)
                ) {
                    // Gradiente superior cinematográfico para la info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.85f),
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Título e Info premium (Esquina superior izquierda)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 36.dp, top = 30.dp)
                    ) {
                        Text(
                            text = movieTitle.uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                        // Insignias: Idioma + Calidad
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Gold.copy(alpha = 0.16f))
                                    .border(1.dp, Gold.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = version.uppercase(),
                                    color = Gold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = selectedVersion?.formattedQuality ?: "HD",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }

                    // Animación de pulso en lugar del spinner central para el buffering
                    if (isBuffering && currentTime > 0) {
                        // El buffering ahora se representa con un efecto en la barra o simplemente se omite el spinner central
                    }

                    // Gradiente inferior cinematográfico para controles
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.97f)
                                    )
                                )
                            )
                    )

                    // Botones centrales (Solo visibles si no hay auto-reconexión)
                    if (!isReconnecting) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(44.dp)
                            ) {
                                // Retroceso -10s (vidrio oscuro con brillo al activarse)
                                val rewindScale by animateFloatAsState(
                                    targetValue = if (accumulatedSeekSeconds < 0) 1.18f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "rewindScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF0E111A).copy(alpha = 0.55f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                        .clip(CircleShape)
                                        .clickable {
                                            player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                                            doubleTapSide = "left"
                                            doubleTapTrigger++
                                        }
                                        .graphicsLayer {
                                            scaleX = rewindScale
                                            scaleY = rewindScale
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.FastRewind,
                                            contentDescription = "Retroceder 10s",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "10",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                // Play/Pause Central: vidrio dorado con halo luminoso
                                Box(
                                    modifier = Modifier.size(104.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Halo exterior
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        Gold.copy(alpha = 0.30f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .background(Color(0xFF0E111A).copy(alpha = 0.65f), CircleShape)
                                            .border(1.5.dp, Gold.copy(alpha = 0.9f), CircleShape)
                                            .clip(CircleShape)
                                            .clickable { togglePlayPause() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isBuffering) {
                                            CircularProgressIndicator(
                                                color = Gold,
                                                modifier = Modifier.size(34.dp),
                                                strokeWidth = 3.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Gold,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        }
                                    }
                                }

                                // Adelanto +10s (vidrio oscuro con brillo al activarse)
                                val forwardScale by animateFloatAsState(
                                    targetValue = if (accumulatedSeekSeconds > 0) 1.18f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "forwardScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF0E111A).copy(alpha = 0.55f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                        .clip(CircleShape)
                                        .clickable {
                                            player.seekTo((player.currentPosition + 10000L).coerceAtMost(player.duration))
                                            doubleTapSide = "right"
                                            doubleTapTrigger++
                                        }
                                        .graphicsLayer {
                                            scaleX = forwardScale
                                            scaleY = forwardScale
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = "Adelantar 10s",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "10",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Barra de progreso y controles (Fila inferior interactiva con arrastre/seek)
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 32.dp, end = 32.dp, bottom = 48.dp)
                    ) {
                        val maxWidth = constraints.maxWidth.toFloat()
                        
                        Column(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* consume click */ }
                        ) {
                            // Barra de progreso Dorada con Puntero (Thumb) y soporte de arrastre manual
                            val displayTime = if (isDraggingBar) dragSeekPosition else currentTime
                            val progressPercentage = if (duration > 0) (displayTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                            
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
                                    .height(48.dp)
                                    .pointerInput(duration) {
                                        detectTapGestures { offset ->
                                            if (duration > 0) {
                                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                                val targetMs = (fraction * duration).toLong()
                                                player.seekTo(targetMs)
                                                currentTime = targetMs
                                                showControls = true
                                            }
                                        }
                                    }
                                    .pointerInput(duration) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                if (duration > 0) {
                                                    isDraggingBar = true
                                                    isSeekingByUser = true
                                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                                    dragSeekPosition = (fraction * duration).toLong()
                                                    showControls = true
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                if (duration > 0) {
                                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    dragSeekPosition = (fraction * duration).toLong()
                                                    showControls = true
                                                }
                                            },
                                            onDragEnd = {
                                                if (duration > 0) {
                                                    player.seekTo(dragSeekPosition)
                                                    currentTime = dragSeekPosition
                                                }
                                                isSeekingByUser = false
                                                isDraggingBar = false
                                            },
                                            onDragCancel = {
                                                isSeekingByUser = false
                                                isDraggingBar = false
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // Track (Fondo) — rieles finos y discretos
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isDraggingBar) 7.dp else 5.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                )

                                // Active Track (gradiente dorado, con pulso si hay buffering)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressPercentage)
                                        .height(if (isDraggingBar) 7.dp else 5.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFFB8860B).copy(alpha = if (isBuffering) pulseAlpha else 1f),
                                                    Gold.copy(alpha = if (isBuffering) pulseAlpha else 1f)
                                                )
                                            )
                                        )
                                )

                                // Puntero (Thumb) dorado con halo sutil
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            val thumbHalf = (if (isDraggingBar) 9.dp else 7.dp).toPx().toInt()
                                            IntOffset(
                                                x = (progressPercentage * maxWidth).roundToInt() - thumbHalf,
                                                y = 0
                                            )
                                        }
                                        .size(if (isDraggingBar) 18.dp else 14.dp)
                                ) {
                                    // Halo
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Gold.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(if (isDraggingBar) 12.dp else 9.dp)
                                            .align(Alignment.Center)
                                            .background(Gold, CircleShape)
                                    )
                                }

                                // Indicador acumulativo flotante sobre el thumb
                                val floatOffset by animateDpAsState(
                                    targetValue = if (showAccumulatedIndicator || isDraggingBar) (-36).dp else (-20).dp,
                                    animationSpec = tween(1200, easing = LinearOutSlowInEasing),
                                    label = "floatOffset"
                                )
                                val floatAlpha by animateFloatAsState(
                                    targetValue = if (showAccumulatedIndicator || isDraggingBar) 1f else 0f,
                                    animationSpec = tween(1200),
                                    label = "floatAlpha"
                                )

                                if (accumulatedSeekSeconds != 0 || isDraggingBar) {
                                    val textToShow = if (isDraggingBar) formatTime(dragSeekPosition) else if (accumulatedSeekSeconds > 0) "+${accumulatedSeekSeconds}s" else "${accumulatedSeekSeconds}s"
                                    Text(
                                        text = textToShow,
                                        color = Gold,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    x = (progressPercentage * maxWidth).roundToInt() - 24.dp.toPx().toInt(),
                                                    y = floatOffset.toPx().toInt()
                                                )
                                            }
                                            .alpha(floatAlpha)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Fila de tiempos (limpio, sin textos de ayuda ruidosos)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatTime(displayTime),
                                        color = Gold,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "  /  ${formatTime(duration)}",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "Doble toque ±10s • Mantener 2x",
                                    color = Color.White.copy(alpha = 0.30f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp
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
