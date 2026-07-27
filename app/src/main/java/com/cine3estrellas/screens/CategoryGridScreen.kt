package com.cine3estrellas.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.cine3estrellas.*
import androidx.activity.compose.BackHandler
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.floor

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategoryGridScreen(
    categoryId: String,
    onMovieClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val isDetailsActive = LocalDetailsActive.current
    var initialFocusRequested by remember(categoryId) { mutableStateOf(false) }
    val category = remember(categoryId) { DataCache.homeCategories.find { it.id == categoryId } }
    var movies by remember(categoryId) { mutableStateOf(DataCache.homeCategoryMovies[categoryId] ?: emptyList()) }
    val totalCount = DataCache.homeCategoryTotalCounts[categoryId] ?: 999999L
    val scope = rememberCoroutineScope()
    val backButtonRequester = remember { FocusRequester() }
    val itemRequesters = remember(categoryId) {
        val list = mutableStateListOf<FocusRequester>()
        repeat(movies.size) { list.add(FocusRequester()) }
        list
    }
    LaunchedEffect(movies.size) {
        while (itemRequesters.size < movies.size) {
            itemRequesters.add(FocusRequester())
        }
    }
    

    var isPageLoading by remember(categoryId) { mutableStateOf(false) }
    var errorMessage by remember(categoryId) { mutableStateOf<String?>(null) }
 
    fun loadNextPage() {
        if (isPageLoading || movies.size >= totalCount || category == null) return
        
        errorMessage = null
        isPageLoading = true
        scope.launch {
            try {
                val fromIndex = movies.size.toLong()
                val toIndex = fromIndex + 99
                val response = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date", "popularity", "created_at")) {
                        filter {
                            val genres = category.genre_ids
                            val keywords = category.keywords
                            if (!genres.isNullOrEmpty()) overlaps("genre_ids", genres)
                            if (!keywords.isNullOrEmpty()) overlaps("keywords", keywords)
                            val rating = category.min_rating
                            if (rating != null && rating > 0.0) gte("vote_average", rating)
                        }
                        val sortCol = if (category.sort_by.isNullOrBlank()) "created_at" else category.sort_by
                        order(sortCol, Order.DESCENDING)
                        range(fromIndex, toIndex)
                    }
                val newMovies = response.decodeList<Movie>()
                if (newMovies.isEmpty()) {
                    DataCache.homeCategoryTotalCounts[categoryId] = movies.size.toLong()
                } else {
                    DataCache.cacheMovies(newMovies)
                    movies = movies + newMovies
                    DataCache.homeCategoryMovies[categoryId] = movies
                    if (newMovies.size < 100) {
                        DataCache.homeCategoryTotalCounts[categoryId] = movies.size.toLong()
                    }
                }
            } catch (e: Exception) {
                if (movies.isEmpty()) {
                    errorMessage = "Error al cargar categoría: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                isPageLoading = false
            }
        }
    }

    LaunchedEffect(categoryId) {
        if (movies.isEmpty() && category != null) {
            loadNextPage()
        }
    }

    LaunchedEffect(movies, isDetailsActive) {
        if (movies.isNotEmpty() && !isDetailsActive && !initialFocusRequested) {
            initialFocusRequested = true
            delay(150) // Wait for layout/composition to settle
            try {
                itemRequesters.getOrNull(0)?.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isDetailsActive) {
        if (!isDetailsActive && movies.isNotEmpty()) {
            val restoreId = DataCache.movieIdToRestore ?: DataCache.lastFocusedMovieId["category_$categoryId"]
            if (restoreId != null) {
                val index = movies.indexOfFirst { it.id == restoreId }
                if (index != -1) {
                    delay(350) // Esperar a que termine la animación de salida de la ficha
                    try {
                        itemRequesters.getOrNull(index)?.requestFocus()
                        DataCache.movieIdToRestore = null
                        DataCache.keyToRestore = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (errorMessage != null && movies.isEmpty()) {
            ErrorScreen(message = errorMessage!!, onRetry = { loadNextPage() })
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val horizontalPadding = 96.dp  // 48.dp start + 48.dp end
                val itemWidth = 110.dp
                val itemSpacing = 20.dp
                val availableWidth = maxWidth - horizontalPadding
                val columnCount = maxOf(1, floor(
                    (availableWidth.value + itemSpacing.value) / (itemWidth.value + itemSpacing.value)
                ).toInt())

                val lazyGridState = rememberLazyGridState()

                // Rastrea qué ítem tiene el foco actualmente (para el manejo manual de UP)
                var focusedIndex by remember { mutableStateOf(0) }

                LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                state = lazyGridState,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Interceptamos D-pad UP para garantizar que el ítem destino
                    // esté compuesto antes de intentar enfocar (fix para foco perdido al subir rápido)
                    .onPreviewKeyEvent { event ->
                        val nativeEvent = event.nativeKeyEvent
                        if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                            val target = focusedIndex - columnCount
                            if (target < 0) {
                                // Primera fila: ir al botón Volver
                                backButtonRequester.requestFocus()
                            } else {
                                scope.launch {
                                    // Primero scroll al ítem destino para que se componga
                                    lazyGridState.scrollToItem(maxOf(0, target - columnCount))
                                    delay(32) // Esperar 2 frames para que se componga
                                    itemRequesters.getOrNull(target)?.requestFocus()
                                }
                            }
                            true // Consumir el evento siempre
                        } else false
                    },
                contentPadding = PaddingValues(
                    start = 48.dp,
                    end = 48.dp,
                    top = 160.dp,
                    bottom = 48.dp
                )
            ) {
                itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                    // Trigger next page load when near end
                    if (index >= movies.size - 20 && !isPageLoading && movies.size < totalCount) {
                        LaunchedEffect(Unit) {
                            loadNextPage()
                        }
                    }

                    val isFirstInRowItem = index % columnCount == 0
                    val isLastInRowItem = (index + 1) % columnCount == 0 || index == movies.size - 1

                    // Derecha desde última columna → primer ítem de la fila siguiente
                    val rowRightFocus = if (isLastInRowItem) {
                        val nextRowFirst = index + 1
                        if (nextRowFirst < movies.size) itemRequesters.getOrNull(nextRowFirst) ?: FocusRequester.Cancel
                        else FocusRequester.Cancel
                    } else null

                    // Izquierda desde primera columna → último ítem de la fila anterior
                    val rowLeftFocus = if (isFirstInRowItem && index > 0) {
                        itemRequesters.getOrNull(index - 1)
                    } else null

                    // Envolvemos con onFocusChanged para trackear qué ítem tiene foco
                    Box(modifier = Modifier.onFocusChanged {
                        if (it.hasFocus) focusedIndex = index
                    }) {
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie.id) },
                            screenKey = "category_$categoryId",
                            focusRequester = if (index < itemRequesters.size) itemRequesters[index] else remember { FocusRequester() },
                            nextFocusRequester = if (index + 1 < itemRequesters.size) itemRequesters[index + 1] else null,
                            rightFocus = rowRightFocus,
                            leftFocus = rowLeftFocus
                        )
                    }
                }
                
                if (isPageLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Cargando más...", color = Gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            }
        } // end else

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Black.copy(alpha = 0.9f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        colors = SurfaceDefaults.colors(containerColor = Gold.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "${totalCount} TÍTULOS DISPONIBLES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gold,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp
                        )
                    }
                    
                    Text(
                        text = category?.name ?: "",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backButtonRequester)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            if (itemRequesters.isNotEmpty()) {
                                down = itemRequesters[0]
                            }
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
                        Text("VOLVER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
