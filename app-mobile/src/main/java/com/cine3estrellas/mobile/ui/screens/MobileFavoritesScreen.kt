package com.cine3estrellas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.launch

@Composable
fun MobileFavoritesScreen(navController: NavController) {
    val favoriteIds = remember(DataCache.currentUser) {
        DataCache.currentUser?.favorites?.map { it.id }?.reversed() ?: emptyList()
    }
    var favoriteMovies by remember { mutableStateOf(emptyList<Movie>()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoriteIds) {
        if (favoriteIds.isEmpty()) {
            favoriteMovies = emptyList()
            return@LaunchedEffect
        }
        
        val missingIds = favoriteIds.filter { !DataCache.movieCardCache.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            isLoading = true
            try {
                val fetched = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date")) { 
                        filter { isIn("id", missingIds) } 
                    }
                    .decodeList<Movie>()
                DataCache.cacheMovies(fetched)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
        favoriteMovies = favoriteIds.mapNotNull { id -> DataCache.movieCardCache[id] }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = "MIS FAVORITOS",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading && favoriteMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        } else if (favoriteMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes favoritos", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoriteMovies) { movie ->
                    MobileMovieCard(
                        movie = movie,
                        onClick = { navController.navigate("details/${movie.id}") }
                    )
                }
            }
        }
    }
}
