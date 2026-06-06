package com.cine3estrellas.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.cine3estrellas.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.launch


// Internal Color Palette
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ExploreScreen(onMovieClick: (Int) -> Unit) {
    val exploreFocusRequester = LocalExploreFocusRequester.current
    // Using DataCache to preserve state across navigation
    var selectedGenre by remember { mutableStateOf(DataCache.exploreSelectedGenre) }
    var genres by remember { mutableStateOf(DataCache.exploreGenres) }
    var movies by remember { mutableStateOf(DataCache.exploreMovies) }
    var isLoading by remember { mutableStateOf(false) }
    var totalCount by remember { mutableStateOf(DataCache.exploreTotalCount) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Filter states
    var minRating by remember { mutableStateOf(DataCache.exploreMinRating) }
    var selectedYear by remember { mutableStateOf(DataCache.exploreSelectedYear) }
    var sortBy by remember { mutableStateOf(DataCache.exploreSortBy) }
    var activeFilterMenu by remember { mutableStateOf<String?>(null) }

    // Focus requesters for filter buttons
    val starsFocusRequester = remember { FocusRequester() }
    val yearFocusRequester = remember { FocusRequester() }
    val sortFocusRequester = remember { FocusRequester() }

    // Sync local state to DataCache
    LaunchedEffect(selectedGenre) { DataCache.exploreSelectedGenre = selectedGenre }
    LaunchedEffect(genres) { DataCache.exploreGenres = genres }
    LaunchedEffect(movies) { DataCache.exploreMovies = movies }
    LaunchedEffect(totalCount) { DataCache.exploreTotalCount = totalCount }
    LaunchedEffect(minRating) { DataCache.exploreMinRating = minRating }
    LaunchedEffect(selectedYear) { DataCache.exploreSelectedYear = selectedYear }
    LaunchedEffect(sortBy) { DataCache.exploreSortBy = sortBy }

    LaunchedEffect(Unit) {
        if (genres.isNotEmpty()) return@LaunchedEffect
        try {
            val fetchedGenres = TmdbManager.getGenres()
            if (fetchedGenres.isNotEmpty()) {
                genres = fetchedGenres
                if (selectedGenre == null) {
                    selectedGenre = fetchedGenres.first()
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error al cargar géneros: ${e.message}"
            e.printStackTrace()
        }
    }

    fun loadMovies(isNewFilter: Boolean = true) {
        if (selectedGenre == null) return
        if (!isNewFilter && (isLoading || movies.size >= totalCount)) return
        
        errorMessage = null
        if (isNewFilter) {
            movies = emptyList() // Clear immediately to trigger shimmer
            isLoading = true
        }

        scope.launch {
            try {
                val fromIndex = if (isNewFilter) 0L else movies.size.toLong()
                val toIndex = fromIndex + 39L

                val response = SupabaseManager.client.from("movies")
                    .select {
                        filter {
                            overlaps("genre_ids", listOf(selectedGenre!!.id))
                            if (minRating > 0.0) gte("vote_average", minRating)
                            if (selectedYear != null && selectedYear != "all") {
                                when (selectedYear) {
                                    "classic" -> lt("release_date", "2000-01-01")
                                    "2000" -> {
                                        gte("release_date", "2000-01-01")
                                        lt("release_date", "2010-01-01")
                                    }
                                    else -> gte("release_date", "$selectedYear-01-01")
                                }
                            }
                        }
                        
                        val sortColumn = when(sortBy) {
                            "popularity" -> "popularity"
                            "vote_average" -> "vote_average"
                            "release_date" -> "release_date"
                            "title" -> "title"
                            else -> "popularity"
                        }
                        
                        val sortOrder = if (sortBy == "title") 
                            io.github.jan.supabase.postgrest.query.Order.ASCENDING 
                        else 
                            io.github.jan.supabase.postgrest.query.Order.DESCENDING
                        
                        order(sortColumn, sortOrder)
                        if (isNewFilter) count(Count.EXACT)
                        range(fromIndex, toIndex)
                    }
                
                val newResults = response.decodeList<Movie>()
                if (isNewFilter) {
                    movies = newResults
                    totalCount = response.countOrNull() ?: newResults.size.toLong()
                } else {
                    movies = movies + newResults
                }
            } catch (e: Exception) {
                if (movies.isEmpty()) {
                    errorMessage = "Error al cargar películas: ${e.message}"
                }
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // This effect triggers whenever filters change
    LaunchedEffect(selectedGenre, minRating, selectedYear, sortBy) {
        // We only skip if we are restoring state AND movies are already loaded
        // To be safe and ensure filters work, we only skip if the current movies 
        // matches the DataCache AND movies is not empty.
        // However, it's safer to just reload if any of these change.
        loadMovies(isNewFilter = true)
    }

    if (errorMessage != null && movies.isEmpty() && genres.isEmpty()) {
        ErrorScreen(
            message = errorMessage!!,
            onRetry = {
                errorMessage = null
                // Trigger a re-run of initialization and loading
                scope.launch {
                    try {
                        val fetchedGenres = TmdbManager.getGenres()
                        if (fetchedGenres.isNotEmpty()) {
                            genres = fetchedGenres
                            selectedGenre = fetchedGenres.first()
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error al cargar géneros: ${e.message}"
                    }
                }
            }
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
        // 2 & 3. Sidebar: "GÉNEROS"
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color.Transparent)
        ) {
            // Title GÉNEROS
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "GÉNEROS",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.3.em
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (genres.isEmpty()) {
                    items(10) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .height(20.dp)
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    items(genres) { genre ->
                        val isSelected = selectedGenre?.id == genre.id
                        GenreSidebarItem(
                            genre = genre,
                            isSelected = isSelected,
                            onClick = { selectedGenre = genre },
                            modifier = if (isSelected && exploreFocusRequester != null)
                                Modifier.focusRequester(exploreFocusRequester)
                            else Modifier
                        )
                    }
                }
            }
        }

        // Right: Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
        ) {
            // 4, 5 & 6. Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 4. Main Title EXPLORAR
                    Text(
                        text = "EXPLORAR",
                        style = TextStyle(
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.2.em,
                            lineHeight = 1.em,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        modifier = Modifier
                            .graphicsLayer(alpha = 0.99f)
                            .drawWithCache {
                                val brush = Brush.verticalGradient(
                                    colors = listOf(Color.White, Color.White.copy(alpha = 0.4f))
                                )
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                                }
                            }
                    )

                    // 6. Filter Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        FilterButton(
                            text = "ESTRELLAS",
                            icon = Icons.Outlined.StarBorder,
                            onClick = { activeFilterMenu = "stars" },
                            modifier = Modifier
                                .focusRequester(starsFocusRequester)
                                .focusProperties {
                                    right = yearFocusRequester
                                    left = exploreFocusRequester ?: FocusRequester.Default
                                }
                        )
                        FilterButton(
                            text = "AÑO",
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = { activeFilterMenu = "year" },
                            modifier = Modifier
                                .focusRequester(yearFocusRequester)
                                .focusProperties {
                                    left = starsFocusRequester
                                    right = sortFocusRequester
                                }
                        )
                        FilterButton(
                            text = "ORDENAR",
                            icon = Icons.Outlined.FilterList,
                            onClick = { activeFilterMenu = "sort" },
                            modifier = Modifier
                                .focusRequester(sortFocusRequester)
                                .focusProperties {
                                    left = yearFocusRequester
                                    right = FocusRequester.Cancel
                                }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                // 5. Subtitle Row (Now below, taking its own line)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    // Genre Name
                    Text(
                        text = selectedGenre?.name?.uppercase() ?: "",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Gold,
                            letterSpacing = 0.2.em
                        )
                    )

                    // Filters
                    if (minRating > 0.0) {
                        Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(12.dp))
                            Text("${minRating}+", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = Gold, letterSpacing = 0.2.em))
                        }
                    }

                    if (selectedYear != null && selectedYear != "all") {
                        Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.sp)
                        val yearText = when (selectedYear) {
                            "classic" -> "RETRO"
                            "2000" -> "2000s"
                            else -> "${selectedYear}+"
                        }
                        Text(
                            text = yearText,
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, color = Gold, letterSpacing = 0.2.em)
                        )
                    }

                    // Separator
                    Text("|", color = Color.White.copy(alpha = 0.2f), fontWeight = FontWeight.Normal, fontSize = 10.sp)

                    // Matches Count
                    Text(
                        text = "${totalCount} TÍTULOS",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate300,
                            letterSpacing = 0.2.em
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid Content
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val gridWidth = maxWidth
                val itemWidth = 98.dp
                val spacing = 16.dp
                val columnCount = ((gridWidth + spacing) / (itemWidth + spacing)).toInt().coerceAtLeast(1)

                androidx.compose.animation.Crossfade(
                    targetState = isLoading && movies.isEmpty(),
                    animationSpec = tween(500),
                    label = "loadingCrossfade"
                ) { isInitialLoading ->
                    if (isInitialLoading) {
                        // Shimmer Skeleton Grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(itemWidth),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                            userScrollEnabled = false,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(columnCount * 4) { // Show 4 rows of placeholders
                                Box(modifier = Modifier.width(itemWidth)) {
                                    MovieCardPlaceholder()
                                }
                            }
                        }
                    } else if (movies.isNotEmpty()) {
                        // Actual Results Grid
                        val focusRequesters = remember { 
                            val list = mutableStateListOf<FocusRequester>()
                            repeat(movies.size) { list.add(FocusRequester()) }
                            list
                        }
                        LaunchedEffect(movies.size) {
                            while (focusRequesters.size < movies.size) {
                                focusRequesters.add(FocusRequester())
                            }
                        }
                        
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(itemWidth),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(movies) { index, movie ->
                                if (index >= movies.size - 10 && !isLoading && movies.size < totalCount) {
                                    LaunchedEffect(Unit) {
                                        loadMovies(isNewFilter = false)
                                    }
                                }
                                
                                val isFirstInColumn = index % columnCount == 0
                                
                                MovieCard(
                                    movie = movie, 
                                    onClick = { onMovieClick(movie.id) }, 
                                    cardWidth = itemWidth,
                                    screenKey = "explore",
                                    focusRequester = if (index < focusRequesters.size) focusRequesters[index] else remember { FocusRequester() },
                                    nextFocusRequester = if (index < focusRequesters.size - 1) focusRequesters[index + 1] else null,
                                    leftFocus = if (isFirstInColumn) exploreFocusRequester else null
                                )
                            }
                            
                            if (isLoading && movies.isNotEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text("Cargando más...", color = Gold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    } else if (!isLoading) {
                        // No results found
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No se encontraron resultados", color = Slate400, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (activeFilterMenu != null) {
        FilterDialog(
            menuType = activeFilterMenu!!,
            currentValue = when (activeFilterMenu) {
                "stars" -> if (minRating == 0.0) "all" else minRating.toString()
                "year" -> selectedYear ?: "all"
                else -> sortBy
            },
            onDismiss = { activeFilterMenu = null },
            onSelect = { newValue ->
                when (activeFilterMenu) {
                    "stars" -> minRating = if (newValue == "all") 0.0 else newValue.toDouble()
                    "year" -> selectedYear = if (newValue == "all") null else newValue
                    "sort" -> sortBy = newValue
                }
                activeFilterMenu = null
            }
        )
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreSidebarItem(
    genre: Genre,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val sidebarRequesters = LocalTabFocusRequesters.current
    val selectedTab = LocalSelectedTab.current

    // Animations
    val duration = 200
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> Gold
            isFocused -> Color.White
            else -> Slate400
        },
        animationSpec = tween(duration), label = "textColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.1f)
            isSelected -> Gold.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(duration), label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> Gold
            isFocused -> Color.White.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        animationSpec = tween(duration), label = "borderColor"
    )
    val translationX by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec = tween(duration), label = "translationX"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(duration), label = "scale"
    )
    val fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal

    Surface(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusProperties {
                if (sidebarRequesters.isNotEmpty()) {
                    left = sidebarRequesters[selectedTab]
                }
            }
            .graphicsLayer {
                this.translationX = translationX.toPx()
            },
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent, // Managed by background modifier
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.0f), // Manual scale
        shape = SelectableSurfaceDefaults.shape(shape = androidx.compose.ui.graphics.RectangleShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        // Borde izquierdo de 4dp
                        clipRect(right = 4.dp.toPx()) {
                            drawRect(borderColor)
                        }
                    }
                }
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Text(
                text = genre.name,
                color = textColor,
                fontWeight = fontWeight,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val duration = 300
    val color by animateColorAsState(
        targetValue = if (isFocused) Gold else Slate500,
        animationSpec = tween(duration), label = "btnColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(duration), label = "btnScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    letterSpacing = 0.1.em
                )
            )
        }
    }
}

