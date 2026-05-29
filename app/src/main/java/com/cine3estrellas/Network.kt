package com.cine3estrellas

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object SupabaseManager {
    private const val URL = "https://febhxbpcorixullzsfgz.supabase.co"
    private const val KEY = "sb_publishable_ix-Edo5azRfUteN0V0h1FQ_GtiH4pqQ"

    val client: SupabaseClient = createSupabaseClient(URL, KEY) {
        install(Postgrest)
    }
}

object TmdbManager {
    private const val API_KEY = "4edaa0d4c185a5d946ffbd2a4f25e27a"
    private const val BASE_URL = "https://api.themoviedb.org/3"

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun getMovieDetails(movieId: Int): Movie? {
        return try {
            // Try Latin American Spanish first
            var movie: Movie = httpClient.get("$BASE_URL/movie/$movieId") {
                parameter("api_key", API_KEY)
                parameter("language", "es-MX")
            }.body()
            
            // If empty, try Spain Spanish
            if (movie.overview.isNullOrBlank()) {
                val spainMovie: Movie = httpClient.get("$BASE_URL/movie/$movieId") {
                    parameter("api_key", API_KEY)
                    parameter("language", "es-ES")
                }.body()
                if (!spainMovie.overview.isNullOrBlank()) {
                    movie = movie.copy(overview = spainMovie.overview)
                }
            }

            // Fallback to English if still empty
            if (movie.overview.isNullOrBlank()) {
                val englishMovie: Movie = httpClient.get("$BASE_URL/movie/$movieId") {
                    parameter("api_key", API_KEY)
                    parameter("language", "en-US")
                }.body()
                movie = movie.copy(overview = englishMovie.overview)
            }
            
            movie
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSimilarMovies(movieId: Int): List<Movie> {
        return try {
            // Primero intentamos con similar
            var response: TmdbResponse = httpClient.get("$BASE_URL/movie/$movieId/similar") {
                parameter("api_key", API_KEY)
                parameter("language", "es-MX")
            }.body()
            
            // Si está vacío, intentamos con recommendations (que suele estar más completo)
            if (response.results.isEmpty()) {
                response = httpClient.get("$BASE_URL/movie/$movieId/recommendations") {
                    parameter("api_key", API_KEY)
                    parameter("language", "es-MX")
                }.body()
            }

            response.results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getGenres(): List<Genre> {
        return try {
            val response: GenreResponse = httpClient.get("$BASE_URL/genre/movie/list") {
                parameter("api_key", API_KEY)
                parameter("language", "es-ES")
            }.body()
            if (response.genres.isNullOrEmpty()) getFallbackGenres() else response.genres
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackGenres()
        }
    }

    private fun getFallbackGenres(): List<Genre> {
        return listOf(
            Genre(28, "Acción"),
            Genre(12, "Aventura"),
            Genre(16, "Animación"),
            Genre(35, "Comedia"),
            Genre(80, "Crimen"),
            Genre(99, "Documental"),
            Genre(18, "Drama"),
            Genre(10751, "Familia"),
            Genre(14, "Fantasía"),
            Genre(36, "Historia"),
            Genre(27, "Terror"),
            Genre(10402, "Música"),
            Genre(9648, "Misterio"),
            Genre(10749, "Romance"),
            Genre(878, "Ciencia ficción"),
            Genre(10770, "Película de TV"),
            Genre(53, "Suspense"),
            Genre(10752, "Bélica"),
            Genre(37, "Western")
        )
    }
}

@kotlinx.serialization.Serializable
data class TmdbResponse(
    val results: List<Movie>
)

@kotlinx.serialization.Serializable
data class GenreResponse(
    val genres: List<Genre>
)

@kotlinx.serialization.Serializable
data class ExtractionResponse(
    val qualities: List<ExtractionQuality> = emptyList()
)

@kotlinx.serialization.Serializable
data class ExtractionQuality(
    val name: String,
    val url: String
)

object VideoExtractor {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun extractCleanUrl(embedUrl: String): String? {
        // 1. Si es un enlace de OK.ru, realizamos extracción local directa de HLS/MP4 de alta calidad
        if (embedUrl.contains("ok.ru") || embedUrl.contains("odnoklassniki")) {
            val result = OkExtractor.extractVideo(embedUrl)
            if (result.isSuccess) {
                val video = result.getOrNull()
                val bestStream = video?.streams?.firstOrNull()
                if (bestStream != null) {
                    return bestStream.url
                }
            }
        }

        // 2. Fallback: Consumir la API de Vercel
        return try {
            val response: ExtractionResponse = client.get("${WebConfig.BASE_URL}/api/extract") {
                parameter("url", embedUrl)
            }.body()
            response.qualities.firstOrNull()?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

