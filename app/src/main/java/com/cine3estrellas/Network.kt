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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import android.content.Context
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

@Serializable
data class GeoIpResponse(
    val ipAddress: String? = null,
    val countryName: String? = null,
    val regionName: String? = null,
    val cityName: String? = null
)

@Serializable
data class AppVersionResponse(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String? = null
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

    suspend fun checkForUpdate(): AppVersionResponse? {
        return try {
            httpClient.get("${WebConfig.BASE_URL}/api/app-version?t=${System.currentTimeMillis()}").body<AppVersionResponse>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Dado un listado de películas de TMDB, devuelve solo las que existen en la base de datos.
     * Hace una sola query con filtro `in` sobre los IDs para ser eficiente.
     */
    suspend fun filterMoviesInDatabase(candidates: List<Movie>): List<Movie> {
        if (candidates.isEmpty()) return emptyList()
        return try {
            val ids = candidates.map { it.id }
            // Usamos MovieIdResult (no Movie) porque Supabase solo devuelve id+poster_path,
            // y Movie.title es non-nullable sin default — causaría fallo de deserialización.
            val inDb = client.from("movies")
                .select(columns = Columns.list("id", "poster_path")) { filter { isIn("id", ids) } }
                .decodeList<MovieIdResult>()
            val inDbIds = inDb.map { it.id }.toSet()
            // Preservamos el orden original de TMDB y enriquecemos con datos de Supabase
            candidates
                .filter { it.id in inDbIds }
                .map { tmdbMovie ->
                    val dbMovie = inDb.find { it.id == tmdbMovie.id }
                    // Preferimos poster de Supabase si está disponible
                    tmdbMovie.copy(
                        poster_path = dbMovie?.poster_path ?: tmdbMovie.poster_path
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

    suspend fun updateMovieVersionCleanUrl(movieId: Int, versionKey: String, cleanUrl: String, expiresAt: Long) {
        try {
            val movie = client.from("movies")
                .select(columns = Columns.list("id", "versions")) { filter { eq("id", movieId) } }
                .decodeSingleOrNull<Movie>() ?: return

            val currentVersions = movie.versions ?: emptyMap()
            val targetVersion = currentVersions[versionKey] ?: return

            val updatedVersion = targetVersion.copy(
                cleanUrl = cleanUrl,
                cleanUrlExpiresAt = expiresAt
            )

            val updatedVersionsMap = currentVersions.toMutableMap().apply {
                put(versionKey, updatedVersion)
            }

            client.from("movies").update(mapOf("versions" to updatedVersionsMap)) {
                filter { eq("id", movieId) }
            }

            val cachedMovie = DataCache.movieDetailsMap[movieId]
            if (cachedMovie != null) {
                DataCache.movieDetailsMap[movieId] = cachedMovie.copy(versions = updatedVersionsMap)
            }

            Log.d("SupabaseManager", "Successfully updated cleanUrl in database for movie $movieId, version $versionKey")
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to update movie version cleanUrl in database", e)
        }
    }

    suspend fun clearMovieVersionCleanUrl(movieId: Int, versionKey: String) {
        try {
            val movie = client.from("movies")
                .select(columns = Columns.list("id", "versions")) { filter { eq("id", movieId) } }
                .decodeSingleOrNull<Movie>() ?: return

            val currentVersions = movie.versions ?: emptyMap()
            val targetVersion = currentVersions[versionKey] ?: return

            val updatedVersion = targetVersion.copy(
                cleanUrl = null,
                cleanUrlExpiresAt = null
            )

            val updatedVersionsMap = currentVersions.toMutableMap().apply {
                put(versionKey, updatedVersion)
            }

            client.from("movies").update(mapOf("versions" to updatedVersionsMap)) {
                filter { eq("id", movieId) }
            }

            val cachedMovie = DataCache.movieDetailsMap[movieId]
            if (cachedMovie != null) {
                DataCache.movieDetailsMap[movieId] = cachedMovie.copy(versions = updatedVersionsMap)
            }
            Log.d("SupabaseManager", "Successfully cleared invalid cleanUrl in database for movie $movieId, version $versionKey")
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to clear movie version cleanUrl in database", e)
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
            platform = "Android TV (v${BuildConfig.VERSION_NAME})",
            ip = geoData?.ipAddress ?: dbUser?.ip ?: "unknown",
            country = geoData?.countryName ?: dbUser?.country,
            province = geoData?.regionName ?: dbUser?.province,
            city = geoData?.cityName ?: dbUser?.city,
            timezone = timezone,
            screenSize = screenSize,
            lastSeen = nowIso,
            totalVisits = dbUser?.totalVisits ?: 0,
            favorites = dbUser?.favorites ?: emptyList(),
            watchHistory = dbUser?.watchHistory ?: emptyList(),
            hiddenHistory = dbUser?.hiddenHistory ?: emptyList(),
            welcomeSent = dbUser?.welcomeSent ?: false,
            watchProgress = dbUser?.watchProgress ?: emptyMap(),
            miniApp = if (dbUser?.miniApp == "✅") "✅" else "❌",
            aplicacion = "✅"
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

    suspend fun getMovieCollectionId(movieId: Int): Int? {
        return try {
            val jsonElement: kotlinx.serialization.json.JsonElement =
                httpClient.get("$BASE_URL/movie/$movieId") {
                    parameter("api_key", API_KEY)
                    parameter("language", "es-MX")
                }.body()
            val obj = jsonElement.jsonObject
            val collection = obj["belongs_to_collection"]
            if (collection is JsonObject) {
                (collection["id"] as? JsonPrimitive)?.intOrNull
            } else null
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

    suspend fun getCollectionMovies(collectionId: Int): List<Movie> {
        return try {
            val response: TmdbCollectionResponse = httpClient.get("$BASE_URL/collection/$collectionId") {
                parameter("api_key", API_KEY)
                parameter("language", "es-MX")
            }.body()
            response.parts
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMovieImages(movieId: Int): List<String> {
        return try {
            val response: TmdbImagesResponse = httpClient.get("$BASE_URL/movie/$movieId/images") {
                parameter("api_key", API_KEY)
            }.body()
            response.backdrops?.mapNotNull { it.filePath } ?: emptyList()
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

    suspend fun extractCleanUrl(embedUrl: String, movieId: Int? = null, version: String? = null): String? {
        val cached = DataCache.extractedUrlsMap[embedUrl]
        if (cached != null) {
            Log.d("VideoExtractor", "Cache hit for embedUrl: $embedUrl")
            return cached
        }

        var resolvedUrl: String? = null

        // 1. Extractor estático local (Dean Edwards packer) — funciona con minochinos.com, callistanise.com, morencius.com y tiktokshopping.xyz
        if (embedUrl.contains("minochinos.com") || embedUrl.contains("callistanise.com") || embedUrl.contains("morencius.com") || embedUrl.contains("tiktokshopping.xyz")) {
            resolvedUrl = StreamExtractor.extractWithStaticParser(embedUrl)
        }

        // 2. Si es un enlace de OK.ru, realizamos extracción local directa de HLS/MP4 de alta calidad
        if (resolvedUrl == null && (embedUrl.contains("ok.ru") || embedUrl.contains("odnoklassniki"))) {
            val result = OkExtractor.extractVideo(embedUrl)
            if (result.isSuccess) {
                val video = result.getOrNull()
                val bestStream = video?.streams?.firstOrNull()
                if (bestStream != null) {
                    resolvedUrl = bestStream.url
                }
            }
        }

        // 3. Fallback: Consumir la API de Vercel
        if (resolvedUrl == null) {
            resolvedUrl = try {
                val response: ExtractionResponse = client.get("${WebConfig.BASE_URL}/api/extract") {
                    parameter("url", embedUrl)
                }.body()
                response.qualities.firstOrNull()?.url
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (resolvedUrl != null) {
            DataCache.extractedUrlsMap[embedUrl] = resolvedUrl
            
            // Asynchronously save to central database if movie context is provided
            if (movieId != null && version != null) {
                val expiresAt = getExpirationTimestamp(resolvedUrl)
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseManager.updateMovieVersionCleanUrl(movieId, version, resolvedUrl, expiresAt)
                }
            }
        }
        return resolvedUrl
    }

    fun getExpirationTimestamp(url: String): Long {
        return try {
            val uri = java.net.URI(url)
            val query = uri.query
            if (query != null) {
                val params = query.split("&")
                for (param in params) {
                    val pair = param.split("=")
                    if (pair.size == 2 && pair[0] == "expires") {
                        val expVal = pair[1].toLongOrNull()
                        if (expVal != null) {
                            return if (expVal < 99999999999L) expVal * 1000 else expVal
                        }
                    }
                }
            }
            System.currentTimeMillis() + 2 * 60 * 60 * 1000 // default 2 hours
        } catch (e: Exception) {
            System.currentTimeMillis() + 2 * 60 * 60 * 1000
        }
    }
}

@kotlinx.serialization.Serializable
data class TmdbImagesResponse(
    val backdrops: List<TmdbImage>? = null
)

@kotlinx.serialization.Serializable
data class TmdbImage(
    @kotlinx.serialization.SerialName("file_path") val filePath: String? = null
)