data class StarOption(
    val id: String,
    val label: String,
    val badge: String,
    val num: String
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FilterDialog(
    menuType: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val starsOptions = remember {
        listOf(
            StarOption("all", "Cualquier Puntaje", "∞", "∞"),
            StarOption("4.0", "Bajas", "4.0+", "4.0"),
            StarOption("5.0", "Regulares", "5.0+", "5.0"),
            StarOption("6.0", "Aceptables", "6.0+", "6.0"),
            StarOption("7.0", "Buenas", "7.0+", "7.0"),
            StarOption("8.0", "Muy Buenas", "8.0+", "8.0"),
            StarOption("9.0", "Obras Maestras", "9.0+", "9.0")
        )
    }

    val options = remember(menuType) {
        when (menuType) {
            "year" -> listOf(
                Pair("all", "Cualquier Año"),
                Pair("2024", "Estrenos (2024+)"),
                Pair("2020", "Modernas (2020+)"),
                Pair("2010", "Última Década (2010+)"),
                Pair("2000", "Clásicos 2000s"),
                Pair("classic", "Retro (Pre-2000)")
            )
            "sort" -> listOf(
                Pair("popularity", "Tendencias"),
                Pair("vote_average", "Mejor Valoradas"),
                Pair("release_date", "Lanz. Recientes"),
                Pair("title", "Alfabético (A-Z)")
            )
            else -> emptyList()
        }
    }

    val initialIndex = remember(menuType, currentValue) {
        if (menuType == "stars") {
            starsOptions.indexOfFirst { it.id == currentValue }.coerceAtLeast(0)
        } else {
            options.indexOfFirst { it.first == currentValue }.coerceAtLeast(0)
        }
    }

    var currentIndex by remember { mutableStateOf(initialIndex) }
    val sliderFocusRequester = remember { FocusRequester() }
    val focusRequesters = remember(options) { List(options.size) { FocusRequester() } }

    LaunchedEffect(menuType) {
        if (menuType == "stars") {
            sliderFocusRequester.requestFocus()
        } else {
            if (initialIndex in focusRequesters.indices) {
                focusRequesters[initialIndex].requestFocus()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(336.dp)
                    .wrapContentHeight()
                    .background(Color(0xFF0F0F11), RoundedCornerShape(16.dp))
                    .border(1.dp, Gold, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                if (menuType == "stars") {
                    var isSliderFocused by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title/Label
                        Text(
                            text = starsOptions[currentIndex].label.uppercase(),
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Gold,
                                letterSpacing = 0.2.em
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        // Large badge
                        Text(
                            text = starsOptions[currentIndex].badge,
                            style = TextStyle(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Slider Track Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .focusRequester(sliderFocusRequester)
                                .onFocusChanged { isSliderFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                if (currentIndex > 0) {
                                                    currentIndex--
                                                    true
                                                } else false
                                            }
                                            Key.DirectionRight -> {
                                                if (currentIndex < starsOptions.size - 1) {
                                                    currentIndex++
                                                    true
                                                } else false
                                            }
                                            Key.DirectionUp, Key.DirectionDown -> {
                                                onDismiss()
                                                true
                                            }
                                            Key.DirectionCenter, Key.Enter -> {
                                                onSelect(starsOptions[currentIndex].id)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Inactive Track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                            )

                            // Active Track
                            val progressFraction = currentIndex.toFloat() / (starsOptions.size - 1)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction.coerceAtLeast(0.01f))
                                    .height(6.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFFB8860B), Gold)
                                        ),
                                        RoundedCornerShape(3.dp)
                                    )
                            )

                            // Knob
                            Box(
                                modifier = Modifier
                                    .align(androidx.compose.ui.BiasAlignment(progressFraction * 2 - 1, 0f))
                                    .size(if (isSliderFocused) 18.dp else 14.dp)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSliderFocused) Gold else Color.White.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                    .background(Color.White, RoundedCornerShape(9.dp))
                            )
                        }

                        // Rating Numbers at the bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            starsOptions.forEachIndexed { i, opt ->
                                val isHighlighted = currentIndex >= i
                                Text(
                                    text = opt.num,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHighlighted) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Custom uppercase header text
                        Text(
                            text = if (menuType == "year") "RANGO DE AÑO" else "ORDEN DE CATÁLOGO",
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.4f),
                                letterSpacing = 0.25.em
                            ),
                            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                        )
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Options vertical list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            options.forEachIndexed { i, opt ->
                                val isSelected = opt.first == currentValue
                                var isFocused by remember { mutableStateOf(false) }

                                val containerColor = if (isFocused) Color.White else Color.Transparent
                                val textColor = when {
                                    isFocused -> Color.Black
                                    isSelected -> Gold
                                    else -> Slate300
                                }
                                val fontWeight = when {
                                    isFocused -> FontWeight.Bold
                                    isSelected -> FontWeight.Bold
                                    else -> FontWeight.Normal
                                }

                                Surface(
                                    onClick = { onSelect(opt.first) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = containerColor,
                                        focusedContainerColor = containerColor,
                                        pressedContainerColor = containerColor
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequesters[i])
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionLeft, Key.DirectionRight -> {
                                                        onDismiss()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = opt.second.uppercase(),
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = fontWeight,
                                                color = textColor,
                                                letterSpacing = 0.1.em
                                            )
                                        )

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isFocused) Color.Black else Gold,
                                                modifier = Modifier.size(16.dp)
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
    }
}
