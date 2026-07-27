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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

private enum class HistSortMode(val label: String) {
    RECENT("Visto recientemente"),
    RATING("Mejor valoradas"),
    YEAR_DESC("Más recientes"),
    TITLE("Alfabético (A-Z)")
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HistoryScreen(onMovieClick: (Int) -> Unit, onBack: () -> Unit) {
    val isDetailsActive = LocalDetailsActive.current
    val scope = rememberCoroutineScope()
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current

    val historyIds = remember(DataCache.currentUser) {
        DataCache.currentUser?.watchHistory ?: emptyList()
    }
    var allMovies by remember { mutableStateOf(emptyList<Movie>()) }
    var isLoading by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(HistSortMode.RECENT) }
    var minRating by remember { mutableDoubleStateOf(0.0) }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var activeFilterMenu by remember { mutableStateOf<String?>(null) }

    val sortFR = remember { FocusRequester() }
    val ratingFR = remember { FocusRequester() }
    val yearFR = remember { FocusRequester() }
    val editFR = remember { FocusRequester() }
    val backFR = remember { FocusRequester() }
    var editMode by remember { mutableStateOf(false) }

    BackHandler(enabled = editMode) {
        editMode = false
    }

    BackHandler(enabled = !editMode) {
        onBack()
    }

    LaunchedEffect(historyIds) {
        if (historyIds.isEmpty()) {
            allMovies = emptyList()
            return@LaunchedEffect
        }
        val missingIds = historyIds.filter { !DataCache.movieCardCache.containsKey(it) }
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
        allMovies = historyIds.mapNotNull { id -> DataCache.movieCardCache[id] }
    }

    val displayMovies = remember(allMovies, sortMode, minRating, selectedYear) {
        var list = allMovies.toList()
        if (minRating > 0.0) list = list.filter { (it.vote_average ?: 0.0) >= minRating }
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
        list = when (sortMode) {
            HistSortMode.RECENT -> list
            HistSortMode.RATING -> list.sortedByDescending { it.vote_average ?: 0.0 }
            HistSortMode.YEAR_DESC -> list.sortedByDescending { it.release_date ?: "" }
            HistSortMode.TITLE -> list.sortedBy { it.title }
        }
        list
    }

