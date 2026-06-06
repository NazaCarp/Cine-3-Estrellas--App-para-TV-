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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import android.content.Context
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from

@Serializable
data class GeoIpResponse(
    val ipAddress: String? = null,
    val countryName: String? = null,
    val regionName: String? = null,
    val cityName: String? = null
)

object SupabaseManager {
    private const val URL = "https://febhxbpcorixullzsfgz.supabase.co"
    private const val KEY = "sb_publishable_ix-Edo5azRfUteN0V0h1FQ_GtiH4pqQ"

    val client: SupabaseClient = createSupabaseClient(URL, KEY) {
        install(Postgrest)
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun fetchTvHomeData(): TvHomeResponse? {
        return try {
            httpClient.get("${WebConfig.BASE_URL}/api/tv/home").body<TvHomeResponse>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchUser(telegramId: Long): User? {
        return try {
            client.from("users").select {
                filter {
                    eq("id", telegramId)
                }
            }.decodeSingleOrNull<User>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun findUserByAuthCode(code: String): User? {
        return try {
            client.from("users").select {
                filter {
                    eq("auth_code", code)
                }
            }.decodeSingleOrNull<User>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isCodeExpired(expiresAtStr: String?): Boolean {
        if (expiresAtStr.isNullOrBlank()) return true
        return try {
            val expires = java.time.OffsetDateTime.parse(expiresAtStr)
            val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            now.isAfter(expires)
        } catch (e: Exception) {
            try {
                val expires = java.time.Instant.parse(expiresAtStr)
                val now = java.time.Instant.now()
                now.isAfter(expires)
            } catch (e2: Exception) {
                e2.printStackTrace()
                true
            }
        }
    }

    suspend fun clearAuthCode(telegramId: Long) {
        try {
            val updateData = kotlinx.serialization.json.buildJsonObject {
                put("auth_code", kotlinx.serialization.json.JsonNull)
                put("auth_code_expires_at", kotlinx.serialization.json.JsonNull)
            }
            client.from("users").update(updateData) {
                filter {
                    eq("id", telegramId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun upsertUser(user: User): User? {
        return try {
            client.from("users").upsert(user) {
                select()
            }.decodeSingleOrNull<User>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun incrementDailyStats(isNew: Boolean, isUnique: Boolean) {
        try {
            client.postgrest.rpc(
                function = "increment_daily_stats",
                parameters = buildJsonObject {
                    put("is_new", isNew)
                    put("is_unique", isUnique)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun logEvent(event: DbEvent) {
        try {
            client.from("events").insert(event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFormattedTimestamp(): String {
        return try {
            val zoneId = java.time.ZoneId.of("America/Argentina/Buenos_Aires")
            val zonedDateTime = java.time.ZonedDateTime.now(zoneId)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:00x")
            zonedDateTime.format(formatter)
        } catch (e: Exception) {
            val now = java.time.Instant.now()
            java.time.format.DateTimeFormatter.ISO_INSTANT.format(now)
        }
    }

    suspend fun syncUser(context: Context, telegramUser: User): User? {
        val geoData = try {
            httpClient.get("https://freeipapi.com/api/json").body<GeoIpResponse>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val dbUser = fetchUser(telegramUser.telegramId)

        val metrics = context.resources.displayMetrics
        val screenSize = "${metrics.widthPixels}x${metrics.heightPixels}"
        val timezone = try {
            java.util.TimeZone.getDefault().id
        } catch (e: Exception) {
            "America/Argentina/Buenos_Aires"
        }

        val nowIso = getFormattedTimestamp()
        val updatedUser = User(
            telegramId = telegramUser.telegramId,
            firstName = telegramUser.firstName ?: dbUser?.firstName,
            lastName = telegramUser.lastName ?: dbUser?.lastName,
            username = telegramUser.username ?: dbUser?.username,
            languageCode = telegramUser.languageCode ?: dbUser?.languageCode,
            isPremium = telegramUser.isPremium || (dbUser?.isPremium ?: false),
            allowsWriteToPm = telegramUser.allowsWriteToPm || (dbUser?.allowsWriteToPm ?: false),
            platform = "android",
            version = "v1.0.4",
            ip = geoData?.ipAddress ?: dbUser?.ip ?: "unknown",
            country = geoData?.countryName ?: dbUser?.country,
            province = geoData?.regionName ?: dbUser?.province,
            city = geoData?.cityName ?: dbUser?.city,
            timezone = timezone,
            browser = "Android TV App",
            screenSize = screenSize,
            lastSeen = nowIso,
            totalVisits = dbUser?.totalVisits ?: 0,
            favorites = dbUser?.favorites ?: emptyList(),
            watchHistory = dbUser?.watchHistory ?: emptyList(),
            hiddenHistory = dbUser?.hiddenHistory ?: emptyList(),
            welcomeSent = dbUser?.welcomeSent ?: false,
            watchProgress = dbUser?.watchProgress ?: emptyMap()
        )

        val savedUser = upsertUser(updatedUser) ?: updatedUser

        // Exclude the administrator (5022144726) from stats increment, matching next.js userService.ts
        val ADMIN_ID = 5022144726L
        if (telegramUser.telegramId != ADMIN_ID) {
            val todayStr = java.time.LocalDate.now().toString()
            val lastSeenStr = dbUser?.lastSeen?.take(10)
            val isNewUser = dbUser == null
            val isUniqueToday = isNewUser || lastSeenStr != todayStr
            incrementDailyStats(isNew = isNewUser, isUnique = isUniqueToday)
        }

        return savedUser
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
        // 1. Extractor estático local (Dean Edwards packer) — funciona con minochinos.com y callistanise.com
        if (embedUrl.contains("minochinos.com") || embedUrl.contains("callistanise.com")) {
            val resolvedUrl = StreamExtractor.extractWithStaticParser(embedUrl)
            if (resolvedUrl != null) {
                return resolvedUrl
            }
        }

        // 2. Si es un enlace de OK.ru, realizamos extracción local directa de HLS/MP4 de alta calidad
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

