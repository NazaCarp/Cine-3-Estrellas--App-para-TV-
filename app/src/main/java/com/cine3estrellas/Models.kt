package com.cine3estrellas

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val telegramId: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class Movie(
    val id: Int,
    val title: String,
    val versions: Map<String, MovieVersion>? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val genre_ids: List<Int>? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val certification: String? = null,
    val overview: String? = null, // From TMDB
    val runtime: Int? = null,    // From TMDB
    val genres: List<Genre>? = null, // From TMDB
    val original_language: String? = null, // From TMDB
    val created_at: String? = null,
    val keywords: List<String>? = null
)

@Serializable
data class MovieVersion(
    val url: String,
    val quality: String,
    val downloadUrl: String? = null
)

@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class HomeCategory(
    val id: String,
    val name: String,
    val icon: String,
    val order: Int,
    val show_in_home: Boolean,
    val genre_ids: List<Int>? = null,
    val keywords: List<String>? = null,
    val min_rating: Double? = 0.0,
    val sort_by: String? = "created_at"
)

data class MovieDetails(
    val movie: Movie,
    val similarMovies: List<Movie> = emptyList()
)
