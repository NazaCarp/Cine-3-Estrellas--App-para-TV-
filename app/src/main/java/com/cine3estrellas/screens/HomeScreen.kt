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
import com.cine3estrellas.LocalTabContentFocusRequesters
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ChevronRight

// Using DataCache instead of local HomeCache

@OptIn(ExperimentalFoundationApi::class)
private val NoScrollBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        0f
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onSeeMoreHistory: () -> Unit,
    heroDownFocus: FocusRequester? = null
) {
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current
    val sidebarRequester = if (sidebarRequesters.isNotEmpty() && selectedTab in sidebarRequesters.indices) {
        sidebarRequesters[selectedTab]
    } else null

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadingFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }
    val seeMoreHistoryRequester = remember { FocusRequester() }

    val contentRequesters = LocalTabContentFocusRequesters.current
    val homeEntryFocusRequester = contentRequesters.getOrNull(1) ?: remember { FocusRequester() }

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
            val missingIds = historyIds.filter { !DataCache.movieCardCache.containsKey(it) }
            if (missingIds.isNotEmpty()) {
                val fetched = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date")) {
                        filter {
                            isIn("id", missingIds)
                        }
                    }.decodeList<Movie>()
                DataCache.cacheMovies(fetched)
            }
            historyMovies = historyIds.mapNotNull { id -> DataCache.movieCardCache[id] }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadHomeData(force: Boolean = false) {
        errorMessage = null
        scope.launch {
            if (!force && DataCache.isHomeInitialLoaded) return@launch

            try {
                val response = SupabaseManager.fetchTvHomeData()
                if (response == null || response.categories.isEmpty()) {
                    throw Exception("El servidor no devolvió datos válidos.")
                }

                // Reuse popular movies from "Lo Más Visto" category for the Hero section
                // This avoids an extra API call.
                val popularCategory = response.categories.find {
                    it.sort_by == "popularity" || it.id == "popular" || it.name.contains(
                        "Más Visto",
                        ignoreCase = true
                    )
                }

                if (popularCategory != null && popularCategory.movies.isNotEmpty()) {
                    val baseMovies = popularCategory.movies.take(10)
                    DataCache.homeHeroMovies = baseMovies

                    // Enrich Hero movies with full metadata (overview, runtime, etc.) from TMDB
                    scope.launch {
                        val enrichedMovies = baseMovies.map { movie ->
                            async {
                                try {
                                    val tmdbDetails = TmdbManager.getMovieDetails(movie.id)
                                    if (tmdbDetails != null) {
                                        movie.copy(
                                            overview = tmdbDetails.overview ?: movie.overview,
                                            runtime = tmdbDetails.runtime ?: movie.runtime,
                                            genres = tmdbDetails.genres ?: movie.genres,
                                            backdrop_path = tmdbDetails.backdrop_path
                                                ?: movie.backdrop_path,
                                            vote_average = tmdbDetails.vote_average
                                                ?: movie.vote_average,
                                            certification = tmdbDetails.certification
                                                ?: movie.certification
                                        )
                                    } else movie
                                } catch (e: Exception) {
                                    movie
                                }
                            }
                        }.awaitAll()
                        DataCache.homeHeroMovies = enrichedMovies
                    }
                } else {
                    DataCache.homeHeroMovies = response.heroMovies
                }

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
                    DataCache.cacheMovies(tvCat.movies)
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

    // Improved Focus Management for loading transition (Iteration 3)
    LaunchedEffect(DataCache.isHomeInitialLoaded) {
        if (!DataCache.isHomeInitialLoaded) {
            // While loading, be extremely aggressive in claiming focus 
            // to suppress any system-initiated focus shifts to the Sidebar.
            repeat(15) {
                try {
                    loadingFocusRequester.requestFocus()
                } catch (e: Exception) {}
                delay(100)
            }
        } else {
            // Loading finished. Transfer focus to content.
            // We wait to ensure the HeroSection and its buttons are ready.
            repeat(5) {
                delay(150)
                try {
                    homeEntryFocusRequester.requestFocus()
                } catch (e: Exception) {}
            }
            // Once focus is settled in Home, unlock the sidebar for fluid navigation
            DataCache.isTabChangeLocked = false
        }
    }

    if (errorMessage != null && !DataCache.isHomeInitialLoaded) {
        ErrorScreen(
            message = errorMessage!!,
            onRetry = { loadHomeData(force = true) }
        )
    } else if (DataCache.homeCategories.isEmpty() && !DataCache.isHomeInitialLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(loadingFocusRequester)
                .focusable()
                .onFocusChanged {
                    if (it.isFocused) {
                        // Keep focus here while loading
                    }
                },
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
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(homeEntryFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            val lastKey = DataCache.lastHomeFocusedKey
                            if (lastKey != null) {
                                // IMPORTANT: Sync global key so components recognize they should restore focus
                                DataCache.globalLastFocusedKey = lastKey

                                if (lastKey == "home_hero") {
                                    heroFocusRequester.requestFocus()
                                } else if (lastKey == "home_history_seemore") {
                                    seeMoreHistoryRequester.requestFocus()
                                } else {
                                    DataCache.focusRestorationTrigger++
                                }
                            } else {
                                heroFocusRequester.requestFocus()
                            }
                        }
                    }
                    .focusable()
            )

            val savedHomePosition = DataCache.homeScrollPosition
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedHomePosition.first,
                initialFirstVisibleItemScrollOffset = savedHomePosition.second
            )

            // Save vertical scroll position when it changes
            LaunchedEffect(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            ) {
                DataCache.homeScrollPosition =
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }

            var isHeroFocused by remember { mutableStateOf(false) }
            var focusTrigger by remember { mutableStateOf(0) }

            // Check if we are returning from a movie detail and restoring focus
            val isRestoringFocus = remember {
                DataCache.globalLastFocusedKey != null && DataCache.globalLastFocusedKey!!.startsWith(
                    "home_"
                )
            }

            LaunchedEffect(isHeroFocused) {
                if (isHeroFocused) {
                    listState.scrollToItem(0, 0)
                }
            }

            // Handle focus restoration when returning from overlays
            LaunchedEffect(DataCache.focusRestorationTrigger) {
                if (DataCache.focusRestorationTrigger > 0) {
                    val key = DataCache.lastHomeFocusedKey
                    if (key == "home_history_seemore") {
                        try {
                            seeMoreHistoryRequester.requestFocus()
                        } catch (e: Exception) {
                        }
                    }
                }
            }

            val deduplicatedCategoryMovies by remember {
                derivedStateOf {
                    computeDeduplicatedCarousels(
                        DataCache.homeCategories,
                        DataCache.homeCategoryMovies
                    )
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
                        Box(
                            modifier = Modifier
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
                                    DataCache.lastHomeFocusedKey = "home_hero"
                                },
                                heroFocusRequester = heroFocusRequester,
                                requestInitialFocus = !isRestoringFocus && !initialFocusRequested,
                                downFocus = if (historyMovies.isNotEmpty()) seeMoreHistoryRequester else null,
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
                            onSeeMoreClick = onSeeMoreHistory,
                            parentListState = listState,
                            seeMoreRequester = seeMoreHistoryRequester,
                            heroFocusRequester = heroFocusRequester,
                            leftFocus = sidebarRequester
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
                        hasHistory = historyMovies.isNotEmpty(),
                        displayMovies = deduplicatedCategoryMovies[category.id] ?: emptyList(),
                        onMovieClick = onMovieClick,
                        onSeeMoreClick = onSeeMoreClick,
                        onFocusGained = { initialFocusRequested = true },
                        parentListState = listState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategoryRow(
    category: HomeCategory,
    rowIndex: Int,
    hasHistory: Boolean,
    displayMovies: List<Movie>,
    onMovieClick: (Int) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onFocusGained: () -> Unit,
    parentListState: LazyListState
) {
    val rowKey = "home_category_${category.id}"
    val rawMovies = DataCache.homeCategoryMovies[category.id] ?: emptyList()

    // Restore scroll position from cache
    val savedPosition = DataCache.rowScrollPositions[rowKey] ?: (0 to 0)
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPosition.first,
        initialFirstVisibleItemScrollOffset = savedPosition.second
    )

    // Save scroll position when it changes
    LaunchedEffect(rowState.firstVisibleItemIndex, rowState.firstVisibleItemScrollOffset) {
        DataCache.rowScrollPositions[rowKey] =
            rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset
    }

    val itemRequesters =
        remember(displayMovies.size) { List(displayMovies.size) { FocusRequester() } }
    val seeMoreRequester = remember { FocusRequester() }

    val lastFocusedId = DataCache.lastFocusedMovieId[rowKey]
    val lastFocusedRequester = remember(lastFocusedId, displayMovies, itemRequesters.size) {
        val lastIdx = displayMovies.indexOfFirst { it.id == lastFocusedId }
        if (lastIdx != -1) itemRequesters.getOrNull(lastIdx) else null
    }

    val isSeeMoreFocusedLast = remember {
        derivedStateOf { DataCache.lastHomeFocusedKey == "${rowKey}_seemore" }
    }

    val isVisible by remember {
        derivedStateOf {
            val visibleItems = parentListState.layoutInfo.visibleItemsInfo
            val actualIndex = if (hasHistory) rowIndex + 2 else rowIndex + 1
            visibleItems.any { it.index == actualIndex }
        }
    }

    // This state tracks if we should force focus to the first item on the next entry
    var isFirstEntry by remember(isVisible) { mutableStateOf(true) }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            try {
                rowState.scrollToItem(0, 0)
            } catch (e: Exception) {
            }
            DataCache.rowScrollPositions[rowKey] = 0 to 0
            DataCache.lastFocusedMovieId.remove("home_category_${category.id}")
            isFirstEntry = true
        }
    }

    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current

    var isRowFocused by remember { mutableStateOf(false) }
    val titleScale by animateFloatAsState(
        targetValue = if (isRowFocused) 1.1f else 1.0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "category_title_scale"
    )

    val remainingCount =
        remember(rawMovies, DataCache.homeCategoryTotalCounts[category.id], displayMovies.size) {
            val total = DataCache.homeCategoryTotalCounts[category.id] ?: rawMovies.size.toLong()
            (total - displayMovies.size).coerceAtLeast(0).toInt()
        }



    if (displayMovies.isNotEmpty() || rawMovies.isEmpty()) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .focusProperties {
                    enter = {
                        if (isFirstEntry) {
                            itemRequesters.getOrNull(0) ?: FocusRequester.Default
                        } else {
                            if (isSeeMoreFocusedLast.value) {
                                seeMoreRequester
                            } else {
                                lastFocusedRequester ?: FocusRequester.Default
                            }
                        }
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
                contentPadding = PaddingValues(
                    start = 48.dp,
                    end = 48.dp,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .onFocusChanged {
                        isRowFocused = it.hasFocus
                        if (it.hasFocus && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                            onFocusGained()
                            isFirstEntry = false
                            DataCache.globalLastFocusedKey = rowKey // Mark row as active
                            DataCache.lastHomeFocusedKey = rowKey
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
                        key = { index, movie -> "${category.id}_${movie.id}_$index" }
                    ) { index, movie ->
                        val fr = itemRequesters.getOrNull(index) ?: remember { FocusRequester() }
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie.id) },
                            isFirstInRow = index == 0,
                            screenKey = "home_category_${category.id}",
                            focusRequester = fr
                        )
                    }
                    item {
                        SeeMoreCard(
                            remainingCount = remainingCount,
                            onClick = { onSeeMoreClick(category.id) },
                            screenKey = "home_category_${category.id}_seemore",
                            focusRequester = seeMoreRequester
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
    onSeeMoreClick: () -> Unit,
    parentListState: LazyListState,
    seeMoreRequester: FocusRequester,
    heroFocusRequester: FocusRequester,
    leftFocus: FocusRequester? = null
) {
    val rowKey = "home_history"
    val savedPosition = DataCache.rowScrollPositions[rowKey] ?: (0 to 0)
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPosition.first,
        initialFirstVisibleItemScrollOffset = savedPosition.second
    )

    LaunchedEffect(rowState.firstVisibleItemIndex, rowState.firstVisibleItemScrollOffset) {
        DataCache.rowScrollPositions[rowKey] =
            rowState.firstVisibleItemIndex to rowState.firstVisibleItemScrollOffset
    }

    val itemRequesters = remember(movies.size) { List(movies.size) { FocusRequester() } }

    val lastFocusedId = DataCache.lastFocusedMovieId[rowKey]
    val lastFocusedRequester = remember(lastFocusedId, movies, itemRequesters.size) {
        val lastIdx = movies.indexOfFirst { it.id == lastFocusedId }
        if (lastIdx != -1) itemRequesters.getOrNull(lastIdx) else null
    }

    val isSeeMoreFocusedLast = remember {
        derivedStateOf { DataCache.lastHomeFocusedKey == "${rowKey}_seemore" }
    }

    val isVisible by remember {
        derivedStateOf {
            val visibleItems = parentListState.layoutInfo.visibleItemsInfo
            visibleItems.any { it.index == 1 }
        }
    }

    val isDetailsActive = LocalDetailsActive.current
    val isCategoryActive = LocalCategoryActive.current
    val isPlayerActive = LocalPlayerOverlayActive.current
    var isFirstEntry by remember(isVisible) { mutableStateOf(true) }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            try {
                rowState.scrollToItem(0, 0)
            } catch (e: Exception) {
            }
            DataCache.rowScrollPositions[rowKey] = 0 to 0
            DataCache.lastFocusedMovieId.remove("home_history")
            isFirstEntry = true
        }
    }

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
                    if (isFirstEntry) {
                        itemRequesters.getOrNull(0) ?: FocusRequester.Default
                    } else {
                        if (isSeeMoreFocusedLast.value) {
                            seeMoreRequester
                        } else {
                            lastFocusedRequester ?: FocusRequester.Default
                        }
                    }
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
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

            // Pastilla VER TODO
            var isSeeMoreFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onSeeMoreClick,
                modifier = Modifier
                    .focusRequester(seeMoreRequester)
                    .onFocusChanged {
                        isSeeMoreFocused = it.isFocused
                        if (it.isFocused && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                            DataCache.globalLastFocusedKey = "${rowKey}_seemore"
                            DataCache.lastHomeFocusedKey = "${rowKey}_seemore"
                        }
                    }
                    .focusProperties {
                        up = heroFocusRequester
                        down = if (isFirstEntry) {
                            if (itemRequesters.isNotEmpty()) itemRequesters[0] else FocusRequester.Default
                        } else {
                            lastFocusedRequester
                                ?: (if (itemRequesters.isNotEmpty()) itemRequesters[0] else FocusRequester.Default)
                        }
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color(0xFF60A5FA).copy(alpha = 0.2f)
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    focusedBorder = Border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFF60A5FA)
                        )
                    )
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VER TODO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = if (isSeeMoreFocused) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isSeeMoreFocused) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .onFocusChanged {
                    isRowFocused = it.hasFocus
                    if (it.hasFocus && !isDetailsActive && !isCategoryActive && !isPlayerActive) {
                        isFirstEntry = false
                        DataCache.globalLastFocusedKey = rowKey
                        DataCache.lastHomeFocusedKey = rowKey
                    }
                }
        ) {
            itemsIndexed(
                items = movies,
                key = { _, movie -> "history_${movie.id}" }
            ) { index, movie ->
                val progressSec =
                    DataCache.currentUser?.watchProgress?.get(movie.id.toString())?.time ?: 0.0
                val fr = itemRequesters.getOrNull(index) ?: remember { FocusRequester() }
                HistoryMovieCard(
                    movie = movie,
                    progressSec = progressSec,
                    onClick = { onMovieClick(movie.id) },
                    isFirstInRow = index == 0,
                    screenKey = "home_history",
                    focusRequester = fr,
                    upFocus = seeMoreRequester,
                    leftFocus = if (index == 0) leftFocus else null
                )
            }
        }
    }
}

