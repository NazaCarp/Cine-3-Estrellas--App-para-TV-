package com.cine3estrellas.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import com.cine3estrellas.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.*
import com.cine3estrellas.*
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow

// Using DataCache instead of local HomeCache

@OptIn(ExperimentalFoundationApi::class)
private val NoScrollBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(onMovieClick: (Int) -> Unit, onSeeMoreClick: (String) -> Unit) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadingFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }

    // Pulse animation for loading logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Track if we've already handled the initial focus for this session
    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }

    var historyMovies by remember { mutableStateOf(emptyList<Movie>()) }
    val historyIds = remember(DataCache.currentUser) {
        val watch = DataCache.currentUser?.watchHistory ?: emptyList()
        val hidden = DataCache.currentUser?.hiddenHistory ?: emptyList()
        watch.filter { it !in hidden }
    }

    LaunchedEffect(historyIds) {
        if (historyIds.isEmpty()) {
            historyMovies = emptyList()
            return@LaunchedEffect
        }
        try {
            val response = SupabaseManager.client.from("movies")
                .select {
                    filter {
                        isIn("id", historyIds)
                    }
                }.decodeList<Movie>()
            val ordered = historyIds.mapNotNull { id -> response.find { it.id == id } }
            historyMovies = ordered
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadHomeData() {
        errorMessage = null
        scope.launch {
            if (DataCache.isHomeInitialLoaded) return@launch

            try {
                val response = SupabaseManager.fetchTvHomeData()
                if (response == null || response.categories.isEmpty()) {
                    throw Exception("El servidor no devolvió datos válidos.")
                }

                DataCache.homeHeroMovies = response.heroMovies
                DataCache.homeCategories = response.categories.map { tvCat ->
                    HomeCategory(
                        id = tvCat.id,
                        name = tvCat.name,
                        icon = tvCat.icon,
                        order = tvCat.order,
                        show_in_home = tvCat.show_in_home,
                        genre_ids = tvCat.genre_ids,
                        keywords = tvCat.keywords,
                        min_rating = tvCat.min_rating,
                        sort_by = tvCat.sort_by
                    )
                }
                response.categories.forEach { tvCat ->
                    DataCache.homeCategoryMovies[tvCat.id] = tvCat.movies
                    // Use the exact count from the server (Supabase COUNT query).
                    // Fallback to movie list size only if the server didn't return a count.
                    val count = tvCat.total_count ?: tvCat.movies.size.toLong()
                    DataCache.homeCategoryTotalCounts[tvCat.id] = count
                }
                DataCache.isHomeInitialLoaded = true
            } catch (e: Exception) {
                val techMsg = e.message ?: ""
                errorMessage = if (techMsg.contains("timeout", ignoreCase = true)) {
                    "La conexión ha tardado demasiado. Por favor, verifica tu internet."
                } else {
                    "Error de conexión: ${e.message}"
                }
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHomeData()
    }

    if (errorMessage != null && !DataCache.isHomeInitialLoaded) {
        ErrorScreen(
            message = errorMessage!!,
            onRetry = { loadHomeData() }
        )
    } else if (DataCache.homeCategories.isEmpty() && !DataCache.isHomeInitialLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(loadingFocusRequester)
                .focusable(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Cine 3 Estrellas Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = Gold,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CONECTANDO CON EL SERVIDOR",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.em
                    )
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        var isHeroFocused by remember { mutableStateOf(false) }
        var focusTrigger by remember { mutableStateOf(0) }

        // Check if we are returning from a movie detail and restoring focus
        val isRestoringFocus = remember {
            DataCache.globalLastFocusedKey != null && DataCache.globalLastFocusedKey!!.startsWith("home_")
        }

        LaunchedEffect(isHeroFocused) {
            if (isHeroFocused) {
                listState.scrollToItem(0, 0)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item(key = "hero_section") {
                @OptIn(ExperimentalFoundationApi::class)
                val responder = remember {
                    object : BringIntoViewResponder {
                        @Suppress("DEPRECATION")
                        override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                            // Swallow internal requests to prevent centering buttons
                        }
                        @Suppress("DEPRECATION")
                        override fun calculateRectForParent(localRect: Rect): Rect {
                            // Always request the top of the Hero to be visible.
                            // Since Hero is at the top of the list, this prevents downward scrolling.
                            return Rect(0f, 0f, localRect.width, 0f)
                        }
                    }
                }

                @OptIn(ExperimentalFoundationApi::class)
                CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollBringIntoViewSpec) {
                    Box(modifier = Modifier
                        .bringIntoViewResponder(responder)
                        .onFocusChanged {
                        isHeroFocused = it.hasFocus
                        if (it.hasFocus) {
                            initialFocusRequested = true
                        }
                    }) {
                        HeroSection(
                            movies = DataCache.homeHeroMovies,
                            onMovieClick = { onMovieClick(it.id) },
                            onFocused = {
                                focusTrigger++
                                initialFocusRequested = true
                            },
                            initialFocusRequester = if (isRestoringFocus || initialFocusRequested) null else heroFocusRequester,
                            screenKey = "home_hero"
                        )
                    }
                }
            }

            if (historyMovies.isNotEmpty()) {
                item(key = "history_section") {
                    HistoryRow(
                        movies = historyMovies,
                        onMovieClick = onMovieClick,
                        parentListState = listState
                    )
                }
            }

            itemsIndexed(
                items = DataCache.homeCategories,
                key = { _, category -> category.id }
            ) { index, category ->
                CategoryRow(
                    category = category,
                    rowIndex = index,
                    onMovieClick = onMovieClick,
                    onSeeMoreClick = onSeeMoreClick,
                    onFocusGained = { initialFocusRequested = true },
                    parentListState = listState
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategoryRow(
    category: HomeCategory,
    rowIndex: Int,
    onMovieClick: (Int) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onFocusGained: () -> Unit,
    parentListState: LazyListState
) {
    val rawMovies = DataCache.homeCategoryMovies[category.id] ?: emptyList()
    val rowState = rememberLazyListState()
    val firstItemRequester = remember { FocusRequester() }

    // Reset scroll when the row leaves the viewport
    val isVisible by remember {
        derivedStateOf<Boolean> {
            parentListState.layoutInfo.visibleItemsInfo.any { it.index == rowIndex + 1 }
        }
    }

    // This state tracks if we should force focus to the first item on the next entry
    var isFirstEntry by remember(isVisible) { mutableStateOf(true) }

    var isRowFocused by remember { mutableStateOf(false) }
    val titleScale by animateFloatAsState(
        targetValue = if (isRowFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "category_title_scale"
    )

    LaunchedEffect(isVisible) {
        if (!isVisible && rowState.firstVisibleItemIndex > 0) {
            rowState.scrollToItem(0)
        }
    }

    // Each category shows its own movies directly — no cross-row dedup.
    // (Deduplication was causing categories lower in the list to show 0–2 movies
    // because earlier rows had already consumed the shared pool.)
    val displayMovies = remember(rawMovies) { rawMovies.take(15) }

    val remainingCount = remember(rawMovies, DataCache.homeCategoryTotalCounts[category.id]) {
        val total = DataCache.homeCategoryTotalCounts[category.id] ?: rawMovies.size.toLong()
        (total - displayMovies.size).coerceAtLeast(0).toInt()
    }

    // Only fetch movies for this category if they are not already cached
    LaunchedEffect(category.id) {
        if (rawMovies.isEmpty()) {
            try {
                val response = SupabaseManager.client.from("movies")
                    .select {
                        filter {
                            val genres = category.genre_ids
                            val keywords = category.keywords

                            if (!genres.isNullOrEmpty()) {
                                overlaps("genre_ids", genres)
                            }

                            if (!keywords.isNullOrEmpty()) {
                                overlaps("keywords", keywords)
                            }

                            val rating = category.min_rating
                            if (rating != null && rating > 0.0) {
                                gte("vote_average", rating)
                            }
                        }
                        val sortCol = if (category.sort_by.isNullOrBlank()) "created_at" else category.sort_by
                        order(sortCol, Order.DESCENDING)
                        // Use this.count() to avoid naming conflict with the property
                        this.count(Count.EXACT)
                        limit(100)
                    }

                val fetchedMovies = response.decodeList<Movie>()
                DataCache.homeCategoryMovies[category.id] = fetchedMovies
                // Save the absolute total from DB
                DataCache.homeCategoryTotalCounts[category.id] = response.countOrNull() ?: fetchedMovies.size.toLong()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (displayMovies.isNotEmpty() || rawMovies.isEmpty()) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .focusProperties {
                    // Redirect entry focus to the first item if it's the first time entering
                    // or after the row has been reset.
                    enter = {
                        if (isFirstEntry) firstItemRequester else FocusRequester.Default
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 48.dp, bottom = 6.dp)
                    .graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFF2AF),
                                    Gold
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    color = Color.White
                )
            }
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .onFocusChanged { 
                        isRowFocused = it.hasFocus
                        if (it.hasFocus) {
                            onFocusGained()
                            isFirstEntry = false // Memory mode active until it leaves screen
                        }
                    }
            ) {
                if (displayMovies.isEmpty()) {
                    // Show Shimmer placeholders while loading
                    items(6) {
                        MovieCardPlaceholder()
                    }
                } else {
                    itemsIndexed(
                        items = displayMovies,
                        key = { _, movie -> movie.id }
                    ) { index, movie ->
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie.id) },
                            isFirstInRow = index == 0,
                            screenKey = "home_category_${category.id}",
                            modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
                        )
                    }
                    item {
                        SeeMoreCard(
                            remainingCount = remainingCount,
                            onClick = { onSeeMoreClick(category.id) },
                            screenKey = "home_category_${category.id}_seemore"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HistoryRow(
    movies: List<Movie>,
    onMovieClick: (Int) -> Unit,
    parentListState: LazyListState
) {
    val rowState = rememberLazyListState()
    val firstItemRequester = remember { FocusRequester() }
    var isFirstEntry by remember { mutableStateOf(true) }

    var isRowFocused by remember { mutableStateOf(false) }
    val titleScale by animateFloatAsState(
        targetValue = if (isRowFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "history_title_scale"
    )

    Column(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .focusProperties {
                enter = {
                    if (isFirstEntry) firstItemRequester else FocusRequester.Default
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 48.dp, bottom = 6.dp)
                .graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CONTINUAR VIENDO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color(0xFF60A5FA)
            )
        }
        
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .onFocusChanged {
                    isRowFocused = it.hasFocus
                    if (it.hasFocus) {
                        isFirstEntry = false
                    }
                }
        ) {
            itemsIndexed(
                items = movies,
                key = { _, movie -> "history_${movie.id}" }
            ) { index, movie ->
                val progressSec = DataCache.currentUser?.watchProgress?.get(movie.id.toString())?.time ?: 0.0
                HistoryMovieCard(
                    movie = movie,
                    progressSec = progressSec,
                    onClick = { onMovieClick(movie.id) },
                    isFirstInRow = index == 0,
                    screenKey = "home_history",
                    modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
                )
            }
        }
    }
}
