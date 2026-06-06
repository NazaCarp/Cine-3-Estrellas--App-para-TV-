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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.cine3estrellas.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategoryGridScreen(
    categoryId: String,
    onMovieClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val category = remember(categoryId) { DataCache.homeCategories.find { it.id == categoryId } }
    var movies by remember { mutableStateOf(DataCache.homeCategoryMovies[categoryId] ?: emptyList()) }
    val totalCount = DataCache.homeCategoryTotalCounts[categoryId] ?: 999999L
    val scope = rememberCoroutineScope()
    val backButtonRequester = remember { FocusRequester() }
    val itemRequesters = remember { 
        val list = mutableStateListOf<FocusRequester>()
        repeat(movies.size) { list.add(FocusRequester()) }
        list
    }
    LaunchedEffect(movies.size) {
        while (itemRequesters.size < movies.size) {
            itemRequesters.add(FocusRequester())
        }
    }
    
    var isPageLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
 
    fun loadNextPage() {
        if (isPageLoading || movies.size >= totalCount || category == null) return
        
        errorMessage = null
        isPageLoading = true
        scope.launch {
            try {
                val fromIndex = movies.size.toLong()
                val toIndex = fromIndex + 99
                val response = SupabaseManager.client.from("movies")
                    .select {
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

    LaunchedEffect(movies.size) {
        // Removed forced focus to allow natural restoration
        /*
        if (movies.size > 0 && movies.size <= 100) {
            kotlinx.coroutines.delay(100)
            try { itemRequesters.getOrNull(0)?.requestFocus() } catch (e: Exception) {}
        }
        */
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (errorMessage != null && movies.isEmpty()) {
            ErrorScreen(message = errorMessage!!, onRetry = { loadNextPage() })
        } else {
            LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 48.dp,
                end = 48.dp,
                top = 160.dp,
                bottom = 48.dp
            )
        ) {
            itemsIndexed(movies) { index, movie ->
                // Trigger next page load when near end
                if (index >= movies.size - 20 && !isPageLoading && movies.size < totalCount) {
                    LaunchedEffect(Unit) {
                        loadNextPage()
                    }
                }

                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie.id) },
                    screenKey = "category_$categoryId",
                    focusRequester = if (index < itemRequesters.size) itemRequesters[index] else remember { FocusRequester() },
                    nextFocusRequester = if (index + 1 < itemRequesters.size) itemRequesters[index + 1] else null,
                    upFocus = if (index < 3) backButtonRequester else null
                )
            }
            
            if (isPageLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Cargando más...", color = Gold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

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
}
