package com.cine3estrellas.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cine3estrellas.* // Shared logic
import com.cine3estrellas.mobile.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cine3EstrellasMobileTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Explore,
        Screen.Search,
        Screen.Favorites
    )
    
    // Auth state check (simplified for now)
    var isLoggedIn by remember { mutableStateOf(DataCache.isLoggedIn) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Black,
                contentColor = Gold
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = DarkGrey
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { MobileHomeScreen(navController) }
            composable(Screen.Explore.route) { MobileExploreScreen(navController) }
            composable(Screen.Search.route) { MobileSearchScreen(navController) }
            composable(Screen.Favorites.route) { MobileFavoritesScreen(navController) }
            // Detalle y Reproductor se añaden aquí también
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Explore : Screen("explore", "Explorar", Icons.Default.Explore)
    object Search : Screen("search", "Buscar", Icons.Default.Search)
    object Favorites : Screen("favorites", "Favoritos", Icons.Default.Favorite)
}
