package com.cine3estrellas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cine3estrellas.Movie
import com.cine3estrellas.SupabaseManager
import com.cine3estrellas.TvHomeResponse
import com.cine3estrellas.mobile.Gold
import com.cine3estrellas.mobile.ui.MobileMovieCard

@Composable
fun MobileHomeScreen(navController: NavController) {
    var homeData by remember { mutableStateOf<TvHomeResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        homeData = SupabaseManager.fetchTvHomeData()
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Hero Section (Simple for now)
            item {
                homeData?.heroMovies?.firstOrNull()?.let { movie ->
                    MobileHeroSection(movie)
                }
            }

            // Categories
            homeData?.categories?.forEach { category ->
                if (category.movies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = category.name.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Gold,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                letterSpacing = 1.sp
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(category.movies) { movie ->
                                    MobileMovieCard(
                                        movie = movie,
                                        onClick = { /* Navegar a detalles */ }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileHeroSection(movie: Movie) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/original${movie.poster_path}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black),
                        startY = 300f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = movie.release_date?.take(4) ?: "",
                    color = Gold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALIDAD HD",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
