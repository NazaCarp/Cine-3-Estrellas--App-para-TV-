package com.cine3estrellas.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.cine3estrellas.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Sort options ─────────────────────────────────────────────────────────────
private enum class FavSortMode(val label: String) {
    DATE_ADDED("Agregado recientemente"),
    RATING("Mejor valoradas"),
    YEAR_DESC("Más recientes"),
    TITLE("Alfabético (A-Z)")
}

// ─── Main screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FavoritesScreen(onMovieClick: (Int) -> Unit) {
    val isDetailsActive = LocalDetailsActive.current
    val scope = rememberCoroutineScope()
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    // ── State ──────────────────────────────────────────────────────────────────
    val favoriteIds = remember(DataCache.currentUser) {
        DataCache.currentUser?.favorites?.map { it.id }?.reversed() ?: emptyList()
    }
    var allMovies by remember { mutableStateOf(emptyList<Movie>()) }
    var isLoading by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(FavSortMode.DATE_ADDED) }
    var minRating by remember { mutableDoubleStateOf(0.0) }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var activeFilterMenu by remember { mutableStateOf<String?>(null) }
    // Focus requesters for filter row
    val contentRequesters = LocalTabContentFocusRequesters.current
    val favEntryFocusRequester = contentRequesters.getOrNull(3) ?: remember { FocusRequester() }
    val sortFR = remember { FocusRequester() }
    val ratingFR = remember { FocusRequester() }
    val yearFR = remember { FocusRequester() }
    val editFR = remember { FocusRequester() }
    var editMode by remember { mutableStateOf(false) }
    // Exit edit mode on back press
    BackHandler(enabled = editMode) {
        editMode = false
    }
    // ── Fetch favorite movies ──────────────────────────────────────────────────
    LaunchedEffect(favoriteIds) {
        if (favoriteIds.isEmpty()) {
            allMovies = emptyList()
            return@LaunchedEffect
        }
        val missingIds = favoriteIds.filter { !DataCache.movieCardCache.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            isLoading = true
            try {
                val fetched = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date")) { filter { isIn("id", missingIds) } }
                    .decodeList<Movie>()
                DataCache.cacheMovies(fetched)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
        allMovies = favoriteIds.mapNotNull { id -> DataCache.movieCardCache[id] }
    }
    // ── Derived: sorted & filtered list ───────────────────────────────────────
    val displayMovies = remember(allMovies, sortMode, minRating, selectedYear) {
        var list = allMovies.toList()
        // Apply rating filter
        if (minRating > 0.0) list = list.filter { (it.vote_average ?: 0.0) >= minRating }
        // Apply year filter
        val currYear = selectedYear
        if (currYear != null && currYear != "all") {
            list = when (currYear) {
                "classic" -> list.filter { (it.release_date ?: "9999") < "2000" }
                "2000" -> list.filter {
                    val y = it.release_date ?: "9999"; y >= "2000" && y < "2010"
                }

                else -> list.filter { (it.release_date ?: "9999") >= currYear }
            }
        }
        // Apply sort
        list = when (sortMode) {
            FavSortMode.DATE_ADDED -> list // already in date-added order
            FavSortMode.RATING -> list.sortedByDescending { it.vote_average ?: 0.0 }
            FavSortMode.YEAR_DESC -> list.sortedByDescending { it.release_date ?: "" }
            FavSortMode.TITLE -> list.sortedBy { it.title }
        }
        list
    }
    // Map of movie.id to FocusRequester
    val focusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    val gridState = rememberLazyGridState()
    // Auto-focus first card when entering edit mode
    LaunchedEffect(editMode) {
        if (editMode && displayMovies.isNotEmpty()) {
            try {
                displayMovies.firstOrNull()?.let {
                    focusRequesters.getOrPut(it.id) { FocusRequester() }.requestFocus()
                }
            } catch (e: Exception) {
            }
        }
    }
    // ── Hero movie = first in displayMovies ───────────────────────────────────
    val heroMovie = displayMovies.firstOrNull()
    // ── Genre stats ───────────────────────────────────────────────────────────
    val genreStats = remember(allMovies) {
        val map = mutableMapOf<Int, Int>()
        allMovies.forEach { m -> m.genre_ids?.forEach { g -> map[g] = (map[g] ?: 0) + 1 } }
        map.entries.sortedByDescending { it.value }.take(3)
    }
    // Map TMDB genre ids to names (basic mapping)
    val tmdbGenreNames = mapOf(
        28 to "Acción", 12 to "Aventura", 16 to "Animación", 35 to "Comedia",
        80 to "Crimen", 99 to "Documental", 18 to "Drama", 10751 to "Familia",
        14 to "Fantasía", 36 to "Historia", 27 to "Terror", 10402 to "Música",
        9648 to "Misterio", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
        53 to "Suspenso", 10752 to "Bélica", 37 to "Western"
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .focusRequester(favEntryFocusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        val lastKey = DataCache.globalLastFocusedKey
                        if (lastKey == "favorites" && displayMovies.isNotEmpty()) {
                            DataCache.focusRestorationTrigger++
                        } else {
                            sortFR.requestFocus()
                        }
                    }
                }
                .focusable()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
        ) {
            // ── Hero Banner ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Background image
                if (heroMovie != null) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/original${heroMovie.backdrop_path ?: heroMovie.poster_path}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0C10))
                    )
                }
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x66000000),
                                0.5f to Color(0x55000000),
                                1f to Color.Black
                            )
                        )
                )
                // Hero content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 48.dp, bottom = 40.dp, end = 320.dp)
                ) {
                    // Badge row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "❤\uFE0F FAVORITO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Gold,
                                letterSpacing = 0.15.em
                            )
                        }
                        if (heroMovie != null) {
                            Icon(
                                Icons.Default.Star,
                                null,
                                tint = Gold,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "${heroMovie.vote_average ?: "—"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Gold
                            )
                            Text("•", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            Text(
                                heroMovie.release_date?.take(4) ?: "",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Title
                    Text(
                        text = heroMovie?.title?.cleanTitle() ?: "MIS FAVORITOS",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FavoritesStatsPanel(
                    total = allMovies.size,
                    filtered = displayMovies.size,
                    genreStats = genreStats,
                    genreNames = tmdbGenreNames,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 48.dp)
                )
            }
            // ── Controls row ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: count info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${displayMovies.size}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Gold
                    )
                    Text(
                        "PELÍCULAS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.2.em
                    )
                    if (displayMovies.size != allMovies.size) {
                        Text("•", color = Color.White.copy(alpha = 0.3f))
                        Text(
                            "de ${allMovies.size} total",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                // Right: filter buttons
                val firstMovieFr = displayMovies.firstOrNull()
                    ?.let { focusRequesters.getOrPut(it.id) { FocusRequester() } }
                    ?: FocusRequester.Default
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FavFilterChip(
                        text = sortMode.label.uppercase(),
                        icon = Icons.Outlined.FilterList,
                        isActive = sortMode != FavSortMode.DATE_ADDED,
                        onClick = { activeFilterMenu = "sort" },
                        leftFR = if (sidebarRequesters.isNotEmpty()) sidebarRequesters[selectedTab] else null,
                        rightFR = ratingFR,
                        modifier = Modifier
                            .focusRequester(sortFR)
                            .focusProperties {
                                down = firstMovieFr
                                up = FocusRequester.Cancel
                            }
                    )
                    FavFilterChip(
                        text = if (minRating > 0.0) "$minRating+ ★" else "ESTRELLAS",
                        icon = Icons.Outlined.StarBorder,
                        isActive = minRating > 0.0,
                        onClick = { activeFilterMenu = "stars" },
                        leftFR = sortFR,
                        rightFR = yearFR,
                        modifier = Modifier
                            .focusRequester(ratingFR)
                            .focusProperties {
                                down = firstMovieFr
                                up = FocusRequester.Cancel
                            }
                    )
                    FavFilterChip(
                        text = when (selectedYear) {
                            null, "all" -> "AÑO"
                            "classic" -> "RETRO"
                            "2000" -> "2000s"
                            else -> "$selectedYear+"
                        },
                        icon = Icons.Outlined.CalendarMonth,
                        isActive = selectedYear != null && selectedYear != "all",
                        onClick = { activeFilterMenu = "year" },
                        leftFR = ratingFR,
                        rightFR = editFR,
                        modifier = Modifier
                            .focusRequester(yearFR)
                            .focusProperties {
                                down = firstMovieFr
                                up = FocusRequester.Cancel
                            }
                    )
                    FavEditButton(
                        isActive = editMode,
                        onClick = { editMode = !editMode },
                        leftFR = yearFR,
                        modifier = Modifier
                            .focusRequester(editFR)
                            .focusProperties {
                                down = firstMovieFr
                                up = FocusRequester.Cancel
                            }
                    )
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Gold)
                }
            } else if (allMovies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    FavoritesEmptyState()
                }
            } else {
                // ── Favorites Grid ──────────────────────────────────────────────────
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val gridWidth = maxWidth
                    val itemWidth = 98.dp
                    val spacing = 12.dp
                    val columnCount =
                        ((gridWidth - 96.dp + spacing) / (itemWidth + spacing)).toInt()
                            .coerceAtLeast(1)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(itemWidth),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 48.dp,
                            end = 48.dp,
                            top = 16.dp,
                            bottom = 150.dp
                        )
                    ) {
                        itemsIndexed(
                            displayMovies,
                            key = { _, movie -> movie.id }) { index, movie ->
                            val fr = focusRequesters.getOrPut(movie.id) { FocusRequester() }
                            val nextMovie = displayMovies.getOrNull(index + 1)
                            val nextFr =
                                if (nextMovie != null) focusRequesters.getOrPut(nextMovie.id) { FocusRequester() } else null

                            FavMovieCard(
                                movie = movie,
                                isFirstInRow = (index % columnCount == 0),
                                editMode = editMode,
                                onMovieClick = { onMovieClick(movie.id) },
                                onRemove = {
                                    val user = DataCache.currentUser
                                    if (user != null) {
                                        scope.launch {
                                            val targetMovie = if (index >= displayMovies.size - 1) {
                                                displayMovies.getOrNull(index - 1)
                                            } else {
                                                displayMovies.getOrNull(index + 1)
                                            }
                                            val targetMovieId = targetMovie?.id
                                            // 1. Focus target movie immediately to prevent focus from escaping the grid
                                            if (targetMovieId != null) {
                                                try {
                                                    focusRequesters.getOrPut(targetMovieId) { FocusRequester() }
                                                        .requestFocus()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            val newFavorites =
                                                user.favorites.filter { it.id != movie.id }
                                            val updatedUser = user.copy(favorites = newFavorites)
                                            val savedUser = SupabaseManager.upsertUser(updatedUser)
                                            if (savedUser != null) {
                                                focusRequesters.remove(movie.id)
                                                DataCache.currentUser = savedUser

                                                // 2. Re-request focus on the target movie after recomposition just in case
                                                delay(100)
                                                if (targetMovieId != null) {
                                                    try {
                                                        focusRequesters.getOrPut(targetMovieId) { FocusRequester() }
                                                            .requestFocus()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                upFocusRequester = if (index < columnCount) editFR else null,
                                focusRequester = fr,
                                nextFocusRequester = nextFr
                            )
                        }
                    }
                }
            }
        } // end outer Column
        // ── Filter dialogs ────────────────────────────────────────────────────────
        if (activeFilterMenu != null) {
            FavFilterDialog(
                menuType = activeFilterMenu!!,
                currentValue = when (activeFilterMenu) {
                    "stars" -> if (minRating == 0.0) "all" else minRating.toString()
                    "year" -> selectedYear ?: "all"
                    else -> sortMode.name
                },
                onDismiss = { activeFilterMenu = null },
                onSelect = { value ->
                    when (activeFilterMenu) {
                        "stars" -> minRating = if (value == "all") 0.0 else value.toDouble()
                        "year" -> selectedYear = if (value == "all") null else value
                        "sort" -> sortMode = FavSortMode.valueOf(value)
                    }
                    activeFilterMenu = null
                }
            )
        }
    }
}

// ─── Stats panel ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoritesStatsPanel(
    total: Int,
    filtered: Int,
    genreStats: List<Map.Entry<Int, Int>>,
    genreNames: Map<Int, String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0A0C10))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Text(
                "TUS ESTADÍSTICAS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = Gold,
                letterSpacing = 0.2.em
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Gold.copy(alpha = 0.3f))
            )
            // Total count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Favorite, null, tint = Gold, modifier = Modifier.size(14.dp))
                Text(
                    "$total favoritos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            // Top genres
            if (genreStats.isNotEmpty()) {
                Text(
                    "GÉNEROS TOP",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 0.2.em
                )
                genreStats.forEachIndexed { i, entry ->
                    val name = genreNames[entry.key] ?: "Género ${entry.key}"
                    val maxCount = genreStats.first().value.toFloat()
                    val pct = (entry.value / maxCount).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                name,
                                fontSize = 10.sp,
                                color = if (i == 0) Gold else Color.White.copy(alpha = 0.7f),
                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "${entry.value}",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pct)
                                    .fillMaxHeight()
                                    .background(
                                        if (i == 0) Gold else Gold.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Movie card ───────────────────────────────────────────────────────────────
@OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
private fun FavMovieCard(
    movie: Movie,
    isFirstInRow: Boolean,
    upFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    nextFocusRequester: FocusRequester? = null,
    editMode: Boolean = false,
    onMovieClick: () -> Unit,
    onRemove: () -> Unit
) {
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current

    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Focus restoration logic matching MovieCard
    LaunchedEffect(isDetailsActive, movie.id, focusRequester, DataCache.focusRestorationTrigger) {
        if (!isDetailsActive) {
            val restoreId = DataCache.movieIdToRestore
            val restoreKey = DataCache.keyToRestore
            val shouldRestore = if (restoreId != null && restoreKey != null) {
                restoreId == movie.id && restoreKey == "favorites"
            } else {
                DataCache.lastFocusedMovieId["favorites"] == movie.id && DataCache.globalLastFocusedKey == "favorites"
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
    LaunchedEffect(isFocused) {
        if (isFocused && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
            DataCache.lastFocusedMovieId["favorites"] = movie.id
            DataCache.globalLastFocusedKey = "favorites"
        }
    }
    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "favMovieCardScale"
    )
    Box(
        modifier = Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer { clip = false }
    ) {
        Column(
            modifier = Modifier
                .width(98.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    clip = false
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .padding(bottom = 8.dp)
        ) {
            Surface(
                onClick = { if (editMode) onRemove() else onMovieClick() },
                interactionSource = interactionSource,
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
                            if (editMode) Color(0xFFFF4444) else Color(0xFFFFE000)
                        )
                    )
                ),
                glow = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(
                        (if (editMode) Color(0xFFFF4444) else Color(0xFFFFE000)).copy(
                            alpha = 0.5f
                        ), 10.dp
                    )
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .focusRequester(focusRequester)
                    .focusProperties {
                        left = if (isFirstInRow && sidebarRequesters.isNotEmpty()) {
                            sidebarRequesters[selectedTab]
                        } else FocusRequester.Default

                        right = nextFocusRequester ?: FocusRequester.Cancel
                        if (upFocusRequester != null) up = upFocusRequester
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Edit mode overlay
                    if (editMode && isFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66FF0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Quitar de favoritos",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info below
            Text(
                text = movie.title.cleanTitle(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Visible else TextOverflow.Ellipsis,
                color = if (isFocused) (if (editMode) Color(0xFFFF8888) else Color.White) else Color.White.copy(
                    alpha = 0.7f
                ),
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
                    tint = if (editMode && isFocused) Color(0xFFFF8888) else Gold,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = " ${movie.vote_average ?: 0.0} • ${movie.release_date?.take(4) ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) (if (editMode) Color(0xFFFF8888) else Gold) else Color.White.copy(
                        alpha = 0.5f
                    )
                )
            }
            // Fix vertical visibility when focused
            Spacer(modifier = Modifier.height(24.dp))
        } // end Column
    } // end Box
}

// ─── Empty state ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoritesEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Gold.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    null,
                    tint = Gold,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "Aún no tenés favoritos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Guardá las películas que más te gustan\npresionando ❤ en cualquier título",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// ─── Edit mode button ─────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavEditButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftFR: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(180), label = "editBtnScale"
    )
    val activeColor = Color(0xFFFF4444)
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused && isActive -> activeColor
            isFocused -> Gold
            isActive -> activeColor.copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = tween(200), label = "editBtnBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused && isActive -> activeColor
            isFocused -> Gold
            isActive -> activeColor.copy(alpha = 0.9f)
            else -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(200), label = "editBtnIcon"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            leftFR?.requestFocus(); leftFR != null
                        }

                        else -> false
                    }
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.1f) else Color.White.copy(
                alpha = 0.04f
            ),
            focusedContainerColor = if (isActive) activeColor.copy(alpha = 0.15f) else Gold.copy(
                alpha = 0.1f
            )
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, borderColor)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.dp, borderColor))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow((if (isActive) activeColor else Gold).copy(alpha = 0.4f), 8.dp)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.DeleteSweep else Icons.Default.Edit,
                contentDescription = if (isActive) "Salir del modo edición" else "Modo edición",
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Filter chip ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavFilterChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    leftFR: FocusRequester? = null,
    rightFR: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Gold
            isActive -> Gold.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = tween(200), label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Gold
            isActive -> Gold.copy(alpha = 0.8f)
            else -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(200), label = "chipText"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Gold.copy(alpha = 0.12f)
        else if (isActive) Gold.copy(alpha = 0.06f)
        else Color.White.copy(alpha = 0.04f),
        animationSpec = tween(200), label = "chipBg"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(180), label = "chipScale"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            leftFR?.requestFocus(); leftFR != null
                        }

                        Key.DirectionRight -> {
                            rightFR?.requestFocus(); rightFR != null
                        }

                        else -> false
                    }
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = chipScale; scaleY = chipScale },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = bgColor
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, borderColor)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.dp, borderColor))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(13.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = 0.1.em
            )
        }
    }
}

