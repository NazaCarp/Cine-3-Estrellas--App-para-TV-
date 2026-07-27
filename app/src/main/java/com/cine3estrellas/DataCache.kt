package com.cine3estrellas

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Global cache to avoid re-fetching data on navigation or screen recreation
object DataCache {
    private const val PREFS_NAME = "cine3estrellas_prefs"
    private const val KEY_TELEGRAM_ID = "saved_telegram_id"
    
    // --- USER SESSION ---
    var currentUser by mutableStateOf<User?>(null)
    var isLoggedIn by mutableStateOf(false)
    var savedIdForLogin by mutableStateOf<String?>(null)

    private const val KEY_LAST_CHECK_TIME = "last_membership_check"

    fun saveSession(context: Context, user: User) {
        currentUser = user
        isLoggedIn = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TELEGRAM_ID, user.telegramId.toString())
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    fun loadSavedId(context: Context): String? {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TELEGRAM_ID, null)
        savedIdForLogin = id
        return id
    }

    fun shouldCheckMembership(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        // 24 hours in ms
        return (now - lastCheck) > 86400000L
    }

    fun updateMembershipCheckTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    fun logout(context: Context) {
        currentUser = null
        isLoggedIn = false
        savedIdForLogin = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TELEGRAM_ID)
            .remove(KEY_LAST_CHECK_TIME)
            .apply()
    }

    // --- HOME CACHE ---
    var homeHeroMovies by mutableStateOf(emptyList<Movie>())
    var homeCategories by mutableStateOf(emptyList<HomeCategory>())
    val homeCategoryMovies = mutableStateMapOf<String, List<Movie>>()
    val homeCategoryTotalCounts = mutableStateMapOf<String, Long>()
    var isHomeInitialLoaded by mutableStateOf(false)

    // --- SEARCH CACHE ---
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf(emptyList<Movie>())
    var searchTotalCount by mutableLongStateOf(0L)
    var searchLastSearchedQuery by mutableStateOf("")

    // --- EXPLORE CACHE ---
    var exploreSelectedGenre by mutableStateOf<Genre?>(null)
    var exploreGenres by mutableStateOf(emptyList<Genre>())
    var exploreMovies by mutableStateOf(emptyList<Movie>())
    var exploreTotalCount by mutableLongStateOf(0L)
    var exploreMinRating by mutableStateOf(0.0)
    var exploreSelectedYear by mutableStateOf<String?>(null)
    var exploreSortBy by mutableStateOf("popularity")
    var exploreLastFocusedGridIndex by mutableIntStateOf(0)

    // --- MOVIE DETAILS CACHE ---
    // Cache for card-level movie metadata (lightweight representation)
    val movieCardCache = mutableStateMapOf<Int, Movie>()

    fun cacheMovies(movies: List<Movie>) {
        movies.forEach { movieCardCache[it.id] = it }
    }

    // Stores detailed movie info by ID
    val movieDetailsMap = mutableStateMapOf<Int, Movie>()
    // Stores similar movies list by movie ID
    val similarMoviesMap = mutableStateMapOf<Int, List<Movie>>()

    // --- VIDEO EXTRACTION CACHE ---
    // Stores extracted direct video stream URLs by embed URL
    val extractedUrlsMap = mutableStateMapOf<String, String>()

    // --- FOCUS PERSISTENCE ---
    // Stores the ID of the last focused movie for each screen/context
    // Key: screen name (e.g., "home", "search", "explore", "category_123")
    val lastFocusedMovieId = mutableStateMapOf<String, Int>()
    
    // Stores the key of the ABSOLUTE LAST focused context to prevent multiple components from stealing focus
    var globalLastFocusedKey by mutableStateOf<String?>(null)

    // Stores the key of the ABSOLUTE LAST focused context within the Home tab specifically
    var lastHomeFocusedKey by mutableStateOf<String?>(null)

    // Trigger to re-request focus when an overlay closes
    var focusRestorationTrigger by mutableIntStateOf(0)

    // Targets to restore focus after overlay closes to avoid race conditions with default focus manager
    var movieIdToRestore by mutableStateOf<Int?>(null)
    var keyToRestore by mutableStateOf<String?>(null)
    var categoryIdToRestore by mutableStateOf<String?>(null)

    // Vertical scroll position of the main HomeScreen LazyColumn
    var homeScrollPosition by mutableStateOf(0 to 0)

    // Current slide index of the Hero carousel
    var heroCurrentIndex by mutableIntStateOf(0)

    // --- NAVIGATION FLAGS ---
    // Prevents automatic tab switching in the sidebar during sensitive transitions (like post-login)
    var isTabChangeLocked by mutableStateOf(false)

    // --- SCROLL PERSISTENCE ---
    // Key: category ID or row unique key. Value: Pair(index, offset)
    val rowScrollPositions = mutableStateMapOf<String, Pair<Int, Int>>()
}
