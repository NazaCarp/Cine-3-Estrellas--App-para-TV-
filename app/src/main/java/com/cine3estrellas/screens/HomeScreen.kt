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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.cine3estrellas.*
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

    // Track if we've already handled the initial focus for this session
    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }

    fun loadHomeData() {
        errorMessage = null
        scope.launch {
            if (DataCache.isHomeInitialLoaded) return@launch

            try {
                // 1. Fetch Categories list
                val fetchedCategories = SupabaseManager.client.from("home_categories")
                    .select {
                        filter { eq("show_in_home", true) }
                    }.decodeList<HomeCategory>()

                // 2. Fetch the 50 newest movies to determine category freshness
                val recentMovies = SupabaseManager.client.from("movies")
                    .select {
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(50)
                    }.decodeList<Movie>()

                // 3. Separate fixed categories from dynamic ones
                val fixedFirst = fetchedCategories.find { it.name.equals("Recién Agregadas", ignoreCase = true) }
                val fixedSecond = fetchedCategories.find { it.name.equals("Lo más visto", ignoreCase = true) }
                val fixedSixth = fetchedCategories.find { it.name.equals("Más Valoradas", ignoreCase = true) }

                val remainingCategories = fetchedCategories.filter {
                    it.id != fixedFirst?.id && it.id != fixedSecond?.id && it.id != fixedSixth?.id
                }

                // Rank remaining categories by their newest movie date
                val dynamicSorted = remainingCategories.map { category ->
                    val newestMovieInCategory = recentMovies.firstOrNull { movie ->
                        val matchGenre = !category.genre_ids.isNullOrEmpty() &&
                                movie.genre_ids?.any { it in category.genre_ids!! } ?: false
                        val matchKeyword = !category.keywords.isNullOrEmpty() &&
                                movie.keywords?.any { it in category.keywords!! } ?: false
                        matchGenre || matchKeyword
                    }
                    category to (newestMovieInCategory?.created_at ?: "1900-01-01")
                }.sortedByDescending { it.second }.map { it.first }.toMutableList()

                // Reassemble the list with fixed positions
                val finalCategories = mutableListOf<HomeCategory>()

                // 1st position
                fixedFirst?.let { finalCategories.add(it) }

                // 2nd position
                fixedSecond?.let { finalCategories.add(it) }

                // Positions 3, 4, 5 from dynamic content
                repeat(3) {
                    if (dynamicSorted.isNotEmpty()) finalCategories.add(dynamicSorted.removeAt(0))
                }

                // 6th position
                fixedSixth?.let { finalCategories.add(it) }

                // Remaining dynamic content
                finalCategories.addAll(dynamicSorted)

                DataCache.homeCategories = finalCategories

                // 4. Fetch movies for Hero
                val baseHeroMovies = recentMovies.take(7)

                // 3. Fetch TMDB details ONLY for the hero section (happens once)
                val detailedHeroMovies = coroutineScope {
                    baseHeroMovies.map { movie ->
                        async {
                            try {
                                val details = TmdbManager.getMovieDetails(movie.id)
                                movie.copy(
                                    overview = details?.overview ?: movie.overview,
                                    runtime = details?.runtime ?: movie.runtime,
                                    genres = details?.genres ?: movie.genres,
                                    backdrop_path = details?.backdrop_path ?: movie.backdrop_path,
                                    vote_average = details?.vote_average ?: movie.vote_average,
                                    original_language = details?.original_language ?: movie.original_language
                                )
                            } catch (e: Exception) {
                                movie
                            }
                        }
                    }.awaitAll()
                }
                DataCache.homeHeroMovies = detailedHeroMovies
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
            Text("Conectando...", color = Gold, style = MaterialTheme.typography.headlineSmall)
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

    LaunchedEffect(isVisible) {
        if (!isVisible && rowState.firstVisibleItemIndex > 0) {
            rowState.scrollToItem(0)
        }
    }

    // Calculate which IDs are already shown in PREVIOUS ROWS ONLY (Ignore Hero)
    val alreadyShownInRows = remember(DataCache.homeCategoryMovies.size) {
        val ids = mutableSetOf<Int>()
        for (i in 0 until rowIndex) {
            val prevCatId = DataCache.homeCategories.getOrNull(i)?.id
            if (prevCatId != null) {
                DataCache.homeCategoryMovies[prevCatId]?.forEach { ids.add(it.id) }
            }
        }
        ids
    }

    // Filter the movies for this row
    val filteredMovies = remember(rawMovies, alreadyShownInRows) {
        rawMovies.filter { it.id !in alreadyShownInRows }
    }

    val displayMovies = remember(filteredMovies) {
        filteredMovies.take(15)
    }

    val remainingCount = remember(filteredMovies, displayMovies, DataCache.homeCategoryTotalCounts[category.id]) {
        val totalAvailable = DataCache.homeCategoryTotalCounts[category.id] ?: filteredMovies.size.toLong()
        (totalAvailable - displayMovies.size).coerceAtLeast(0).toInt()
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

    if (displayMovies.isNotEmpty() || (rawMovies.isEmpty())) {
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
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                color = Gold
            )
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .onFocusChanged { 
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
