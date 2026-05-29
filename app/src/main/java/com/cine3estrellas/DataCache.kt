package com.cine3estrellas

import android.content.Context
import androidx.compose.runtime.getValue
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

    fun saveSession(context: Context, user: User) {
        currentUser = user
        isLoggedIn = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TELEGRAM_ID, user.telegramId)
            .apply()
    }

    fun loadSavedId(context: Context): String? {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TELEGRAM_ID, null)
        savedIdForLogin = id
        return id
    }

    fun logout(context: Context) {
        currentUser = null
        isLoggedIn = false
        savedIdForLogin = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TELEGRAM_ID)
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

    // --- MOVIE DETAILS CACHE ---
    // Stores detailed movie info by ID
    val movieDetailsMap = mutableStateMapOf<Int, Movie>()
    // Stores similar movies list by movie ID
    val similarMoviesMap = mutableStateMapOf<Int, List<Movie>>()

    // --- FOCUS PERSISTENCE ---
    // Stores the ID of the last focused movie for each screen/context
    // Key: screen name (e.g., "home", "search", "explore", "category_123")
    val lastFocusedMovieId = mutableStateMapOf<String, Int>()
    
    // Stores the key of the ABSOLUTE LAST focused context to prevent multiple components from stealing focus
    var globalLastFocusedKey by mutableStateOf<String?>(null)
}
