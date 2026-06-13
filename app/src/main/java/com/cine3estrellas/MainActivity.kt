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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
val LocalDetailsActive = compositionLocalOf { false }
val LocalCategoryActive = compositionLocalOf { false }
val LocalPlayerOverlayActive = compositionLocalOf { false }

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

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf(0) }
    var isSidebarFocused by remember { mutableStateOf(false) }
    
    // --- STATE FOR MOVIE DETAILS OVERLAY ---
    var selectedMovieId by remember { mutableStateOf<Int?>(null) }
    var lastNonNullMovieId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedMovieId) {
        if (selectedMovieId != null) {
            lastNonNullMovieId = selectedMovieId
        }
    }

    // --- STATE FOR CATEGORY GRID OVERLAY ---
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var lastNonNullCategoryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            lastNonNullCategoryId = selectedCategoryId
        }
    }

    // --- STATE FOR VIDEO PLAYER OVERLAY ---
    var playerMovieId by remember { mutableStateOf<Int?>(null) }
    var playerVersion by remember { mutableStateOf<String?>(null) }
    
    var isCheckingSession by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val savedId = DataCache.loadSavedId(context)
        if (savedId != null) {
            val telegramIdLong = savedId.toLongOrNull()
            if (telegramIdLong != null) {
                var isMember = true
                var currentTelegramUser: User? = null
                
                // 1. Check membership via Telegram Bot API only if 24 hours have passed
                if (DataCache.shouldCheckMembership(context)) {
                    isMember = TelegramManager.checkGroupMembership(savedId)
                    if (isMember) {
                        currentTelegramUser = TelegramManager.getUserInfo(savedId)
                        DataCache.updateMembershipCheckTime(context)
                    }
                }

                if (isMember) {
                    val telegramUserObj = currentTelegramUser ?: User(telegramId = telegramIdLong)
                    val syncedUser = SupabaseManager.syncUser(context, telegramUserObj)
                    if (syncedUser != null) {
                        DataCache.currentUser = syncedUser
                        DataCache.isLoggedIn = true
                    } else {
                        DataCache.logout(context)
                    }
                } else {
                    DataCache.logout(context)
                }
            } else {
                DataCache.logout(context)
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

    val tabs = listOf("Buscar", "Inicio", "Explorar", "Favoritos")
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    val exploreFocusRequester = remember { FocusRequester() }
    
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarFocused) 220.dp else 80.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sidebarWidth"
    )

    val sidebarBgColor by animateColorAsState(
        targetValue = if (isSidebarFocused) Color(0xF20A0C10) else Color(0x660A0C10),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sidebarBgColor"
    )

    val sidebarBorderColor by animateColorAsState(
        targetValue = if (isSidebarFocused) Gold.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sidebarBorderColor"
    )

    val logoSize by animateDpAsState(
        targetValue = if (isSidebarFocused) 48.dp else 40.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "logoSize"
    )
    
    val brandTextHeight by animateDpAsState(
        targetValue = if (isSidebarFocused) 30.dp else 0.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "brandTextHeight"
    )
    
    val brandTextOpacity by animateFloatAsState(
        targetValue = if (isSidebarFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "brandTextOpacity"
    )
    
    val brandTextOffsetY by animateDpAsState(
        targetValue = if (isSidebarFocused) 0.dp else 10.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "brandTextOffsetY"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSidebarFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "labelAlpha"
    )
    
    val labelTranslationX by animateDpAsState(
        targetValue = if (isSidebarFocused) 0.dp else (-10).dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "labelTranslationX"
    )

    val icons = listOf(
        Icons.Default.Search,
        Icons.Default.Home,
        Icons.Default.Explore,
        Icons.Default.Favorite
    )

    val startRoute = if (DataCache.isLoggedIn) "inicio" else "login"
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startRoute
    val isPlayerOverlayActive = playerMovieId != null

    CompositionLocalProvider(
        LocalTabFocusRequesters provides tabFocusRequesters,
        LocalSelectedTab provides selectedTab,
        LocalExploreFocusRequester provides exploreFocusRequester,
        LocalDetailsActive provides (selectedMovieId != null),
        LocalCategoryActive provides (selectedCategoryId != null),
        LocalPlayerOverlayActive provides isPlayerOverlayActive
    ) {
        LaunchedEffect(currentRoute) {
            val index = tabs.indexOfFirst { it.lowercase() == currentRoute }
            if (index != -1) {
                selectedTab = index
            }
        }

        val isDetailsActive = selectedMovieId != null
        val isCategoryActive = selectedCategoryId != null
        val isLoginActive = currentRoute == "login"
        val hideSidebar = isPlayerOverlayActive || isDetailsActive || isCategoryActive || isLoginActive

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Capa del contenido principal (NavHost)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        if (selectedMovieId != null || selectedCategoryId != null) {
                            // BLOQUEO TOTAL DE FOCO: Si hay un detalle o categoría abierta,
                            // no permitimos que el foco entre o permanezca aquí.
                            canFocus = false
                        } else if (!hideSidebar && currentRoute != "explorar") {
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
                                onMovieClick = { id -> selectedMovieId = id },
                                onSeeMoreClick = { categoryId -> selectedCategoryId = categoryId }
                            )
                        }
                    }
                    composable("buscar") { 
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            SearchScreen(onMovieClick = { id -> selectedMovieId = id })
                        }
                    }
                    composable("explorar") {
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            ExploreScreen(onMovieClick = { id -> selectedMovieId = id })
                        }
                    }
                    composable("favoritos") { 
                        Box(modifier = Modifier.padding(start = 80.dp)) { 
                            FavoritesScreen(onMovieClick = { id -> selectedMovieId = id })
                        }
                    }
                    composable("details/{movieId}") { backStackEntry ->
                        // This route is now secondary as we use overlay, 
                        // but we keep it for backward compatibility or direct deep links.
                        val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull()
                        movieId?.let {
                            MovieDetailsScreen(
                                movieId = it,
                                onMovieClick = { id -> selectedMovieId = id },
                                onPlayClick = { mid, version ->
                                    navController.navigate("player/$mid/$version")
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
                visible = !hideSidebar && selectedMovieId == null,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(sidebarWidth)
                        .onFocusChanged { isSidebarFocused = it.hasFocus },
                    colors = SurfaceDefaults.colors(
                        containerColor = sidebarBgColor,
                    ),
                    shape = RectangleShape
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Right side solid border matching Web
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .align(Alignment.CenterEnd)
                                .background(sidebarBorderColor)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Logo Section - Column layout matching Web
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(logoSize),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo),
                                        contentDescription = "Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (brandTextHeight > 0.dp) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        painter = painterResource(id = R.drawable.brand_text),
                                        contentDescription = "Cine 3 Estrellas",
                                        modifier = Modifier
                                            .height(brandTextHeight)
                                            .offset(y = brandTextOffsetY)
                                            .alpha(brandTextOpacity)
                                    )
                                }
                            }

                            // Navigation Items
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .focusProperties {
                                        enter = { tabFocusRequesters[selectedTab] }
                                    },
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index
                                    var isItemFocused by remember { mutableStateOf(false) }

                                    val animatedItemColor by animateColorAsState(
                                        targetValue = when {
                                            isSelected -> Gold
                                            isItemFocused -> Color.White
                                            else -> Color.White.copy(alpha = 0.3f)
                                        },
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                        label = "animatedItemColor"
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
                                        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f),
                                        colors = SelectableSurfaceDefaults.colors(
                                            containerColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            selectedContainerColor = Color.Transparent,
                                            focusedSelectedContainerColor = Color.Transparent
                                         ),
                                        glow = SelectableSurfaceDefaults.glow(
                                            focusedGlow = Glow.None
                                        ),
                                        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Selected Indicator (left bar) matching Web
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                                                exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
                                                modifier = Modifier.align(Alignment.CenterStart)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(16.dp)
                                                        .clip(
                                                            RoundedCornerShape(
                                                                topStart = 0.dp,
                                                                bottomStart = 0.dp,
                                                                topEnd = 4.dp,
                                                                bottomEnd = 4.dp
                                                            )
                                                        )
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
                                                    tint = animatedItemColor,
                                                    modifier = Modifier.size(28.dp)
                                                )

                                                Spacer(modifier = Modifier.width(20.dp))

                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = animatedItemColor,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            alpha = labelAlpha
                                                            translationX = labelTranslationX.toPx()
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom Version info
                            Text(
                                text = "v1.0.4",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .padding(start = 24.dp, bottom = 16.dp)
                                    .graphicsLayer {
                                        alpha = labelAlpha
                                    }
                            )
                        }
                    }
                }
            }

            // --- CATEGORY GRID OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedCategoryId != null,
                enter = fadeIn(tween(300)) + expandIn(expandFrom = Alignment.Center),
                exit = fadeOut(tween(300)) + shrinkOut(shrinkTowards = Alignment.Center),
                modifier = Modifier.zIndex(90f)
            ) {
                lastNonNullCategoryId?.let { id ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .zIndex(90f)
                        .focusProperties {
                            if (selectedMovieId != null || isPlayerOverlayActive) {
                                canFocus = false
                            } else {
                                enter = { FocusRequester.Default }
                            }
                        }
                    ) {
                        CategoryGridScreen(
                            categoryId = id,
                            onMovieClick = { selectedMovieId = it },
                            onBack = {
                                DataCache.categoryIdToRestore = selectedCategoryId
                                selectedCategoryId = null
                            }
                        )
                    }
                }
            }

            // --- MOVIE DETAILS OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedMovieId != null,
                enter = fadeIn(tween(300)) + expandIn(expandFrom = Alignment.Center),
                exit = fadeOut(tween(300)) + shrinkOut(shrinkTowards = Alignment.Center),
                modifier = Modifier.zIndex(100f)
            ) {
                lastNonNullMovieId?.let { id ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .zIndex(100f)
                        .focusProperties {
                            if (isPlayerOverlayActive) {
                                // Bloquear foco de la ficha mientras el player esté encima
                                canFocus = false
                            } else {
                                // Capturamos el foco para que no se escape a la lista de atrás
                                enter = { FocusRequester.Default }
                            }
                        }
                    ) {
                        MovieDetailsScreen(
                            movieId = id,
                            onMovieClick = { selectedMovieId = it },
                            onPlayClick = { mid, version ->
                                playerMovieId = mid
                                playerVersion = version
                            },
                            onClose = {
                                DataCache.movieIdToRestore = selectedMovieId
                                DataCache.keyToRestore = DataCache.globalLastFocusedKey
                                selectedMovieId = null
                                DataCache.focusRestorationTrigger++
                            }
                        )
                    }
                }
            }

            // --- VIDEO PLAYER OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlayerOverlayActive,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.zIndex(200f)
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .zIndex(200f)
                    .focusProperties {
                        enter = { FocusRequester.Default }
                    }
                ) {
                    val pid = playerMovieId
                    val pver = playerVersion
                    if (pid != null && pver != null) {
                        VideoPlayerScreen(
                            movieId = pid,
                            version = pver,
                            onBack = {
                                playerMovieId = null
                                playerVersion = null
                            }
                        )
                    }
                }
            }
        }
    }
}
