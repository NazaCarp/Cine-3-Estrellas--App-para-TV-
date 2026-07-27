package com.cine3estrellas

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// --- COMPOSITION LOCALS ---
val LocalTabFocusRequesters =
    compositionLocalOf<List<FocusRequester>> { error("No focus requesters provided") }

// 1. CORRECCIÓN: Se declara el CompositionLocal de contenido que faltaba
val LocalTabContentFocusRequesters =
    compositionLocalOf<List<FocusRequester>> { error("No content focus requesters provided") }

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
    var selectedTab by remember { mutableStateOf(1) }
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
    // --- STATE FOR HISTORY OVERLAY ---
    var isHistoryOpen by remember { mutableStateOf(false) }
    // --- STATE FOR VIDEO PLAYER OVERLAY ---
    var playerMovieId by remember { mutableStateOf<Int?>(null) }
    var playerVersion by remember { mutableStateOf<String?>(null) }
    // --- STATE FOR UPDATE ---
    var pendingUpdate by remember { mutableStateOf<AppVersionResponse?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                        // Activar escudo de foco para el inicio automático
                        DataCache.isTabChangeLocked = true
                        DataCache.lastHomeFocusedKey = "home_hero"
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
        // Verificar si hay una actualización disponible (en background, sin bloquear la UI)
        val updateInfo = SupabaseManager.checkForUpdate()
        if (updateInfo != null && updateInfo.versionCode > BuildConfig.VERSION_CODE) {
            pendingUpdate = updateInfo
            // Iniciar descarga automáticamente
            scope.launch {
                UpdateManager.downloadAndInstall(context, updateInfo.apkUrl)
                    .collect { state -> downloadState = state }
            }
        }
    }

    val tabs = listOf("Buscar", "Inicio", "Explorar", "Favoritos")
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    val contentFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    // Solicitar foco automáticamente al contenido de Inicio tras el Auto-Login
    LaunchedEffect(isCheckingSession) {
        if (!isCheckingSession && DataCache.isLoggedIn) {
            delay(500) // Un poco más de tiempo para asegurar el renderizado
            try {
                contentFocusRequesters[1].requestFocus()
            } catch (e: Exception) {}
        }
    }

    if (isCheckingSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Gold)
        }
        return
    }
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
    val startRoute = if (DataCache.isLoggedIn) "main" else "login"
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startRoute
    val isPlayerOverlayActive = playerMovieId != null

    // 3. CORRECCIÓN: Se provee "LocalTabContentFocusRequesters" en el CompositionLocalProvider
    CompositionLocalProvider(
        LocalTabFocusRequesters provides tabFocusRequesters,
        LocalTabContentFocusRequesters provides contentFocusRequesters,
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
        val isHistoryFullActive = isHistoryOpen
        val isLoginActive = currentRoute == "login"
        val hideSidebar =
            isPlayerOverlayActive || isDetailsActive || isCategoryActive || isLoginActive || isHistoryFullActive

        // --- MANEJO DEL BOTÓN ATRÁS EN LOGIN ---
        BackHandler(enabled = currentRoute == "login") {
            (context as? Activity)?.finish()
        }
        // BackHandler vacío en Home: bloquea que OnBackPressedDispatcher haga finish()
        // La lógica real está en onPreviewKeyEvent del Box, que intercepta a nivel de teclado.
        BackHandler(enabled = !hideSidebar && currentRoute == "main") { /* bloqueado por onPreviewKeyEvent */ }
        val contentPaddingStart by animateDpAsState(
            targetValue = if (hideSidebar) 0.dp else 80.dp,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            label = "contentPaddingStart"
        )
        // Alpha animado para el popup de salida — siempre en el árbol, nunca perturba el foco
        val exitConfirmAlpha by animateFloatAsState(
            targetValue = if (showExitConfirmation) 1f else 0f,
            animationSpec = tween(220),
            label = "exitConfirmAlpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { keyEvent ->
                    // Interceptar el botón Back en Home a nivel de teclado
                    if (keyEvent.key == Key.Back && !hideSidebar && currentRoute == "main") {
                        when (keyEvent.type) {
                            // KeyDown: consumir para evitar que Android TV limpie el foco activo
                            KeyEventType.KeyDown -> true
                            // KeyUp: ejecutar la lógica real
                            KeyEventType.KeyUp -> {
                                if (selectedTab != 1) {
                                    selectedTab = 1
                                    // Solicitar foco al contenido de Inicio para que restaure el último elemento
                                    scope.launch {
                                        delay(50)
                                        try {
                                            contentFocusRequesters[1].requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                } else {
                                    if (showExitConfirmation) {
                                        (context as? Activity)?.finish()
                                    } else {
                                        showExitConfirmation = true
                                        scope.launch {
                                            delay(2500)
                                            showExitConfirmation = false
                                        }
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Capa del contenido principal (NavHost)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        if (selectedMovieId != null || selectedCategoryId != null || isHistoryOpen || pendingUpdate != null) {
                            // BLOQUEO TOTAL DE FOCO: Si hay un detalle, categoría, historial o diálogo de actualización
                            // abierto, no permitimos que el foco entre o permanezca aquí.
                            canFocus = false
                        } else if (!hideSidebar && selectedTab != 2) {
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
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                            selectedTab = 1
                            // Activar bloqueo de cambio de pestañas durante la transición
                            DataCache.isTabChangeLocked = true
                            
                            // Asegurar que al entrar a Inicio se busque el Hero por defecto
                            DataCache.lastHomeFocusedKey = "home_hero"
                            
                            // Forzar el foco al contenido de Inicio tras una breve pausa para permitir la navegación
                            scope.launch {
                                delay(200)
                                try {
                                    contentFocusRequesters[1].requestFocus()
                                } catch (e: Exception) {}
                            }
                        })
                    }
                    composable("main") {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val screenHeight = maxHeight
                            val density = LocalDensity.current
                            val screenHeightPx = with(density) { screenHeight.toPx() }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = contentPaddingStart)
                            ) {
                                // 1. Buscar (Index 0)
                                TabScreenWrapper(
                                    index = 0,
                                    selectedIndex = selectedTab,
                                    screenHeightPx = screenHeightPx
                                ) {
                                    SearchScreen(onMovieClick = { id -> selectedMovieId = id })
                                }

                                // 2. Inicio (Index 1)
                                TabScreenWrapper(
                                    index = 1,
                                    selectedIndex = selectedTab,
                                    screenHeightPx = screenHeightPx
                                ) {
                                    HomeScreen(
                                        onMovieClick = { id -> selectedMovieId = id },
                                        onSeeMoreClick = { categoryId ->
                                            selectedCategoryId = categoryId
                                        },
                                        onSeeMoreHistory = { isHistoryOpen = true }
                                    )
                                }

                                // 3. Explorar (Index 2)
                                TabScreenWrapper(
                                    index = 2,
                                    selectedIndex = selectedTab,
                                    screenHeightPx = screenHeightPx
                                ) {
                                    ExploreScreen(onMovieClick = { id -> selectedMovieId = id })
                                }

                                // 4. Favoritos (Index 3)
                                TabScreenWrapper(
                                    index = 3,
                                    selectedIndex = selectedTab,
                                    screenHeightPx = screenHeightPx
                                ) {
                                    FavoritesScreen(onMovieClick = { id -> selectedMovieId = id })
                                }
                            }
                        }
                    }
                    composable("details/{movieId}") { backStackEntry ->
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
                        VideoPlayerScreen(
                            movieId,
                            version,
                            onBack = { navController.popBackStack() })
                    }
                }
            }
            // Capa de la Sidebar (Overlay)
            androidx.compose.animation.AnimatedVisibility(
                visible = !hideSidebar && selectedMovieId == null && pendingUpdate == null && !isHistoryOpen,
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
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = FastOutSlowInEasing
                                        ),
                                        label = "animatedItemColor"
                                    )
                                    Surface(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTab = index
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .onFocusChanged {
                                                isItemFocused = it.isFocused
                                                // Restauración de navegación fluida:
                                                // Las pestañas cambian al enfocarlas, a menos que el sistema esté bloqueado
                                                // temporalmente (por ejemplo, durante la transición post-login).
                                                if (it.isFocused && !DataCache.isTabChangeLocked) {
                                                    selectedTab = index
                                                }
                                            }
                                            .focusRequester(tabFocusRequesters[index])
                                            .focusProperties {
                                                right = contentFocusRequesters[index]
                                                if (index == 0) up = FocusRequester.Cancel
                                                if (index == tabs.lastIndex) down = FocusRequester.Cancel
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
                                        shape = SelectableSurfaceDefaults.shape(
                                            RoundedCornerShape(
                                                12.dp
                                            )
                                        )
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn(tween(300)) + expandVertically(
                                                    tween(
                                                        300
                                                    )
                                                ),
                                                exit = fadeOut(tween(300)) + shrinkVertically(
                                                    tween(
                                                        300
                                                    )
                                                ),
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
                        }
                    }
                }
            }
            // --- CATEGORY GRID OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedCategoryId != null,
                enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.zIndex(90f)
            ) {
                lastNonNullCategoryId?.let { id ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(90f)
                            .focusProperties {
                                if (selectedMovieId != null || isPlayerOverlayActive || isHistoryOpen) {
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
            // --- HISTORY OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = isHistoryOpen,
                enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.zIndex(95f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(95f)
                        .focusProperties {
                            if (selectedMovieId != null || isPlayerOverlayActive) {
                                canFocus = false
                            } else {
                                enter = { FocusRequester.Default }
                            }
                        }
                ) {
                    HistoryScreen(
                        onMovieClick = { selectedMovieId = it },
                        onBack = {
                            isHistoryOpen = false
                            DataCache.globalLastFocusedKey = "home_history_seemore"
                            DataCache.focusRestorationTrigger++
                        }
                    )
                }
            }
            // --- MOVIE DETAILS OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedMovieId != null,
                enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.zIndex(100f)
            ) {
                lastNonNullMovieId?.let { id ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(100f)
                            .focusProperties {
                                if (isPlayerOverlayActive) {
                                    canFocus = false
                                } else {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(200f)
                        .focusProperties {
                            canFocus = true
                            enter = { FocusRequester.Default }
                            exit = { FocusRequester.Cancel }
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
            // --- UPDATE DIALOG OVERLAY ---
            androidx.compose.animation.AnimatedVisibility(
                visible = pendingUpdate != null,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.zIndex(300f)
            ) {
                pendingUpdate?.let { update ->
                    UpdateDialog(
                        updateInfo = update,
                        downloadState = downloadState,
                        onUpdate = {
                            scope.launch {
                                UpdateManager.downloadAndInstall(context, update.apkUrl)
                                    .collect { state -> downloadState = state }
                            }
                        },
                        onDismiss = {
                            if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Installing) {
                                pendingUpdate = null
                            }
                        }
                    )
                }
            }
            // --- EXIT CONFIRMATION OVERLAY ---
            // Siempre en el árbol (no AnimatedVisibility) para no perturbar el foco al aparecer.
            // Solo varía su alpha — el elemento enfocado permanece intacto.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .zIndex(500f)
                    .graphicsLayer { alpha = exitConfirmAlpha }
                    .focusProperties { canFocus = false }
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1A1C1E).copy(alpha = 0.9f))
                        .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .focusProperties { canFocus = false }
                ) {
                    Text(
                        text = "Presione nuevamente para salir",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// UPDATE DIALOG
// ---------------------------------------------------------------------------
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun UpdateDialog(
    updateInfo: AppVersionResponse,
    downloadState: DownloadState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = downloadState is DownloadState.Downloading
    val isInstalling = downloadState is DownloadState.Installing
    val isError = downloadState is DownloadState.Error
    val isIdle = downloadState is DownloadState.Idle
    val progress = (downloadState as? DownloadState.Downloading)?.progress ?: 0f
    val context = LocalContext.current
    val hasDownloadedFile =
        remember(downloadState) { UpdateManager.getUpdateFile(context).exists() }
    val scope = rememberCoroutineScope()
    val updateButtonRequester = remember { FocusRequester() }
    LaunchedEffect(downloadState, hasDownloadedFile) {
        if (isError || isIdle || isInstalling) {
            repeat(5) {
                delay(100L)
                try {
                    updateButtonRequester.requestFocus()
                } catch (_: Exception) {
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .zIndex(300f)
            .focusProperties { enter = { updateButtonRequester } },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(440.dp)
                .focusRequester(updateButtonRequester)
                .border(
                    width = 1.dp,
                    color = Gold.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp)
                ),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF08090C)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Gold.copy(alpha = 0.25f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Actualización obligatoria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "  →  ",
                        color = Gold.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gold.copy(alpha = 0.15f))
                            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "v${updateInfo.version} (${updateInfo.versionCode})",
                            color = Gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                if (isDownloading || isInstalling || (isIdle && !hasDownloadedFile)) {
                    val currentProgress = if (isInstalling) 1f else if (isIdle) 0f else progress
                    val animatedProgress by animateFloatAsState(
                        targetValue = currentProgress,
                        animationSpec = tween(durationMillis = 300),
                        label = "downloadProgress"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Gold,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            isInstalling -> "Descarga completa. Instalando..."
                            isDownloading && progress < 0f -> "Preparando descarga..."
                            isDownloading -> "Descargando actualización... ${(progress * 100).toInt()}%"
                            else -> "Iniciando descarga..."
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                if (isError) {
                    Text(
                        text = "Hubo un problema con la descarga.\nVerificá tu conexión.",
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                if (isError || isIdle || isInstalling) {
                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                if (hasDownloadedFile) {
                                    UpdateManager.installExistingApk(context)
                                } else {
                                    onUpdate()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(updateButtonRequester),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isError) Color.White.copy(alpha = 0.1f) else Gold,
                                focusedContainerColor = if (isError) Color.White.copy(alpha = 0.2f) else Gold.copy(
                                    alpha = 0.8f
                                )
                            ),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = when {
                                        isError -> "Reintentar"
                                        hasDownloadedFile -> "Instalar"
                                        else -> "Empezar"
                                    },
                                    color = if (isError) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (hasDownloadedFile && !isDownloading) {
                            Surface(
                                onClick = onUpdate,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.15f)
                                        )
                                    ),
                                    focusedBorder = Border(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.3f)
                                        )
                                    )
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "Redescargar",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
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
fun TabScreenWrapper(
    index: Int,
    selectedIndex: Int,
    screenHeightPx: Float,
    content: @Composable () -> Unit
) {
    val targetTranslationY = when {
        index == selectedIndex -> 0f
        index < selectedIndex -> -screenHeightPx
        else -> screenHeightPx
    }
    val animateTranslationY by animateFloatAsState(
        targetValue = targetTranslationY,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "tabTranslationY"
    )
    val animatedAlpha = (1f - (Math.abs(animateTranslationY) / screenHeightPx)).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = animateTranslationY
                alpha = animatedAlpha
            }
            .zIndex(if (index == selectedIndex) 1f else 0f)
            .focusProperties {
                canFocus = (index == selectedIndex)
            }
    ) {
        content()
    }
 }
