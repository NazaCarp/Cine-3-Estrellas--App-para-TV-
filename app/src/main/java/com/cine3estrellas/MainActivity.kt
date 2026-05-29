package com.cine3estrellas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.*
import com.cine3estrellas.screens.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

val LocalTabFocusRequesters = compositionLocalOf<List<FocusRequester>> { error("No focus requesters provided") }
val LocalSelectedTab = compositionLocalOf { 0 }
val LocalExploreFocusRequester = compositionLocalOf<FocusRequester?> { null }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cine3EstrellasTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf(0) }
    var isSidebarFocused by remember { mutableStateOf(false) }
    
    var isCheckingSession by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val savedId = DataCache.loadSavedId(context)
        if (savedId != null) {
            val isMember = TelegramManager.checkGroupMembership(savedId)
            if (isMember) {
                val user = TelegramManager.getUserInfo(savedId)
                if (user != null) {
                    DataCache.currentUser = user
                    DataCache.isLoggedIn = true
                }
            }
        }
        isCheckingSession = false
    }

    if (isCheckingSession) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Gold)
        }
        return
    }

    val tabs = listOf("Inicio", "Buscar", "Explorar", "Favoritos")
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    val exploreFocusRequester = remember { FocusRequester() }
    
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarFocused) 220.dp else 80.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "sidebarWidth"
    )

    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.Search,
        Icons.Default.Explore,
        Icons.Default.Favorite
    )

    val startRoute = if (DataCache.isLoggedIn) "inicio" else "login"

    CompositionLocalProvider(
        LocalTabFocusRequesters provides tabFocusRequesters,
        LocalSelectedTab provides selectedTab,
        LocalExploreFocusRequester provides exploreFocusRequester
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: startRoute

        LaunchedEffect(currentRoute) {
            val index = tabs.indexOfFirst { it.lowercase() == currentRoute }
            if (index != -1) {
                selectedTab = index
            }
        }

        val isPlayerActive = currentRoute.startsWith("player")
        val isDetailsActive = currentRoute.startsWith("details")
        val isCategoryActive = currentRoute.startsWith("category")
        val isLoginActive = currentRoute == "login"
        val hideSidebar = isPlayerActive || isDetailsActive || isCategoryActive || isLoginActive

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Capa del contenido principal (NavHost)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        if (!hideSidebar && currentRoute != "explorar") {
                            left = tabFocusRequesters[selectedTab]
                        }
                    }
            ) {
                NavHost(
                    navController = navController, 
                    startDestination = startRoute,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(150)) },
                    popExitTransition = { fadeOut(tween(150)) }
                ) {
                    composable("login") {
                        LoginScreen(onLoginSuccess = {
                            navController.navigate("inicio") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }
                    composable("inicio") { 
                        Box(modifier = Modifier.padding(start = 80.dp)) {
                            HomeScreen(
                                onMovieClick = { id -> navController.navigate("details/$id") },
                                onSeeMoreClick = { categoryId -> navController.navigate("category/$categoryId") }
                            )
                        }
                    }
                    composable("buscar") { 
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            SearchScreen(onMovieClick = { id -> navController.navigate("details/$id") }) 
                        }
                    }
                    composable("explorar") {
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            ExploreScreen(onMovieClick = { id -> navController.navigate("details/$id") }) 
                        }
                    }
                    composable("favoritos") { 
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            FavoritesScreen(onMovieClick = { id -> navController.navigate("details/$id") }) 
                        }
                    }
                    composable("category/{categoryId}") { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getString("categoryId")
                        categoryId?.let {
                            CategoryGridScreen(
                                categoryId = it,
                                onMovieClick = { id -> navController.navigate("details/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable("details/{movieId}") { backStackEntry ->
                        val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull()
                        movieId?.let { 
                            MovieDetailsScreen(
                                movieId = it, 
                                onMovieClick = { id -> navController.navigate("details/$id") },
                                onPlayClick = { id, version -> 
                                    navController.navigate("player/$id/$version") 
                                }
                            ) 
                        }
                    }
                    composable(
                        route = "player/{movieId}/{version}",
                        arguments = listOf(
                            navArgument("movieId") { type = NavType.IntType },
                            navArgument("version") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
                        val version = backStackEntry.arguments?.getString("version") ?: ""
                        VideoPlayerScreen(movieId, version, onBack = { navController.popBackStack() })
                    }
                }
            }

            // Capa de la Sidebar (Overlay)
            androidx.compose.animation.AnimatedVisibility(
                visible = !hideSidebar,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(sidebarWidth)
                        .onFocusChanged { isSidebarFocused = it.hasFocus },
                    colors = SurfaceDefaults.colors(
                        containerColor = Color(0xFF080808),
                    ),
                    shape = RectangleShape
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Right side subtle glow line
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .align(Alignment.CenterEnd)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Gold.copy(alpha = 0.2f), Color.Transparent)
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Logo Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        colors = SurfaceDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo),
                                            contentDescription = "Logo",
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isSidebarFocused,
                                        enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(animationSpec = tween(150)),
                                        exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally(animationSpec = tween(100))
                                    ) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Image(
                                            painter = painterResource(id = R.drawable.brand_text),
                                            contentDescription = "Cine 3 Estrellas",
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }
                            }

                            // Navigation Items
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index
                                    var isItemFocused by remember { mutableStateOf(false) }

                                    val itemAlpha by animateFloatAsState(
                                        targetValue = if (isSelected || isItemFocused) 1f else 0.6f,
                                        label = "itemAlpha"
                                    )

                                    Surface(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTab = index
                                            navController.navigate(title.lowercase()) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .onFocusChanged { isItemFocused = it.isFocused }
                                            .focusRequester(tabFocusRequesters[index])
                                            .focusProperties {
                                                if (index == 2 && currentRoute == "explorar") {
                                                    right = exploreFocusRequester
                                                }
                                            },
                                        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
                                        colors = SelectableSurfaceDefaults.colors(
                                            containerColor = Color.Transparent,
                                            focusedContainerColor = Gold,
                                            selectedContainerColor = Gold.copy(alpha = 0.15f),
                                            focusedSelectedContainerColor = Gold
                                         ),
                                        glow = SelectableSurfaceDefaults.glow(
                                            focusedGlow = Glow(Gold.copy(alpha = 0.15f), 20.dp)
                                        ),
                                        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(16.dp))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Selected Indicator (left bar)
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                                modifier = Modifier.align(Alignment.CenterStart)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(24.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(Gold)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    icons[index],
                                                    contentDescription = title,
                                                    tint = if (isItemFocused) Color.Black else if (isSelected) Gold else Color.White,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .alpha(itemAlpha)
                                                )

                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = isSidebarFocused,
                                                    enter = fadeIn(animationSpec = tween(150)) + slideInHorizontally(animationSpec = tween(150)),
                                                    exit = fadeOut(animationSpec = tween(100)) + slideOutHorizontally(animationSpec = tween(100))
                                                ) {
                                                    Row {
                                                        Spacer(modifier = Modifier.width(20.dp))
                                                        Text(
                                                            text = title,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isItemFocused) Color.Black else if (isSelected) Gold else Color.White,
                                                            modifier = Modifier.alpha(itemAlpha)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom Version info
                            if (isSidebarFocused) {
                               Text(
                                   text = "v1.0.4",
                                   style = MaterialTheme.typography.labelSmall,
                                   color = Color.White.copy(alpha = 0.3f),
                                   modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                               )
                            }
                        }
                    }
                }
            }
        }
    }
}
