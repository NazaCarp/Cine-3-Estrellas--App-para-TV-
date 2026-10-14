package com.cine3estrellas.mobile.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.cine3estrellas.DataCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cine3estrellas.SupabaseManager
import com.cine3estrellas.VideoExtractor
import com.cine3estrellas.mobile.Gold
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun MobileVideoPlayerScreen(movieId: Int, version: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var embedUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // Fallback a decoder por software (patrón MovieBox) si el decoder de hardware falla
    var softwareDecoderMode by remember { mutableIntStateOf(0) }
    var fallbackResumePos by remember { mutableLongStateOf(0L) }
    // Recuperación silenciosa ante 5xx persistentes del CDN (p. ej. 522 de Cloudflare)
    var serverRecoveryAttempts by remember { mutableIntStateOf(0) }
    var altCdnAttempted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(movieId, version) {
        // Fetch movie details to get the embed URL
        try {
            val movieDetails = SupabaseManager.client.from("movies")
                .select { 
                    filter {
                        eq("id", movieId)
                    }
                }
                .decodeSingleOrNull<com.cine3estrellas.Movie>()
            
            val versionsMap = movieDetails?.versions ?: emptyMap()
            val targetVersion = versionsMap[version]
                ?: versionsMap.entries.firstOrNull { it.key.equals(version, ignoreCase = true) }?.value
                ?: versionsMap.values.firstOrNull()
            
            val targetEmbedUrl = targetVersion?.url
            embedUrl = targetEmbedUrl
            if (targetEmbedUrl != null) {
                videoUrl = VideoExtractor.extractCleanUrl(targetEmbedUrl, movieId, version)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoading = false
    }

    if (isLoading || videoUrl == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator(color = Gold)
            else androidx.compose.material3.Text("No se pudo extraer el video", color = Color.White)
        }
    } else {
        val exoPlayer = remember(videoUrl, softwareDecoderMode) {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            val referer = when {
                embedUrl == null -> null
                embedUrl!!.contains("ok.ru") || embedUrl!!.contains("odnoklassniki") -> "https://ok.ru/"
                embedUrl!!.contains("minochinos.com") -> "https://minochinos.com/"
                embedUrl!!.contains("tiktokshoppig.xyz") -> "https://tiktokshopping.xyz/"
                embedUrl!!.contains("callistanise.com") -> "https://callistanise.com/"
                embedUrl!!.contains("vidhidefast.com") -> "https://vidhidefast.com/"
                embedUrl!!.contains("morencius.com") -> "https://morencius.com/"
                embedUrl!!.contains("vidsonic") -> "https://vidsonic.net/"
                embedUrl!!.contains("vidmoly") -> "https://vidmoly.to/"
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
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
                    // Los CDN frontados por Cloudflare (redirector.cdnsync.cloud) devuelven
                    // 520/522/524 transitorios: reintentar con backoff antes de rendirse.
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
                            return androidx.media3.common.C.TIME_UNSET
                        }
                        if (ex is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
                            return if (loadErrorInfo.errorCount <= 8) 1500L else androidx.media3.common.C.TIME_UNSET
                        }
                        return super.getRetryDelayMsFor(loadErrorInfo)
                    }

                    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 8
                })

            // Hosts lentos (callistanise, vidhide, etc.): ABR arrancando bajo,
            // tope de bitrate y búfer grande para evitar trabas cada pocos segundos.
            val isSlowHost = embedUrl != null && listOf(
                "callistanise.com", "minochinos.com", "morencius.com",
                "vidhidefast.com", "tiktokshopping.xyz"
            ).any { embedUrl!!.contains(it) }

            val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(if (isSlowHost) 800_000L else 2_500_000L)
                .build()

            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
                if (isSlowHost) {
                    setParameters(buildUponParameters().setMaxVideoBitrate(1_800_000))
                }
            }

            val loadControl = if (isSlowHost) {
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(50_000, 120_000, androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, 6_000)
                    .build()
            } else {
                androidx.media3.exoplayer.DefaultLoadControl()
            }

            // RenderersFactory con fallback de decoders habilitado y extensión preferida
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            if (softwareDecoderMode > 0) {
                // Forzar decoders por software (c2.android.*) tras un fallo del de hardware
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

            ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setBandwidthMeter(bandwidthMeter)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build().apply {
                    val url = videoUrl!!
                    // Forzar el MIME de HLS cuando corresponde: ExoPlayer no infiere HLS de
                    // playlists sin extensión .m3u8 (p. ej. los master.txt de vidhide)
                    val mediaItem = if (VideoExtractor.isHlsStreamUrl(url)) {
                        MediaItem.Builder()
                            .setUri(url)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    } else {
                        MediaItem.fromUri(url)
                    }
                    setMediaItem(mediaItem)
                    // Si venimos del fallback a software o de conmutar de CDN, reanudar donde quedó
                    if (fallbackResumePos > 0L) {
                        seekTo(fallbackResumePos)
                    }
                    prepare()
                    playWhenReady = true
                }
        }

        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        serverRecoveryAttempts = 0
                        altCdnAttempted = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    var httpStatus: Int? = null
                    var cause: Throwable? = error.cause
                    var failedUri: String? = null
                    var causeMsg: String? = null
                    while (cause != null) {
                        if (cause is HttpDataSource.InvalidResponseCodeException) {
                            httpStatus = cause.responseCode
                            if (failedUri == null) {
                                failedUri = cause.dataSpec.uri.toString()
                            }
                        }
                        if (causeMsg == null && !cause.message.isNullOrBlank()) {
                            causeMsg = cause.message
                        }
                        cause = cause.cause
                    }

                    // --- Fallback a decodificador por software (patrón MovieBox) ---
                    val isDecoderFailure = error.errorCode in 4001..4005 ||
                        causeMsg?.contains("Decoder failed", ignoreCase = true) == true ||
                        causeMsg?.contains("DecoderInitException", ignoreCase = true) == true

                    if (isDecoderFailure && softwareDecoderMode == 0) {
                        fallbackResumePos = exoPlayer.currentPosition
                        android.widget.Toast.makeText(
                            context,
                            "Reiniciando reproducción con decodificador alternativo…",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        softwareDecoderMode = 1 // Recrea el player y reanuda en fallbackResumePos
                        return
                    }

                    // --- Recuperación ante 5xx del CDN (transitorios de Cloudflare) ---
                    // Si tras los reintentos del loader el servidor sigue devolviendo 5xx,
                    // re-preparar la misma URL unas veces más en vez de cortar al usuario.
                    val isServer5xx = httpStatus != null && httpStatus in 500..599
                    if (isServer5xx) {
                        // 5xx que llega hasta aquí ya sobrevivió los reintentos del
                        // loader: el CDN elegido tiene segmentos caídos. Conmutar al
                        // siguiente CDN candidato (hls3/hls2) reanudando la posición.
                        val embed = embedUrl
                        val current = videoUrl
                        if (!altCdnAttempted && embed != null && current != null) {
                            altCdnAttempted = true
                            val resumePos = exoPlayer.currentPosition
                            scope.launch {
                                val alt = VideoExtractor.extractAlternativeCandidate(embed, current)
                                if (alt != null) {
                                    fallbackResumePos = resumePos
                                    serverRecoveryAttempts = 0
                                    videoUrl = alt // remember(videoUrl, ...) recrea el player con el CDN nuevo
                                } else if (serverRecoveryAttempts < 6) {
                                    serverRecoveryAttempts++
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            exoPlayer.prepare()
                                            if (resumePos > 0L) exoPlayer.seekTo(resumePos)
                                            exoPlayer.play()
                                        } catch (_: Exception) {}
                                    }, 2000L * serverRecoveryAttempts)
                                }
                            }
                            return
                        }
                        if (serverRecoveryAttempts < 6) {
                            serverRecoveryAttempts++
                            val resumePos = exoPlayer.currentPosition
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    exoPlayer.prepare()
                                    if (resumePos > 0L) exoPlayer.seekTo(resumePos)
                                    exoPlayer.play()
                                } catch (_: Exception) {}
                            }, 2000L * serverRecoveryAttempts)
                            return
                        }
                    }
                    // Si el servidor rechazó la URL (código HTTP) o el contenido no se pudo
                    // parsear como video, invalidar la caché para que el próximo ingreso
                    // re-extracte en vez de reutilizar el enlace roto.
                    if (httpStatus != null ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    ) {
                        embedUrl?.let { VideoExtractor.invalidateCachedUrl(it) }
                    }

                    val devDetails = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
                    SupabaseManager.reportPlaybackErrorAsync(
                        movieTitle = "ID: $movieId",
                        version = version,
                        server = embedUrl,
                        streamUrl = videoUrl,
                        errorCode = "${error.errorCodeName} (${error.errorCode})",
                        httpStatus = httpStatus?.let { "HTTP $it" },
                        errorDetail = causeMsg ?: error.message,
                        user = DataCache.currentUser,
                        device = "Celular Android ($devDetails)",
                        failedResourceUrl = failedUri,
                        playbackPosition = "${exoPlayer.currentPosition / 1000}s",
                        deviceModel = devDetails
                    )
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                embedUrl?.let { VideoExtractor.invalidateCachedUrl(it) }
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                // Si el player se recrea (fallback a software), reenganchar la vista
                update = { view -> view.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
            
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}