// ─── Filter dialog ────────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavFilterDialog(
    menuType: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = remember(menuType) {
        when (menuType) {
            "sort" -> FavSortMode.entries.map { Pair(it.name, it.label) }
            "stars" -> listOf(
                Pair("all", "Cualquier puntaje"),
                Pair("5.0", "5.0+ (Regulares)"),
                Pair("6.0", "6.0+ (Aceptables)"),
                Pair("7.0", "7.0+ (Buenas)"),
                Pair("8.0", "8.0+ (Muy buenas)"),
                Pair("9.0", "9.0+ (Obras maestras)")
            )

            "year" -> listOf(
                Pair("all", "Cualquier año"),
                Pair("2024", "Estrenos (2024+)"),
                Pair("2020", "Modernas (2020+)"),
                Pair("2010", "Última década (2010+)"),
                Pair("2000", "Clásicos 2000s"),
                Pair("classic", "Retro (Pre-2000)")
            )

            else -> emptyList()
        }
    }
    val initialIdx = remember { options.indexOfFirst { it.first == currentValue }.coerceAtLeast(0) }
    val focusRequesters = remember { List(options.size) { FocusRequester() } }
    LaunchedEffect(menuType) {
        if (initialIdx in focusRequesters.indices) {
            focusRequesters[initialIdx].requestFocus()
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F0F11))
                    .border(1.dp, Gold, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val title = when (menuType) {
                    "sort" -> "ORDENAR POR"
                    "stars" -> "PUNTAJE MÍNIMO"
                    else -> "FILTRAR POR AÑO"
                }
                Text(
                    title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Gold,
                    letterSpacing = 0.2.em,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEachIndexed { index, (value, label) ->
                    val isSelected = value == currentValue
                    FavDialogOption(
                        label = label,
                        isSelected = isSelected,
                        onClick = { onSelect(value) },
                        modifier = Modifier.focusRequester(focusRequesters[index])
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavDialogOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Gold.copy(alpha = 0.15f)
            isSelected -> Gold.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(150), label = "optionBg"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused || isSelected -> Gold
            else -> Color.White.copy(alpha = 0.65f)
        },
        animationSpec = tween(150), label = "optionText"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = bgColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = Gold, modifier = Modifier.size(16.dp))
            }
        }
    }
}
