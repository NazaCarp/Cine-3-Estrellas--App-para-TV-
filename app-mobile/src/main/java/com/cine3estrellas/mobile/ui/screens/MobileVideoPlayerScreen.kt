package com.cine3estrellas.mobile.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cine3estrellas.SupabaseManager
import com.cine3estrellas.VideoExtractor
import com.cine3estrellas.mobile.Gold
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun MobileVideoPlayerScreen(movieId: Int, version: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(movieId, version) {
        val movie = SupabaseManager.fetchUser(0) // Dummy call to ensure network is up
        // In a real app, you'd get the movie details first or pass the embed URL
        // For now, let's assume we fetch movie details to get the embed URL
        val movieDetails = SupabaseManager.client.from("movies")
            .select { filter { eq("id", movieId) } }
            .decodeSingleOrNull<com.cine3estrellas.Movie>()
        
        val embedUrl = movieDetails?.versions?.get(version)?.url
        if (embedUrl != null) {
            videoUrl = VideoExtractor.extractCleanUrl(embedUrl, movieId, version)
        }
        isLoading = false
    }

    if (isLoading || videoUrl == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator(color = Gold)
            else androidx.compose.material3.Text("No se pudo extraer el video", color = Color.White)
        }
    } else {
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl!!)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(Unit) {
            onDispose { exoPlayer.release() }
        }

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
            modifier = Modifier.fillMaxSize()
        )
    }
}
