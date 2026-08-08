package com.cine3estrellas.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cine3estrellas.*
import com.cine3estrellas.mobile.Gold
import com.cine3estrellas.mobile.ui.MobileMovieCard
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf(DataCache.searchQuery) }
    var searchResults by remember { mutableStateOf(DataCache.searchResults) }
    var isLoading by remember { mutableStateOf(false) }
    var totalCount by remember { mutableLongStateOf(DataCache.searchTotalCount) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun performSearch() {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        
        scope.launch {
            isLoading = true
            try {
                val response = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date", "popularity")) {
                        filter {
                            ilike("title", "%$searchQuery%")
                        }
                        count(Count.EXACT)
                        range(0, 50)
                    }

                val newResults = response.decodeList<Movie>()
                DataCache.cacheMovies(newResults)
                searchResults = newResults
                totalCount = response.countOrNull() ?: newResults.size.toLong()
                
                // Sync to cache
                DataCache.searchQuery = searchQuery
                DataCache.searchResults = searchResults
                DataCache.searchTotalCount = totalCount
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Buscar películas...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Gold) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        searchResults = emptyList()
                        DataCache.searchQuery = ""
                        DataCache.searchResults = emptyList()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { performSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Color.DarkGray,
                cursorColor = Gold
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        } else if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
            // Empty State with QR (Adapted from TV)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No encontramos resultados",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pídela en nuestro grupo de Telegram",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Image(
                        painter = painterResource(id = R.drawable.cine_3_estrellas_grupo),
                        contentDescription = "QR Telegram",
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { movie ->
                    MobileMovieCard(
                        movie = movie,
                        onClick = { navController.navigate("details/${movie.id}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