    val focusRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(isDetailsActive, displayMovies) {
        if (!isDetailsActive && displayMovies.isNotEmpty()) {
            val restoreId = DataCache.movieIdToRestore
            val restoreKey = DataCache.keyToRestore

            val shouldRestore = if (restoreId != null && restoreKey == "history_full") {
                true
            } else {
                DataCache.globalLastFocusedKey == "history_full"
            }

            if (shouldRestore) {
                val targetId = restoreId ?: DataCache.lastFocusedMovieId["history_full"]
                if (targetId != null) {
                    delay(100)
                    try {
                        focusRequesters.getOrPut(targetId) { FocusRequester() }.requestFocus()
                    } catch (e: Exception) {
                    }
                    DataCache.movieIdToRestore = null
                    DataCache.keyToRestore = null
                }
            }
        }
    }

    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(displayMovies, isDetailsActive) {
        if (displayMovies.isNotEmpty() && !isDetailsActive && !initialFocusRequested) {
            if (DataCache.globalLastFocusedKey != "history_full") {
                initialFocusRequested = true
                delay(150)
                try {
                    displayMovies.firstOrNull()?.let {
                        focusRequesters.getOrPut(it.id) { FocusRequester() }.requestFocus()
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

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

    val heroMovie = displayMovies.firstOrNull()

    val genreStats = remember(allMovies) {
        val map = mutableMapOf<Int, Int>()
        allMovies.forEach { m -> m.genre_ids?.forEach { g -> map[g] = (map[g] ?: 0) + 1 } }
        map.entries.sortedByDescending { it.value }.take(3)
    }
    val tmdbGenreNames = mapOf(
        28 to "Acción", 12 to "Aventura", 16 to "Animación", 35 to "Comedia",
        80 to "Crimen", 99 to "Documental", 18 to "Drama", 10751 to "Familia",
        14 to "Fantasía", 36 to "Historia", 27 to "Terror", 10402 to "Música",
        9648 to "Misterio", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
        53 to "Suspenso", 10752 to "Bélica", 37 to "Western"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { clip = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (heroMovie != null) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/original${heroMovie.backdrop_path ?: heroMovie.poster_path}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0C10)))
            }

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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 40.dp, end = 320.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF60A5FA).copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF60A5FA).copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "▶️ VISTO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF60A5FA),
                            letterSpacing = 0.15.em
                        )
                    }
                    if (heroMovie != null) {
                        Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(12.dp))
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

                Text(
                    text = heroMovie?.title?.cleanTitle() ?: "MI HISTORIAL",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HistoryStatsPanel(
                total = allMovies.size,
                filtered = displayMovies.size,
                genreStats = genreStats,
                genreNames = tmdbGenreNames,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFR)
                        .focusProperties {
                            right = sortFR
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                        },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        focusedContentColor = Color.White
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.1f),
                    shape = ButtonDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "VOLVER",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${displayMovies.size}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF60A5FA)
                    )
                    Text(
                        "PELÍCULAS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.2.em
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistFilterChip(
                    text = sortMode.label.uppercase(),
                    icon = Icons.Outlined.FilterList,
                    isActive = sortMode != HistSortMode.RECENT,
                    onClick = { activeFilterMenu = "sort" },
                    leftFR = backFR,
                    rightFR = ratingFR,
                    modifier = Modifier
                        .focusRequester(sortFR)
                        .focusProperties { up = FocusRequester.Cancel }
                )
                HistFilterChip(
                    text = if (minRating > 0.0) "$minRating+ ★" else "ESTRELLAS",
                    icon = Icons.Outlined.StarBorder,
                    isActive = minRating > 0.0,
                    onClick = { activeFilterMenu = "stars" },
                    leftFR = sortFR,
                    rightFR = yearFR,
                    modifier = Modifier
                        .focusRequester(ratingFR)
                        .focusProperties { up = FocusRequester.Cancel }
                )
                HistFilterChip(
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
                        .focusProperties { up = FocusRequester.Cancel }
                )
                HistEditButton(
                    isActive = editMode,
                    onClick = { editMode = !editMode },
                    leftFR = yearFR,
                    modifier = Modifier
                        .focusRequester(editFR)
                        .focusProperties { up = FocusRequester.Cancel }
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
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                HistoryEmptyState()
            }
        } else {
            BoxWithConstraints(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                val gridWidth = maxWidth
                val itemWidth = 98.dp
                val spacing = 12.dp
                val columnCount =
                    ((gridWidth - 96.dp + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 48.dp,
                        end = 48.dp,
                        top = 16.dp,
                        bottom = 150.dp
                    )
                ) {
                    itemsIndexed(displayMovies, key = { _, movie -> movie.id }) { index, movie ->
                        val fr = focusRequesters.getOrPut(movie.id) { FocusRequester() }

                        // Primera fila: al subir va a los filtros
                        val isFirstRow = index < columnCount
                        // Última fila real: el ítems que comienzan en el último bloque de filas
                        val lastRowStart = ((displayMovies.size - 1) / columnCount) * columnCount
                        val isLastRow = index >= lastRowStart

                        val rightFr = if (index < displayMovies.size - 1) {
                            val nextId = displayMovies[index + 1].id
                            focusRequesters.getOrPut(nextId) { FocusRequester() }
                        } else {
                            FocusRequester.Cancel
                        }

                        Box(
                            modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        if (isFirstRow) {
                                            try { editFR.requestFocus() } catch (_: Exception) {}
                                        } else {
                                            val targetIndex = index - columnCount
                                            val targetId = displayMovies[targetIndex].id
                                            scope.launch {
                                                // Primero hacemos scroll al ítem objetivo para forzar su composición en pantalla
                                                gridState.scrollToItem(targetIndex)
                                                delay(50) // Pequeño delay para que Compose registre el nuevo elemento en el árbol de vistas
                                                val targetFr = focusRequesters.getOrPut(targetId) { FocusRequester() }
                                                try {
                                                    targetFr.requestFocus()
                                                } catch (_: Exception) {
                                                    // Fallback por si acaso
                                                    delay(50)
                                                    try { targetFr.requestFocus() } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (!isLastRow) {
                                            val targetIndex = minOf(
                                                index + columnCount,
                                                displayMovies.size - 1
                                            )
                                            val targetId = displayMovies[targetIndex].id
                                            scope.launch {
                                                // Hacemos scroll al ítem inferior para asegurar que esté renderizado
                                                gridState.scrollToItem(maxOf(0, targetIndex - columnCount + 1))
                                                delay(50)
                                                val targetFr = focusRequesters.getOrPut(targetId) { FocusRequester() }
                                                try {
                                                    targetFr.requestFocus()
                                                } catch (_: Exception) {
                                                    delay(50)
                                                    try { targetFr.requestFocus() } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                        ) {
                            HistMovieCard(
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

                                            if (targetMovieId != null) {
                                                try {
                                                    focusRequesters.getOrPut(targetMovieId) { FocusRequester() }
                                                        .requestFocus()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }

                                            val newHistory = user.watchHistory.filter { it != movie.id }
                                            val updatedUser = user.copy(watchHistory = newHistory)
                                            val savedUser = SupabaseManager.upsertUser(updatedUser)
                                            if (savedUser != null) {
                                                focusRequesters.remove(movie.id)
                                                DataCache.currentUser = savedUser

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
                                rightFocusRequester = rightFr,
                                focusRequester = fr
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeFilterMenu != null) {
        HistFilterDialog(
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
                    "sort" -> sortMode = HistSortMode.valueOf(value)
                }
                activeFilterMenu = null
            }
        )
    }
}

@Composable
private fun HistoryStatsPanel(
    total: Int,
    filtered: Int,
    genreStats: List<Map.Entry<Int, Int>>,
    genreNames: Map<Int, String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            "ESTADÍSTICAS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 0.2.em
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text("$total", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                " vistos",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }

        if (genreStats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "GÉNEROS PREDOMINANTES",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF60A5FA),
                letterSpacing = 0.1.em
            )
            Spacer(modifier = Modifier.height(8.dp))

            genreStats.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        genreNames[entry.key] ?: "Otros",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        "${entry.value}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
private fun HistMovieCard(
    movie: Movie,
    isFirstInRow: Boolean,
    rightFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    editMode: Boolean = false,
    onMovieClick: () -> Unit,
    onRemove: () -> Unit
) {
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
            DataCache.lastFocusedMovieId["history_full"] = movie.id
            DataCache.globalLastFocusedKey = "history_full"
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "histMovieCardScale"
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
                            if (editMode) Color(0xFFFF4444) else Color(0xFF3B82F6)
                        )
                    )
                ),
                glow = ClickableSurfaceDefaults.glow(
                    focusedGlow = Glow(
                        (if (editMode) Color(0xFFFF4444) else Color(0xFF3B82F6)).copy(
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

                        if (rightFocusRequester != null) right = rightFocusRequester
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (editMode && isFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66FF0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Quitar del historial",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "TU HISTORIAL ESTÁ VACÍO",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.3f),
            letterSpacing = 0.1.em
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Las películas que veas aparecerán aquí.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.2f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistEditButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftFR: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFFFF4444).copy(alpha = 0.15f)
            isFocused -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.05f)
        }, label = "editBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFFFF4444)
            isFocused -> Color.White
            else -> Color.White.copy(alpha = 0.5f)
        }, label = "editContent"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    leftFR?.requestFocus(); leftFR != null
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = bgColor
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    contentColor.copy(alpha = 0.2f)
                )
            ),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.dp, contentColor))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                if (isActive) Icons.Default.Check else Icons.Default.Edit,
                null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = (if (isActive) "LISTO" else "EDITAR"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                letterSpacing = 0.1.em
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistFilterChip(
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
            isFocused -> Color(0xFF60A5FA)
            isActive -> Color(0xFF60A5FA).copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = tween(200), label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF60A5FA)
            isActive -> Color(0xFF60A5FA).copy(alpha = 0.8f)
            else -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(200), label = "chipText"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF60A5FA).copy(alpha = 0.12f)
        else if (isActive) Color(0xFF60A5FA).copy(alpha = 0.06f)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistFilterDialog(
    menuType: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color.Black.copy(alpha = 0.85f))
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Back) {
                    onDismiss(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(320.dp),
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF0F1216)),
            border = Border(
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.1f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = when (menuType) {
                        "sort" -> "ORDENAR POR"
                        "stars" -> "FILTRAR POR ESTRELLAS"
                        "year" -> "FILTRAR POR AÑO"
                        else -> ""
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF60A5FA),
                    letterSpacing = 0.15.em,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val options = when (menuType) {
                    "sort" -> HistSortMode.entries.map { it.name to it.label }
                    "stars" -> listOf(
                        "all" to "Todas las valoraciones",
                        "8" to "8+ Estrellas",
                        "7" to "7+ Estrellas",
                        "6" to "6+ Estrellas"
                    )

                    "year" -> listOf(
                        "all" to "Todos los años",
                        "2024" to "2024",
                        "2023" to "2023",
                        "2020" to "2020+",
                        "2010" to "2010+",
                        "2000" to "2000s",
                        "classic" to "Anteriores a 2000"
                    )

                    else -> emptyList()
                }

                options.forEachIndexed { idx, (valKey, label) ->
                    val fr = remember { FocusRequester() }
                    if (idx == 0) LaunchedEffect(Unit) {
                        try {
                            fr.requestFocus()
                        } catch (e: Exception) {
                        }
                    }

                    HistDialogOption(
                        label = label,
                        isSelected = currentValue == valKey,
                        onClick = { onSelect(valKey) },
                        modifier = Modifier.focusRequester(fr)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistDialogOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = if (isSelected) Color(0xFF60A5FA) else if (isFocused) Color.White else Color.White.copy(
                    alpha = 0.6f
                ),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
