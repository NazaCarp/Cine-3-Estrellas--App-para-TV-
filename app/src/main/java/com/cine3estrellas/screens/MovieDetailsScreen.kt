package com.cine3estrellas.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.tv.material3.*
import com.cine3estrellas.*
import coil.compose.AsyncImage
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movieId: Int, 
    onMovieClick: (Int) -> Unit,
    onPlayClick: (Int, String) -> Unit
) {
    val context = LocalContext.current
    
    // Buscamos en la caché global para mostrar datos de inmediato (evita pantalla negra)
    var movie by remember(movieId) { 
        mutableStateOf(
            DataCache.movieDetailsMap[movieId] ?:
            DataCache.homeHeroMovies.find { it.id == movieId } ?: 
            DataCache.homeCategoryMovies.values.flatten().find { it.id == movieId }
        ) 
    }

    var similarMovies by remember(movieId) { 
        mutableStateOf(DataCache.similarMoviesMap[movieId] ?: emptyList<Movie>()) 
    }
    var showPlayerOptions by remember { mutableStateOf(false) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var isHeroFocused by remember { mutableStateOf(false) }
    var focusTrigger by remember { mutableStateOf(0) }
    val playButtonRequester = remember { FocusRequester() }
    val loadingFocusRequester = remember { FocusRequester() }
    val firstOptionRequester = remember { FocusRequester() }

    BackHandler(enabled = showPlayerOptions || showDownloadOptions) {
        if (showDownloadOptions) {
            showDownloadOptions = false
            showPlayerOptions = true
        } else {
            showPlayerOptions = false
        }
    }

    LaunchedEffect(Unit) {
        if (movie == null) {
            loadingFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(movie) {
        if (movie != null) {
            delay(100)
            playButtonRequester.requestFocus()
        }
    }

    LaunchedEffect(showPlayerOptions, showDownloadOptions) {
        if (showPlayerOptions || showDownloadOptions) {
            delay(150)
            firstOptionRequester.requestFocus()
        }
    }

    LaunchedEffect(showPlayerOptions, showDownloadOptions) {
        if (!showPlayerOptions && !showDownloadOptions && movie != null) {
            delay(100)
            playButtonRequester.requestFocus()
        }
    }

    LaunchedEffect(isHeroFocused, focusTrigger) {
        if (isHeroFocused) {
            scrollState.scrollTo(0)
        }
    }

    fun loadMovieDetails() {
        errorMessage = null
        // If we already have full details and similar movies, don't fetch again
        if (DataCache.movieDetailsMap.containsKey(movieId) && DataCache.similarMoviesMap.containsKey(movieId)) {
            return
        }

        scope.launch {
            try {
                val supabaseMovie = SupabaseManager.client.from("movies")
                    .select { filter { eq("id", movieId) } }
                    .decodeSingleOrNull<Movie>()
                
                val tmdbMovie = TmdbManager.getMovieDetails(movieId)
                
                val detailedMovie = supabaseMovie?.let { sm ->
                    sm.copy(
                        overview = tmdbMovie?.overview ?: sm.overview,
                        runtime = tmdbMovie?.runtime ?: sm.runtime,
                        genres = tmdbMovie?.genres ?: sm.genres,
                        backdrop_path = tmdbMovie?.backdrop_path ?: sm.backdrop_path,
                        vote_average = tmdbMovie?.vote_average ?: sm.vote_average,
                        certification = tmdbMovie?.certification ?: sm.certification,
                        original_language = tmdbMovie?.original_language ?: sm.original_language
                    )
                } ?: tmdbMovie

                detailedMovie?.let {
                    DataCache.movieDetailsMap[movieId] = it
                    movie = it
                }

                val similar = TmdbManager.getSimilarMovies(movieId)
                DataCache.similarMoviesMap[movieId] = similar
                similarMovies = similar
            } catch (e: Exception) {
                if (movie == null) {
                    errorMessage = "Error al cargar detalles: ${e.message}"
                }
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(movieId) {
        loadMovieDetails()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val m = movie
        if (errorMessage != null && m == null) {
            ErrorScreen(message = errorMessage!!, onRetry = { loadMovieDetails() })
        } else if (m == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(loadingFocusRequester)
                    .focusable()
            )
        } else {
            // Fondo común (siempre presente)
            AsyncImage(
                model = "https://image.tmdb.org/t/p/original${m.backdrop_path}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f)
                    .then(if (showPlayerOptions || showDownloadOptions) Modifier.blur(10.dp) else Modifier)
            )

            if (showPlayerOptions || showDownloadOptions) {
                // VISTA DE REPRODUCCIÓN (Limpia)
                Box(modifier = Modifier.fillMaxSize()) {
                    // Filtro oscuro base sobre toda la imagen de fondo
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

                    // Degradado horizontal fuerte para el menú
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.95f),
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = 1200f
                                )
                            )
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Columna Izquierda: Menú (Tamaño compacto, estilo Premium)
                        Column(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F0F0F).copy(alpha = 0.7f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                                .width(280.dp)
                        ) {
                            Text(
                                text = "REPRODUCCIÓN",
                                color = Gold,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                fontSize = 10.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            val versionEntries = m.versions?.entries?.toList() ?: emptyList()
                            versionEntries.forEachIndexed { index, entry ->
                                val lang = entry.key
                                val version = entry.value
                                
                                val originFlag = when(m.original_language) {
                                    "en" -> "🇺🇸"
                                    "ko" -> "🇰🇷"
                                    "ja" -> "🇯🇵"
                                    "fr" -> "🇫🇷"
                                    "it" -> "🇮🇹"
                                    "pt" -> "🇧🇷"
                                    "tr" -> "🇹🇷"
                                    "hi" -> "🇮🇳"
                                    "ru" -> "🇷🇺"
                                    "zh" -> "🇨🇳"
                                    "es" -> "🇪🇸"
                                    else -> "🌐"
                                }

                                val (flag, subtext) = when (lang.lowercase()) {
                                    "latino" -> "🇲🇽" to "AUDIO ESPAÑOL LATINO"
                                    "castellano" -> "🇪🇸" to "AUDIO ESPAÑOL ESPAÑA"
                                    else -> originFlag to "SUBTITULADO / AUDIO ORIG."
                                }

                                var isFocused by remember { mutableStateOf(false) }
                                val translationX by animateDpAsState(targetValue = if (isFocused) 10.dp else 0.dp, label = "translationX")

                                Surface(
                                    onClick = { onPlayClick(movieId, lang) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .height(52.dp)
                                        .offset(x = translationX)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .then(if (index == 0) Modifier.focusRequester(firstOptionRequester) else Modifier),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color(0xFF1A1A1A),
                                        focusedContainerColor = Color(0xFF252525)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, Color(0xFFFFE000)), shape = RoundedCornerShape(10.dp))
                                    ),
                                    glow = ClickableSurfaceDefaults.glow(
                                        focusedGlow = Glow(Color(0xFFFFE000).copy(alpha = 0.1f), 10.dp)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isFocused) {
                                            Box(modifier = Modifier.width(2.dp).height(24.dp).clip(RoundedCornerShape(1.dp)).background(Gold))
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }

                                        Text(text = flag, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = lang.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White, fontSize = 12.sp)
                                            Text(text = subtext, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, if (isFocused) Gold else Color.White.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                                                .background(if (isFocused) Gold.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(5.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(text = "HD", style = MaterialTheme.typography.labelSmall, color = if (isFocused) Gold else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black, fontSize = 8.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Text(text = "DESCARGA DIRECTA", color = Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            var isDownloadFocused by remember { mutableStateOf(false) }
                            val downloadTranslationX by animateDpAsState(targetValue = if (isDownloadFocused) 10.dp else 0.dp, label = "downloadTranslationX")

                            Surface(
                                onClick = { 
                                    m.versions?.values?.firstOrNull()?.downloadUrl?.let {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                            context.startActivity(intent)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .offset(x = downloadTranslationX)
                                    .onFocusChanged { isDownloadFocused = it.isFocused },
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1A1A), focusedContainerColor = Color(0xFF252525)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp))
                            ) {
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("OBTENER ENLACES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                                }
                            }
                        }

                        // Columna Derecha: Título
                        Box(modifier = Modifier.weight(1f).padding(end = 60.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "${m.title.cleanTitle().uppercase()} (${m.release_date?.take(4) ?: ""})",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        offset = Offset(4f, 4f),
                                        blurRadius = 8f
                                    )
                                ),
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 40.dp)
                            )
                        }
                    }

                    // Botón Cerrar
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopEnd) {
                        Surface(
                            onClick = { showPlayerOptions = false; showDownloadOptions = false },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), focusedContainerColor = Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            } else {
                // VISTA DE DETALLES NORMAL
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 48.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 48.dp)
                                .onFocusChanged {
                                    isHeroFocused = it.hasFocus
                                    if (it.hasFocus) focusTrigger++
                                }
                        ) {
                            Text(
                                text = m.title.cleanTitle(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "${m.vote_average}", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }

                                Text(text = m.release_date?.take(4) ?: "", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                                
                                if (!m.certification.isNullOrBlank()) {
                                    Box(modifier = Modifier.border(1.dp, Gold, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(text = m.certification!!, style = MaterialTheme.typography.labelLarge, color = Gold, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val hours = (m.runtime ?: 0) / 60
                                val minutes = (m.runtime ?: 0) % 60
                                val runtimeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                                Text(text = runtimeText, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)

                                Box(modifier = Modifier.border(1.dp, Gold, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(text = "HD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Gold, fontSize = 11.sp)
                                }

                                val versionKeys = m.versions?.keys ?: emptySet()
                                for (lang in versionKeys) {
                                    Surface(
                                        shape = CircleShape,
                                        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp)) {
                                            when (lang.lowercase()) {
                                                "latino" -> Text(text = "🇲🇽", fontSize = 18.sp)
                                                "castellano" -> Text(text = "🇪🇸", fontSize = 18.sp)
                                                else -> Icon(imageVector = Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = lang.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            if (!m.genres.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0x4D000000), RoundedCornerShape(8.dp))
                                        .padding(vertical = 6.dp, horizontal = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "GÉNEROS:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Gold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = m.genres!!.joinToString(" • ") { it.name },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Text(text = if (m.overview.isNullOrBlank()) "Sin sinopsis disponible." else m.overview!!, style = MaterialTheme.typography.bodyLarge, color = Color.White, lineHeight = 24.sp, modifier = Modifier.width(800.dp))
                            Spacer(modifier = Modifier.height(32.dp))

                            Row {
                                val sidebarRequesters = LocalTabFocusRequesters.current
                                val selectedTab = LocalSelectedTab.current

                                var isReproducirFocused by remember { mutableStateOf(false) }
                                val reproducirScale by animateFloatAsState(
                                    targetValue = if (isReproducirFocused) 1.1f else 1.0f,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "scale"
                                )
                                val reproducirAlpha by animateFloatAsState(
                                    targetValue = if (isReproducirFocused) 1f else 0.6f,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "alpha"
                                )
                                val reproducirContentColor by animateColorAsState(
                                    targetValue = if (isReproducirFocused) Color(0xFF3A3000) else Color.Black,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                    label = "color"
                                )

                                Button(
                                    onClick = { showPlayerOptions = true },
                                    modifier = Modifier
                                        .focusRequester(playButtonRequester)
                                        .onFocusChanged { isReproducirFocused = it.isFocused }
                                        .focusProperties { 
                                            up = FocusRequester.Cancel
                                            left = if (sidebarRequesters.isNotEmpty()) sidebarRequesters[selectedTab] else FocusRequester.Default 
                                        }
                                        .zIndex(if (isReproducirFocused) 1f else 0f)
                                        .graphicsLayer {
                                            scaleX = reproducirScale
                                            scaleY = reproducirScale
                                            alpha = reproducirAlpha
                                        }
                                        .requiredWidth(180.dp)
                                        .requiredHeight(52.dp),
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.9f),
                                        contentColor = reproducirContentColor,
                                        focusedContainerColor = Color.Transparent,
                                        focusedContentColor = reproducirContentColor
                                    ),
                                    scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                    glow = ButtonDefaults.glow(focusedGlow = Glow(Color(0xFFFFD700).copy(alpha = 0.4f), 10.dp)),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isReproducirFocused) Modifier.background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFFFFF6DF), Color(0xFFFFD700))
                                                    )
                                                ) else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp), tint = reproducirContentColor)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Reproducir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = reproducirContentColor)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))

                                val isFavorite = remember(m, DataCache.currentUser) {
                                    DataCache.currentUser?.favorites?.any { it.id == m.id } == true
                                }

                                var isFavoritosFocused by remember { mutableStateOf(false) }
                                val favoritosScale by animateFloatAsState(
                                     targetValue = if (isFavoritosFocused) 1.1f else 1.0f,
                                     animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                     label = "scale"
                                 )
                                 val favoritosContentColor by animateColorAsState(
                                     targetValue = if (isFavoritosFocused) Color(0xFF3A3000) else if (isFavorite) Gold else Color(0xFFD0C6AB).copy(alpha = 0.8f),
                                     animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                     label = "color"
                                 )

                                 Button(
                                     onClick = {
                                         val user = DataCache.currentUser
                                         if (user != null) {
                                             scope.launch {
                                                 val newFavorites = if (isFavorite) {
                                                     user.favorites.filter { it.id != m.id }
                                                 } else {
                                                     user.favorites + FavoriteItem(id = m.id, title = m.title)
                                                 }
                                                 val updatedUser = user.copy(favorites = newFavorites)
                                                 val savedUser = SupabaseManager.upsertUser(updatedUser)
                                                 if (savedUser != null) {
                                                     DataCache.currentUser = savedUser
                                                 }
                                                 
                                                 val eventName = if (isFavorite) "💔 Unlike" else "❤️ Like"
                                                 SupabaseManager.logEvent(
                                                     DbEvent(
                                                         user_id = user.telegramId,
                                                         first_name = user.firstName,
                                                         event_name = eventName,
                                                         movie_id = m.id.toLong(),
                                                         movie_title = m.title
                                                     )
                                                 )
                                             }
                                         }
                                     },
                                     modifier = Modifier
                                         .onFocusChanged { isFavoritosFocused = it.isFocused }
                                         .focusProperties { up = FocusRequester.Cancel }
                                         .zIndex(if (isFavoritosFocused) 1f else 0f)
                                         .graphicsLayer {
                                             scaleX = favoritosScale
                                             scaleY = favoritosScale
                                         }
                                         .requiredSize(52.dp),
                                     colors = ButtonDefaults.colors(
                                         containerColor = Color.White.copy(alpha = 0.1f),
                                         contentColor = favoritosContentColor,
                                         focusedContainerColor = Color.Transparent,
                                         focusedContentColor = favoritosContentColor
                                     ),
                                     scale = ButtonDefaults.scale(focusedScale = 1.0f),
                                     glow = ButtonDefaults.glow(focusedGlow = Glow(Color(0xFFFFD700).copy(alpha = 0.4f), 10.dp)),
                                     shape = ButtonDefaults.shape(CircleShape),
                                     contentPadding = PaddingValues(0.dp)
                                 ) {
                                     Box(
                                         modifier = Modifier
                                             .fillMaxSize()
                                             .then(
                                                 if (isFavoritosFocused) Modifier.background(
                                                     Brush.linearGradient(
                                                         colors = listOf(Color(0xFFFFF6DF), Color(0xFFFFD700))
                                                     )
                                                 ) else Modifier
                                             ),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                             contentDescription = if (isFavorite) "Quitar de Favoritos" else "Agregar a Favoritos",
                                             modifier = Modifier.size(24.dp),
                                             tint = favoritosContentColor
                                         )
                                     }
                                 }
                            }
                        }

                        if (similarMovies.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Text(text = "Películas Similares", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(horizontal = 48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                itemsIndexed(similarMovies) { index, sim -> MovieCard(movie = sim, onClick = { onMovieClick(sim.id) }, isFirstInRow = index == 0) }
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