private fun computeDeduplicatedCarousels(
    categories: List<HomeCategory>,
    categoryMoviesMap: Map<String, List<Movie>>
): Map<String, List<Movie>> {
    val result = mutableMapOf<String, List<Movie>>()
    val shownMovieIds = mutableSetOf<Int>()

    for (category in categories) {
        val rawMovies = categoryMoviesMap[category.id] ?: emptyList()
        if (rawMovies.isEmpty()) {
            result[category.id] = emptyList()
            continue
        }

        val unrepeated = mutableListOf<Movie>()
        val repeated = mutableListOf<Movie>()
        val seenInRaw = mutableSetOf<Int>()

        for (movie in rawMovies) {
            if (!seenInRaw.add(movie.id)) continue

            if (movie.id !in shownMovieIds) {
                unrepeated.add(movie)
            } else {
                repeated.add(movie)
            }
        }

        val displayList = mutableListOf<Movie>()

        if (unrepeated.size >= 15) {
            displayList.addAll(unrepeated.take(15))
        } else {
            displayList.addAll(unrepeated)
            val needed = 15 - displayList.size
            displayList.addAll(repeated.take(needed))
        }

        for (movie in displayList) {
            shownMovieIds.add(movie.id)
        }

        result[category.id] = displayList
    }

    return result
}
