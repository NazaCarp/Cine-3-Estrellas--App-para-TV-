package com.cine3estrellas.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
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
import com.cine3estrellas.TmdbManager
import com.cine3estrellas.mobile.Gold

@Composable
fun MobileMovieDetailsScreen(movieId: Int, navController: NavController) {
    var movie by remember { mutableStateOf<Movie?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(movieId) {
        movie = TmdbManager.getMovieDetails(movieId)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
    } else {
        movie?.let { m ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
            ) {
                // Backdrop with Back Button
                Box(modifier = Modifier.height(300.dp)) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/original${m.backdrop_path}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black),
                                    startY = 200f
                                )
                            )
                    )
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                }

                // Info
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = m.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${m.release_date?.take(4)} • ${m.vote_average} ★",
                        color = Gold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            // Navegar al reproductor con la primera versión disponible
                            val versionKey = m.versions?.keys?.firstOrNull() ?: "Latino"
                            navController.navigate("player/${m.id}/$versionKey")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("REPRODUCIR", fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "SINOPSIS",
                        style = MaterialTheme.typography.titleSmall,
                        color = Gold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = m.overview ?: "No hay sinopsis disponible.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}
