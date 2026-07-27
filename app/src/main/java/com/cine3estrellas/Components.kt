package com.cine3estrellas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MovieCardPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val shimmerColors = listOf(
        Color.DarkGray.copy(alpha = 0.6f),
        Color.Gray.copy(alpha = 0.2f),
        Color.DarkGray.copy(alpha = 0.6f),
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(2f / 3f)
            .background(brush, RoundedCornerShape(8.dp))
    )
}

fun String.cleanTitle(): String {
    return this.replace(Regex("\\s\\(\\d{4}\\)$"), "").trim()
}

/**
 * Converts text-based certifications (TMDB) to numerical format (+18, +16, etc.)
 */
fun formatCertification(cert: String?): String {
    if (cert.isNullOrBlank()) return ""

    // Normalize string
    val c = cert.uppercase().trim()

    // Check if the certification is just a number
    if (c.all { it.isDigit() }) return "+$c"
    return when {
        // Spain / Generic numeric
        c.contains("18") -> "+18"
        c.contains("16") -> "+16"
        c.contains("15") -> "+15"
        c.contains("13") -> "+13"
        c.contains("12") -> "+12"
        c.contains("7") -> "+7"

        // US Ratings (Common in TMDB)
        c == "R" || c == "NC-17" || c == "TV-MA" -> "+18"
        c == "PG-13" || c == "TV-14" -> "+13"
        c == "PG" || c == "TV-PG" -> "+7"
        c == "G" || c == "TV-G" || c == "TV-Y" || c == "TV-Y7" -> "TP" // Todo Público

        // Latin America
        c == "C" -> "+18"
        c == "B15" -> "+15"
        c == "B" -> "+12"
        c == "A" -> "TP"

        else -> cert // Fallback
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstInRow: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp = 110.dp,
    screenKey: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    nextFocusRequester: FocusRequester? = null,
    leftFocus: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    rightFocus: FocusRequester? = null
) {
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current
    var isFocused by remember { mutableStateOf(false) }
    // Focus restoration logic
    LaunchedEffect(isDetailsActive, focusRequester, DataCache.focusRestorationTrigger) {
        if (!isDetailsActive && screenKey != null) {
            val restoreId = DataCache.movieIdToRestore
            val restoreKey = DataCache.keyToRestore
            val shouldRestore = if (restoreId != null && restoreKey != null) {
                restoreId == movie.id && restoreKey == screenKey
            } else {
                DataCache.lastFocusedMovieId[screenKey] == movie.id && DataCache.globalLastFocusedKey == screenKey
            }
            if (shouldRestore) {
                delay(50) // Tiny delay for UI settling
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
                if (restoreId != null) {
                    DataCache.movieIdToRestore = null
                    DataCache.keyToRestore = null
                }
            }
        }
    }
    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "movieCardScale"
    )
    Box(modifier = Modifier.graphicsLayer { clip = false }) {
        Column(
            modifier = modifier
                .width(cardWidth)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .onFocusChanged {
                    isFocused = it.hasFocus
                    if (it.hasFocus && screenKey != null && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                        DataCache.lastFocusedMovieId[screenKey] = movie.id
                        DataCache.globalLastFocusedKey = screenKey
                    }
                }
                .padding(bottom = 8.dp)
        ) {
            Surface(
                onClick = onClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    focusedBorder = Border(
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Color(0xFFFFE000)
                        )
                    )
                ),
                glow = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(Color(0xFFFFE000).copy(alpha = 0.5f), 10.dp)
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .focusRequester(focusRequester)
                    .focusProperties {
                        left = leftFocus ?: if (isFirstInRow && sidebarRequesters.isNotEmpty()) {
                            sidebarRequesters[selectedTab]
                        } else FocusRequester.Default

                        if (rightFocus != null) {
                            right = rightFocus
                        } else if (nextFocusRequester != null) {
                            right = nextFocusRequester
                        }

                        if (upFocus != null) up = upFocus
                        if (downFocus != null) down = downFocus
                    }
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info below
            Text(
                text = movie.title.cleanTitle(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Visible else TextOverflow.Ellipsis,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .then(
                        if (isFocused) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 50.dp,
                                initialDelayMillis = 1000
                            )
                        } else Modifier
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = " ${movie.vote_average ?: 0.0} • ${movie.release_date?.take(4) ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) Gold else Color.White.copy(alpha = 0.5f)
                )
            }
        } // end Column
    } // end Box
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HistoryMovieCard(
    movie: Movie,
    progressSec: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstInRow: Boolean = false,
    screenKey: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    leftFocus: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current
    var isFocused by remember { mutableStateOf(false) }
    // Focus restoration logic
    LaunchedEffect(isDetailsActive, movie.id, screenKey, focusRequester, DataCache.focusRestorationTrigger) {
        if (!isDetailsActive && screenKey != null) {
            val restoreId = DataCache.movieIdToRestore
            val restoreKey = DataCache.keyToRestore
            val shouldRestore = if (restoreId != null && restoreKey != null) {
                restoreId == movie.id && restoreKey == screenKey
            } else {
                DataCache.lastFocusedMovieId[screenKey] == movie.id && DataCache.globalLastFocusedKey == screenKey
            }
            if (shouldRestore) {
                delay(50) // Tiny delay for UI settling
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                }
                if (restoreId != null) {
                    DataCache.movieIdToRestore = null
                    DataCache.keyToRestore = null
                }
            }
        }
    }
    val histCardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "histCardScale"
    )
    Box(modifier = Modifier.graphicsLayer { clip = false }) {
        Column(
            modifier = modifier
                .width(96.dp)
                .graphicsLayer {
                    scaleX = histCardScale
                    scaleY = histCardScale
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .onFocusChanged {
                    isFocused = it.hasFocus
                    if (it.hasFocus && screenKey != null && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                        DataCache.lastFocusedMovieId[screenKey] = movie.id
                        DataCache.globalLastFocusedKey = screenKey
                    }
                }
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                onClick = onClick,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    focusedBorder = Border(
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Color(0xFF3B82F6)
                        )
                    )
                ),
                glow = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(Color(0xFF3B82F6).copy(alpha = 0.5f), 10.dp)
                ),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                modifier = Modifier
                    .size(80.dp)
                    .focusRequester(focusRequester)
                    .focusProperties {
                        left = leftFocus ?: if (isFirstInRow && sidebarRequesters.isNotEmpty()) {
                            sidebarRequesters[selectedTab]
                        } else FocusRequester.Default

                        if (nextFocusRequester != null) right = nextFocusRequester
                        if (upFocus != null) up = upFocus
                        if (downFocus != null) down = downFocus
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    val runtimeMin = movie.runtime ?: 120
                    val totalDurationSec = runtimeMin * 60.0
                    val progressFraction =
                        (progressSec / totalDurationSec).coerceIn(0.0, 1.0).toFloat()

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 4.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                        val arcOffset = Offset(
                            (size.width - diameter) / 2,
                            (size.height - diameter) / 2
                        )

                        // Background grey ring
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = radius,
                            style = Stroke(width = strokeWidth)
                        )

                        // Foreground blue progress arc
                        drawArc(
                            color = Color(0xFF3B82F6),
                            startAngle = -90f,
                            sweepAngle = progressFraction * 360f,
                            useCenter = false,
                            style = Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            ),
                            size = arcSize,
                            topLeft = arcOffset
                        )
                    }

                    if (isFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x33000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = movie.title.cleanTitle(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .then(
                        if (isFocused) Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 50.dp,
                            initialDelayMillis = 1000
                        ) else Modifier
                    )
            )

            val progressMin = (progressSec / 60.0).toInt()
            Text(
                text = "$progressMin MIN",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF60A5FA),
                fontWeight = FontWeight.Black,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } // end Column
    } // end Box
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeeMoreCard(
    remainingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    screenKey: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }
    val isCategoryActive = LocalCategoryActive.current
    val isDetailsActive = LocalDetailsActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current
    // Focus restoration logic
    LaunchedEffect(isCategoryActive, screenKey, focusRequester, DataCache.focusRestorationTrigger) {
        if (!isCategoryActive && screenKey != null) {
            val restoreKey = DataCache.categoryIdToRestore
            val shouldRestore = if (restoreKey != null) {
                screenKey == "home_category_${restoreKey}_seemore"
            } else {
                DataCache.globalLastFocusedKey == screenKey
            }
            if (shouldRestore) {
                delay(50) // Tiny delay for UI settling
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                }
                if (restoreKey != null) {
                    DataCache.categoryIdToRestore = null
                }
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isFocused) 360f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "rotation"
    )
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent)),
            focusedBorder = Border(
                androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Color(0xFFFFE000)
                )
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(Color(0xFFFFE000).copy(alpha = 0.5f), 15.dp)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0F0F0F),
            focusedContainerColor = Color(0xFF1A1A1A)
        ),
        modifier = modifier
            .width(110.dp)
            .aspectRatio(2f / 3f)
            .focusRequester(focusRequester)
            .focusProperties {
                right = FocusRequester.Cancel
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && screenKey != null && !isCategoryActive && !isDetailsActive && !isPlayerActive) {
                    DataCache.globalLastFocusedKey = screenKey
                }
            }
            .drawWithContent {
                drawContent()
                if (!isFocused) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        ),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Circle with + icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .background(
                            color = if (isFocused) Color(0xFFFFE000) else Color.White.copy(alpha = 0.08f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "VER MÁS",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )

                if (remainingCount > 0) {
                    Text(
                        text = "+$remainingCount títulos",
                        color = Gold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HeroSection(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    heroFocusRequester: FocusRequester? = null,
    requestInitialFocus: Boolean = false,
    downFocus: FocusRequester? = null,
    screenKey: String? = null
) {
    // Basic Carousel for Hero (persisted in DataCache)
    val currentIndex = DataCache.heroCurrentIndex
    var isAnyElementFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Track if the user has explicitly interacted with the Hero (e.g., moved within it or clicked)
    // This allows the carousel to scroll on startup even if focused by default.
    var hasInteractedWithHero by remember { mutableStateOf(false) }

    // Auto-update info when movie list changes
    val movie = if (movies.isNotEmpty()) movies[currentIndex % movies.size] else null
    val scope = rememberCoroutineScope()
    val isFavorite = remember(movie, DataCache.currentUser) {
        val id = movie?.id
        DataCache.currentUser?.favorites?.any { it.id == id } == true
    }
    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current
    // Focus restoration logic for Hero Section
    LaunchedEffect(isDetailsActive) {
        if (!isDetailsActive && screenKey != null && DataCache.globalLastFocusedKey == screenKey) {
            val lastFocusedId = DataCache.lastFocusedMovieId[screenKey]
            val indexInHero = movies.indexOfFirst { it.id == lastFocusedId }
            if (indexInHero != -1) {
                DataCache.heroCurrentIndex = indexInHero
                delay(50)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                }
            }
        }
    }
    // Automatic carousel logic
    LaunchedEffect(movies, isAnyElementFocused, hasInteractedWithHero) {
        // Scroll if no focus OR if focused but user hasn't interacted yet (initial entry)
        if (movies.isNotEmpty() && (!isAnyElementFocused || !hasInteractedWithHero)) {
            while (true) {
                delay(5000) // 5 seconds per slide
                DataCache.heroCurrentIndex = (DataCache.heroCurrentIndex + 1) % movies.size
            }
        }
    }
    // Initial focus request
    LaunchedEffect(Unit) {
        if (heroFocusRequester != null && requestInitialFocus) {
            delay(100)
            try {
                heroFocusRequester.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp)
            .onFocusChanged {
                if (it.hasFocus) {
                    isAnyElementFocused = true
                    onFocused()
                    // If we focus the Hero, save the currently displayed movie ID
                    if (screenKey != null && movie != null && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                        DataCache.lastFocusedMovieId[screenKey] = movie.id
                        DataCache.globalLastFocusedKey = screenKey
                    }
                } else {
                    isAnyElementFocused = false
                }
            }
    ) {
        // Background Image of focused movie
        movie?.let { m ->
            AsyncImage(
                model = "https://image.tmdb.org/t/p/original${m.backdrop_path}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 58.dp, bottom = 64.dp)
                    .width(700.dp)
            ) {
                Text(
                    text = m.title.cleanTitle(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // HD Badge
                    Box(
                        modifier = Modifier
                            .border(1.dp, Gold, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "HD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Rating
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " ${m.vote_average ?: 0.0}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Gold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Year
                    Text(
                        text = m.release_date?.take(4) ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (!m.certification.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        // Age Rating Badge
                        val formattedCert = formatCertification(m.certification)
                        val isAdult = formattedCert.contains("18")
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isAdult) Color.Red else Gold,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = formattedCert,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAdult) Color.White else Color.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Runtime
                    val hours = (m.runtime ?: 0) / 60
                    val minutes = (m.runtime ?: 0) % 60
                    val runtimeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (m.overview.isNullOrBlank()) "Sin sinopsis disponible." else m.overview!!,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Genres Row
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
                Spacer(modifier = Modifier.height(32.dp))

                Row {
                    val sidebarRequesters = LocalTabFocusRequesters.current
                    val selectedTab = LocalSelectedTab.current

                    var isVerAhoraFocused by remember { mutableStateOf(false) }
                    val verAhoraScale by animateFloatAsState(
                        targetValue = if (isVerAhoraFocused) 1.1f else 1.0f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "scale"
                    )
                    val verAhoraAlpha by animateFloatAsState(
                        targetValue = if (isVerAhoraFocused) 1f else 0.6f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "alpha"
                    )
                    val verAhoraContentColor by animateColorAsState(
                        targetValue = if (isVerAhoraFocused) Color(0xFF3A3000) else Color.Black,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "color"
                    )
                    Button(
                        onClick = {
                            hasInteractedWithHero = true
                            onMovieClick(m)
                        },
                        modifier = Modifier
                            .onFocusChanged {
                                isVerAhoraFocused = it.isFocused
                                if (it.isFocused) onFocused()
                                // If focused AND moving or clicking, mark as interacted
                                if (it.isFocused && it.hasFocus) {
                                    // We'll set this to true if the user actually does something
                                }
                            }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    hasInteractedWithHero = true
                                }
                                false
                            }
                            .focusRequester(focusRequester)
                            .then(
                                if (heroFocusRequester != null) Modifier.focusRequester(
                                    heroFocusRequester
                                ) else Modifier
                            )
                            .focusProperties {
                                up = FocusRequester.Cancel
                                if (downFocus != null) down = downFocus
                                left =
                                    if (sidebarRequesters.isNotEmpty()) sidebarRequesters[selectedTab] else FocusRequester.Default
                            }
                            .zIndex(if (isVerAhoraFocused) 1f else 0f)
                            .graphicsLayer {
                                scaleX = verAhoraScale
                                scaleY = verAhoraScale
                                alpha = verAhoraAlpha
                            }
                            .requiredWidth(180.dp)
                            .requiredHeight(52.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.9f),
                            contentColor = verAhoraContentColor,
                            focusedContainerColor = Color.Transparent,
                            focusedContentColor = verAhoraContentColor
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.0f),
                        glow = ButtonDefaults.glow(
                            focusedGlow = Glow(Color(0xFFFFD700).copy(alpha = 0.4f), 10.dp)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isVerAhoraFocused) Modifier.background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFFFF6DF), Color(0xFFFFD700))
                                        )
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Ver ahora",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = verAhoraContentColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    var isMasInfoFocused by remember { mutableStateOf(false) }
                    val masInfoScale by animateFloatAsState(
                        targetValue = if (isMasInfoFocused) 1.1f else 1.0f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "scale"
                    )
                    val masInfoContentColor by animateColorAsState(
                        targetValue = if (isMasInfoFocused) Color(0xFF3A3000) else if (isFavorite) Gold else Color.White.copy(
                            alpha = 0.8f
                        ),
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "color"
                    )
                    Button(
                        onClick = {
                            hasInteractedWithHero = true
                            val user = DataCache.currentUser
                            if (user != null && m != null) {
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
                            .onFocusChanged {
                                isMasInfoFocused = it.isFocused
                                if (it.isFocused) onFocused()
                            }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    hasInteractedWithHero = true
                                }
                                false
                            }
                            .focusProperties {
                                up = FocusRequester.Cancel
                            }
                            .zIndex(if (isMasInfoFocused) 1f else 0f)
                            .graphicsLayer {
                                scaleX = masInfoScale
                                scaleY = masInfoScale
                            }
                            .requiredSize(52.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = masInfoContentColor,
                            focusedContainerColor = Color.Transparent,
                            focusedContentColor = masInfoContentColor
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.0f),
                        glow = ButtonDefaults.glow(
                            focusedGlow = Glow(Color(0xFFFFD700).copy(alpha = 0.4f), 10.dp)
                        ),
                        shape = ButtonDefaults.shape(CircleShape),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isMasInfoFocused) Modifier.background(
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
                                tint = masInfoContentColor
                            )
                        }
                    }
                }
            }
        }
        // Carousel Indicators
        if (movies.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                movies.forEachIndexed { index, _ ->
                    val isActive = index == currentIndex % movies.size
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .background(
                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SignalWifiOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¡UPS! ALGO SALIÓ MAL",
                style = MaterialTheme.typography.headlineMedium,
                color = Gold,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (message.contains("timeout", ignoreCase = true))
                    "La conexión con el servidor ha tardado demasiado.\nPor favor, verifica tu internet e intenta de nuevo."
                else message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Surface(
                onClick = onRetry,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .width(220.dp)
                    .height(56.dp),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Gold,
                    pressedContainerColor = Gold.copy(alpha = 0.8f)
                ),
                glow = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(Gold.copy(alpha = 0.3f), 20.dp)
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "REINTENTAR",
                        fontWeight = FontWeight.Bold,
                        color = Color.Unspecified, // Uses contentColor from Surface
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
