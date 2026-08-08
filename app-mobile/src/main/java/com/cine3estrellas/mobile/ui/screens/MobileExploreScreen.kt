package com.cine3estrellas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cine3estrellas.*
import com.cine3estrellas.mobile.Gold
import com.cine3estrellas.mobile.ui.MobileMovieCard
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

@Composable
fun MobileExploreScreen(navController: NavController) {
    var genres by remember { mutableStateOf(DataCache.exploreGenres) }
    var selectedGenre by remember { mutableStateOf(DataCache.exploreSelectedGenre) }
    var movies by remember { mutableStateOf(DataCache.exploreMovies) }
    var isLoading by remember { mutableStateOf(false) }
    var totalCount by remember { mutableStateOf(DataCache.exploreTotalCount) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (genres.isEmpty()) {
            genres = TmdbManager.getGenres()
            DataCache.exploreGenres = genres
            if (selectedGenre == null) {
                selectedGenre = genres.firstOrNull()
            }
        }
    }

    fun loadMovies() {
        if (selectedGenre == null) return
        scope.launch {
            isLoading = true
            try {
                val response = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date", "popularity")) {
                        filter {
                            overlaps("genre_ids", listOf(selectedGenre!!.id))
                        }
                        order("popularity", Order.DESCENDING)
                        count(Count.EXACT)
                        range(0, 60)
                    }

                val newResults = response.decodeList<Movie>()
                DataCache.cacheMovies(newResults)
                movies = newResults
                totalCount = response.countOrNull() ?: newResults.size.toLong()
                
                // Sync to cache
                DataCache.exploreSelectedGenre = selectedGenre
                DataCache.exploreMovies = movies
                DataCache.exploreTotalCount = totalCount
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedGenre) {
        if (selectedGenre != null) {
            loadMovies()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Genre Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(genres) { genre ->
                FilterChip(
                    selected = selectedGenre?.id == genre.id,
                    onClick = { selectedGenre = genre },
                    label = { Text(genre.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold,
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF1A1A1A),
                        labelColor = Color.White
                    ),
                    border = null
                )
            }
        }

        if (isLoading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(movies) { movie ->
                    MobileMovieCard(
                        movie = movie,
                        onClick = { navController.navigate("details/${movie.id}") }
                    )
                }
            }
        }
    }
}
