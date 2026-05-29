package com.cine3estrellas.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.cine3estrellas.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(onMovieClick: (Int) -> Unit) {
    // In a real app, this would come from a local database (Room) or Supabase user data
    var favoriteMovies by remember { mutableStateOf(emptyList<Movie>()) }

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("MIS FAVORITOS", style = MaterialTheme.typography.displaySmall, color = Gold)
        Spacer(modifier = Modifier.height(24.dp))

        if (favoriteMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tienes películas guardadas aún.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(favoriteMovies) { movie ->
                    MovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
                }
            }
        }
    }
}
