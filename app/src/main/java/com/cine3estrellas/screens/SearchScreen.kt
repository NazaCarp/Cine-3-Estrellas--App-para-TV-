package com.cine3estrellas.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.cine3estrellas.R
import com.cine3estrellas.LocalTabContentFocusRequesters
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import com.cine3estrellas.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(onMovieClick: (Int) -> Unit) {
    val isDetailsActive = LocalDetailsActive.current
    // Using DataCache to preserve state across navigation
    var searchQuery by remember { mutableStateOf(DataCache.searchQuery) }
    var searchResults by remember { mutableStateOf(DataCache.searchResults) }
    var isLoading by remember { mutableStateOf(false) }
    var totalCount by remember { mutableLongStateOf(DataCache.searchTotalCount) }
    var lastSearchedQuery by remember { mutableStateOf(DataCache.searchLastSearchedQuery) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    val queryScrollState = rememberScrollState()

    val contentRequesters = LocalTabContentFocusRequesters.current
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardRightFocusRequester = remember { FocusRequester() }
    val entryFocusRequester = contentRequesters.getOrNull(0) ?: remember { FocusRequester() }
    val focusRequesters = remember(searchResults.size) { List(searchResults.size) { FocusRequester() } }

    val lastFocusedId = DataCache.lastFocusedMovieId["search"]
    val targetIndex = remember(searchResults, lastFocusedId) {
        val idx = if (lastFocusedId != null) {
            searchResults.indexOfFirst { it.id == lastFocusedId }
        } else -1
        if (idx != -1) idx else 0
    }

    // Sync local state to DataCache
    LaunchedEffect(searchQuery) {
        DataCache.searchQuery = searchQuery
        queryScrollState.animateScrollTo(queryScrollState.maxValue)
    }

    LaunchedEffect(searchResults) {
        DataCache.searchResults = searchResults
    }

    LaunchedEffect(totalCount) {
        DataCache.searchTotalCount = totalCount
    }

    LaunchedEffect(lastSearchedQuery) {
        DataCache.searchLastSearchedQuery = lastSearchedQuery
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    LaunchedEffect(searchQuery) {
        queryScrollState.animateScrollTo(queryScrollState.maxValue)
    }

    fun performSearch(isNewSearch: Boolean = true) {
        if (searchQuery.isBlank()) return
        if (!isNewSearch && (isLoading || searchResults.size >= totalCount)) return

        errorMessage = null
        if (isNewSearch) {
            lastSearchedQuery = searchQuery
            searchResults = emptyList()
        }

        scope.launch {
            isLoading = true
            try {
                val fromIndex = if (isNewSearch) 0L else searchResults.size.toLong()
                val toIndex = fromIndex + 49L

                val response = SupabaseManager.client.from("movies")
                    .select(columns = Columns.list("id", "title", "poster_path", "backdrop_path", "vote_average", "genre_ids", "release_date", "popularity")) {
                        filter {
                            ilike("title", "%$searchQuery%")
                        }
                        if (isNewSearch) count(Count.EXACT)
                        range(fromIndex, toIndex)
                    }

                val newResults = response.decodeList<Movie>()
                DataCache.cacheMovies(newResults)
                if (isNewSearch) {
                    searchResults = newResults
                    totalCount = response.countOrNull() ?: newResults.size.toLong()
                    listState.scrollToItem(0)
                } else {
                    searchResults = searchResults + newResults
                }
            } catch (e: Exception) {
                if (searchResults.isEmpty()) {
                    errorMessage = "Error en la búsqueda: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .focusRequester(entryFocusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        if (searchResults.isNotEmpty()) {
                            try {
                                focusRequesters.getOrNull(targetIndex)?.requestFocus()
                            } catch (e: Exception) {
                                searchFocusRequester.requestFocus()
                            }
                        } else {
                            searchFocusRequester.requestFocus()
                        }
                    }
                }
                .focusable()
        )

        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF131313))) {
            // Left side: Keyboard and Search bar
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(Color(0x330A0A0A))
                    .padding(start = 24.dp, top = 16.dp, end = 8.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "BUSCAR",
                    style = TextStyle(
                        fontSize = 40.sp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White, Color.White.copy(alpha = 0.4f))
                        ),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.em
                    )
                )
                Text(
                    text = "ENCUENTRA TUS TÍTULOS FAVORITOS",
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.em
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Query Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(queryScrollState)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = searchQuery,
                            style = TextStyle(
                                fontSize = 24.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                        // Cursor
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .width(4.dp)
                                .height(32.dp)
                                .graphicsLayer { alpha = cursorAlpha }
                                .background(Gold)
                        )
                    }
                    // Underline
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Gold.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TV Keyboard
                val keys = listOf(
                    "A", "B", "C", "D", "E", "F",
                    "G", "H", "I", "J", "K", "L",
                    "M", "N", "Ñ", "O", "P", "Q",
                    "R", "S", "T", "U", "V", "W",
                    "X", "Y", "Z", "1", "2", "3",
                    "4", "5", "6", "7", "8", "9",
                    "0", "CLEAR", "SPACE", "BACK", "SEARCH"
                )

                val sidebarRequesters = LocalTabFocusRequesters.current
                val selectedTab = LocalSelectedTab.current

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
                ) {
                    itemsIndexed(
                        items = keys,
                        span = { _, key ->
                            if (key == "SEARCH") GridItemSpan(2) else GridItemSpan(1)
                        }
                    ) { index, key ->
                        val isActionKey = key in listOf("CLEAR", "SPACE", "BACK", "SEARCH")
                        val isSearch = key == "SEARCH"

                        var isKeyFocused by remember { mutableStateOf(false) }

                        val animatedScale by animateFloatAsState(
                            targetValue = if (isKeyFocused) 1.1f else 1.0f,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "scale"
                        )

                        val animatedContentColor by animateColorAsState(
                            targetValue = if (isKeyFocused) Color(0xFF3A3000) else Color(0xFFD0C6AB).copy(alpha = 0.8f),
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "color"
                        )

                        Surface(
                            onClick = {
                                when (key) {
                                    "SPACE" -> searchQuery += " "
                                    "BACK" -> if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                                    "CLEAR" -> searchQuery = ""
                                    "SEARCH" -> performSearch(true)
                                    else -> searchQuery += key
                                }
                            },
                            modifier = Modifier
                                .then(if (isSearch) Modifier.aspectRatio(2.1f) else Modifier.aspectRatio(1f))
                                .then(
                                    when {
                                        index == 0 -> Modifier.focusRequester(searchFocusRequester)
                                        key == "SEARCH" -> Modifier.focusRequester(keyboardRightFocusRequester)
                                        else -> Modifier
                                    }
                                )
                                .zIndex(if (isKeyFocused) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                                .onFocusChanged { isKeyFocused = it.isFocused }
                                .focusProperties {
                                    if (index % 7 == 0 && sidebarRequesters.isNotEmpty()) {
                                        left = sidebarRequesters[selectedTab]
                                    }
                                    if (index < 7) {
                                        up = FocusRequester.Cancel
                                    }
                                    if (index >= 35) {
                                        down = FocusRequester.Cancel
                                    }
                                    if (key == "BACK") {
                                        right = keyboardRightFocusRequester
                                    }
                                    val isRightmost = (index % 7 == 6) || (key == "SEARCH")
                                    if (isRightmost) {
                                        right = if (searchResults.isNotEmpty()) {
                                            focusRequesters.getOrNull(targetIndex) ?: FocusRequester.Default
                                        } else {
                                            FocusRequester.Cancel
                                        }
                                    }
                                },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = animatedContentColor,
                                focusedContainerColor = Color.Transparent,
                                focusedContentColor = animatedContentColor
                            ),
                            glow = ClickableSurfaceDefaults.glow(
                                focusedGlow = Glow(Color(0xFFFFD700).copy(alpha = 0.4f), 10.dp)
                            ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f) // We handle scale manually with graphicsLayer for precise easing
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isKeyFocused) Modifier.background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFFFFF6DF), Color(0xFFFFD700))
                                            )
                                        ) else Modifier
                                    )
                            ) {
                                when (key) {
                                    "CLEAR" -> Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(20.dp), tint = animatedContentColor)
                                    "SPACE" -> Icon(Icons.Outlined.SpaceBar, null, modifier = Modifier.size(20.dp), tint = animatedContentColor)
                                    "BACK" -> Icon(Icons.AutoMirrored.Outlined.Backspace, null, modifier = Modifier.size(20.dp), tint = animatedContentColor)
                                    "SEARCH" -> Text("BUSCAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = animatedContentColor)
                                    else -> Text(key, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = animatedContentColor)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0x0DFFFFFF))
            )

            // Right side: Results
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (lastSearchedQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vertical accent line
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(Gold)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "ESTÁS BUSCANDO",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.15.em
                                    )
                                )
                                Text(
                                    text = "\"$lastSearchedQuery\"",
                                    style = TextStyle(
                                        fontSize = 28.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-0.02).em
                                    ),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(IntrinsicSize.Max)
                        ) {
                            Text(
                                text = "$totalCount",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "COINCIDENCIAS",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.1.em
                                )
                            )
                        }
                    }
                }

                if (errorMessage != null && searchResults.isEmpty()) {
                    ErrorScreen(message = errorMessage!!, onRetry = { performSearch(true) })
                } else if (isLoading && searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Buscando...", color = Gold, style = MaterialTheme.typography.headlineSmall)
                    }
                } else if (searchResults.isEmpty() && lastSearchedQuery.isNotEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .width(420.dp)
                                .wrapContentHeight()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1E1E24),
                                            Color(0xFF121216)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.15f),
                                            Gold.copy(alpha = 0.4f),
                                            Color.White.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 32.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "¡PEDIDOS DISPONIBLES!",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.2.em
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "No encontramos \"$lastSearchedQuery\"",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Escanea el código para pedirla en el grupo",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    letterSpacing = 0.2.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .background(Color.White, RoundedCornerShape(20.dp))
                                    .border(
                                        width = 3.dp,
                                        color = Gold,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.cine_3_estrellas_grupo),
                                    contentDescription = "QR Telegram Grupo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "GRUPO OFICIAL DE TELEGRAM",
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.2.em
                                )
                            )
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gridWidth = maxWidth
                        val itemWidth = 110.dp
                        val spacing = 16.dp
                        val columnCount = ((gridWidth + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(itemWidth),
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            state = listState
                        ) {
                            itemsIndexed(searchResults) { index, movie ->
                                if (index >= searchResults.size - 10 && !isLoading && searchResults.size < totalCount) {
                                    LaunchedEffect(Unit) { performSearch(false) }
                                }
                                val isFirstInColumn = index % columnCount == 0
                                val isLastInRowItem = (index + 1) % columnCount == 0 || index == searchResults.size - 1
                                val lastRowStartIndex = ((searchResults.size - 1) / columnCount) * columnCount
                                val isLastRow = index >= lastRowStartIndex

                                MovieCard(
                                    movie = movie,
                                    onClick = { onMovieClick(movie.id) },
                                    screenKey = "search",
                                    focusRequester = focusRequesters.getOrNull(index) ?: remember { FocusRequester() },
                                    nextFocusRequester = focusRequesters.getOrNull(index + 1),
                                    leftFocus = if (isFirstInColumn) keyboardRightFocusRequester else null,
                                    downFocus = if (isLastRow) FocusRequester.Cancel else null,
                                    rightFocus = if (isLastInRowItem) FocusRequester.Cancel else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
