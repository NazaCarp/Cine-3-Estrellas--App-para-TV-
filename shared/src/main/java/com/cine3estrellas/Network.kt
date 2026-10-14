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

    /**
     * Recupera las versiones (calidades/enlaces) de una lista de películas en una sola consulta.
     */
    suspend fun fetchMoviesVersions(ids: List<Int>): List<MovieVersionsResult> {
        if (ids.isEmpty()) return emptyList()
        return try {
            client.from("movies")
                .select(columns = Columns.list("id", "versions")) { filter { isIn("id", ids) } }
                .decodeList<MovieVersionsResult>()
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
            throw e
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
            Log.d("SupabaseManager", "Upserting user: ${user.telegramId}, favorites count: ${user.favorites.size}")
            val response = client.from("users").upsert(user) {
                select()
            }.decodeSingleOrNull<User>()
            if (response != null) {
                Log.d("SupabaseManager", "Upsert successful. New favorites count: ${response.favorites.size}")
            } else {
                Log.e("SupabaseManager", "Upsert failed: Server returned null")
            }
            response
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Upsert exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Updates ONLY the favorites column for a user.
     * Safer than upsertUser as it doesn't touch other potentially sensitive or complex columns.
     */
    suspend fun updateUserFavorites(telegramId: Long, favorites: List<FavoriteItem>): Boolean {
        return try {
            val updateData = kotlinx.serialization.json.buildJsonObject {
                // Manually build the JSON array of favorites to avoid complex serializer issues
                val array = kotlinx.serialization.json.buildJsonArray {
                    favorites.forEach { item ->
                        add(kotlinx.serialization.json.buildJsonObject {
                            put("id", item.id)
                            put("title", item.title ?: "")
                        })
                    }
                }
                put("favorites", array)
            }
            
            client.from("users").update(updateData) {
                filter {
                    eq("id", telegramId)
                }
            }
            Log.d("SupabaseManager", "Favorites column updated successfully for user $telegramId")
            true
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to update favorites column: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateUserWatchHistory(telegramId: Long, watchHistory: List<Int>): Boolean {
        return try {
            val updateData = kotlinx.serialization.json.buildJsonObject {
                val array = kotlinx.serialization.json.buildJsonArray {
                    watchHistory.forEach { id ->
                        add(kotlinx.serialization.json.JsonPrimitive(id))
                    }
                }
                put("watch_history", array)
            }

            client.from("users").update(updateData) {
                filter {
                    eq("id", telegramId)
                }
            }
            Log.d("SupabaseManager", "Watch history column updated successfully for user $telegramId")
            true
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to update watch_history column: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun updateUserWatchProgress(telegramId: Long, watchProgress: Map<String, WatchProgressVal>): Boolean {
        return try {
            val updateData = kotlinx.serialization.json.buildJsonObject {
                val progressObj = kotlinx.serialization.json.buildJsonObject {
                    watchProgress.forEach { (movieId, valProgress) ->
                        put(movieId, kotlinx.serialization.json.buildJsonObject {
                            put("time", valProgress.time)
                            put("updatedAt", valProgress.updatedAt)
                        })
                    }
                }
                put("watch_progress", progressObj)
            }

            client.from("users").update(updateData) {
                filter {
                    eq("id", telegramId)
                }
            }
            Log.d("SupabaseManager", "Watch progress column updated successfully for user $telegramId")
            true
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to update watch_progress column: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Actualiza watch_history y watch_progress en una sola operación atómica.
     * Usar cuando ambas columnas cambian juntas (p. ej., quitar una película del historial)
     * para evitar estados intermedios inconsistentes en la base de datos.
     */
    suspend fun updateUserHistory(
        telegramId: Long,
        watchHistory: List<Int>,
        watchProgress: Map<String, WatchProgressVal>
    ): Boolean {
        return try {
            val updateData = kotlinx.serialization.json.buildJsonObject {
                put("watch_history", kotlinx.serialization.json.buildJsonArray {
                    watchHistory.forEach { id ->
                        add(kotlinx.serialization.json.JsonPrimitive(id))
                    }
                })
                put("watch_progress", kotlinx.serialization.json.buildJsonObject {
                    watchProgress.forEach { (movieId, valProgress) ->
                        put(movieId, kotlinx.serialization.json.buildJsonObject {
                            put("time", valProgress.time)
                            put("updatedAt", valProgress.updatedAt)
                        })
                    }
                })
            }

            client.from("users").update(updateData) {
                filter {
                    eq("id", telegramId)
                }
            }
            Log.d("SupabaseManager", "History columns updated atomically for user $telegramId")
            true
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to update history columns: ${e.message}")
            e.printStackTrace()
            false
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

    fun reportPlaybackErrorAsync(
        movieTitle: String,
        version: String,
        server: String?,
        streamUrl: String?,
        errorCode: String?,
        httpStatus: String?,
        errorDetail: String?,
        user: User?,
        device: String?,
        failedResourceUrl: String? = null,
        playbackPosition: String? = null,
        connectionType: String? = null,
        deviceModel: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://cine3estrellas-xi.vercel.app/api/admin/report-error")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val userObj = org.json.JSONObject().apply {
                    if (user != null) {
                        put("id", user.telegramId)
                        put("full_name", user.displayName)
                        put("username", user.username ?: "")
                    }
                }

                val json = org.json.JSONObject().apply {
                    put("movieTitle", movieTitle)
                    put("version", version)
                    put("server", server ?: "N/A")
                    put("streamUrl", streamUrl ?: "N/A")
                    put("errorCode", errorCode ?: "N/A")
                    put("httpStatus", httpStatus ?: "N/A")
                    put("error", errorDetail ?: "Fallo de reproducción")
                    if (failedResourceUrl != null) put("failedResourceUrl", failedResourceUrl)
                    if (playbackPosition != null) put("playbackPosition", playbackPosition)
                    if (connectionType != null) put("connectionType", connectionType)
                    if (deviceModel != null) put("deviceModel", deviceModel)
                    if (user != null) {
                        put("user", userObj)
                    }
                    put("device", device ?: "Android")
                }

                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val code = conn.responseCode
                Log.d("SupabaseManager", "Auto-reported error to Telegram: HTTP $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Failed to auto-report error: ${e.message}")
            }
        }
    }

    suspend fun syncUser(context: Context, telegramUser: User, platformName: String): User? {
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

        val combinedFullName = telegramUser.fullName
            ?: listOfNotNull(telegramUser.firstName, telegramUser.lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
            ?: dbUser?.fullName
            ?: listOfNotNull(dbUser?.firstName, dbUser?.lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }

        val nowIso = getFormattedTimestamp()
        val updatedUser = User(
            telegramId = telegramUser.telegramId,
            fullName = combinedFullName,
            firstName = null,
            lastName = null,
            username = telegramUser.username ?: dbUser?.username,
            languageCode = telegramUser.languageCode ?: dbUser?.languageCode,
            isPremium = telegramUser.isPremium || (dbUser?.isPremium ?: false),
            allowsWriteToPm = telegramUser.allowsWriteToPm || (dbUser?.allowsWriteToPm ?: false),
            platform = platformName,
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

    suspend fun extractCleanUrl(embedUrl: String, movieId: Int? = null, version: String? = null, forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            val cached = DataCache.getCachedStreamUrl(embedUrl)
            if (cached != null) {
                Log.d("VideoExtractor", "Cache hit (valid TTL) for embedUrl: $embedUrl")
                return cached
            }
        } else {
            DataCache.invalidateStreamUrl(embedUrl)
        }

        var resolvedUrl: String? = null

        // 1. Extractor estático local (Dean Edwards packer) — funciona con minochinos.com, callistanise.com, morencius.com, tiktokshopping.xyz y vidhidefast.com
        if (embedUrl.contains("minochinos.com") || embedUrl.contains("callistanise.com") || embedUrl.contains("morencius.com") || embedUrl.contains("tiktokshopping.xyz") || embedUrl.contains("vidhidefast.com")) {
            // Las páginas viewer (/v/) de vidhidefast no traen el player empaquetado; su /embed/ equivalente sí
            val staticUrl = embedUrl.replaceFirst("vidhidefast.com/v/", "vidhidefast.com/embed/")
            resolvedUrl = StreamExtractor.extractWithStaticParser(staticUrl)
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
                // Preferir mirrors con playlist .m3u8 real; los master.txt del CDN de vidhide
                // no se detectan como HLS en todos los players
                response.qualities.firstOrNull { it.url.contains(".m3u8") }?.url
                    ?: response.qualities.firstOrNull()?.url
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val finalUrl = resolvedUrl?.let { normalizeStreamUrl(it) }
        if (finalUrl != null) {
            DataCache.setCachedStreamUrl(embedUrl, finalUrl)
        }
        return finalUrl
    }

    /**
     * Normaliza las URLs que devuelve la API de Vercel: llegan como rutas relativas a su
     * proxy (`/api/proxy?url=<url del CDN codificada>`), pensadas para el navegador de la
     * web. Desenvuelve el parámetro y devuelve la URL absoluta del CDN; si no hay nada
     * que desenvolver, la devuelve intacta.
     */
    fun normalizeStreamUrl(url: String): String {
        val key = "url="
        val index = url.indexOf(key)
        if (index == -1) return url
        val encoded = url.substring(index + key.length)
        val decoded = try {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            return url
        }
        return if (decoded.startsWith("http")) decoded else url
    }

    /**
     * Detecta si una URL apunta a un playlist HLS. Además del clásico `.m3u8`, vidhide
     * sirve playlists con extensión `.txt` y paths `/hls2/` `/hls3/` para evadir detección.
     */
    fun isHlsStreamUrl(url: String): Boolean {
        return url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".urlset/", ignoreCase = true) ||
            url.contains("master.txt", ignoreCase = true) ||
            Regex("/hls\\d*/", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    /**
     * Cuando el CDN del stream elegido devuelve errores 5xx sostenidos (p. ej. 522 de
     * redirector.cdnsync.cloud), intenta conmutar al siguiente CDN candidato de la
     * extracción previa (hls3/hls2 de vidhide en acek-cdn, etc.). Devuelve la nueva URL
     * validada y la deja cacheada, o null si no hay alternativa.
     */
    suspend fun extractAlternativeCandidate(embedUrl: String, currentUrl: String): String? {
        if (!(embedUrl.contains("minochinos.com") || embedUrl.contains("callistanise.com") ||
              embedUrl.contains("morencius.com") || embedUrl.contains("tiktokshopping.xyz") ||
              embedUrl.contains("vidhidefast.com"))) {
            return null
        }
        val staticUrl = embedUrl.replaceFirst("vidhidefast.com/v/", "vidhidefast.com/embed/")
        val alt = StreamExtractor.tryNextCandidate(staticUrl, currentUrl, staticUrl) ?: return null
        val finalUrl = normalizeStreamUrl(alt)
        DataCache.setCachedStreamUrl(embedUrl, finalUrl)
        Log.d("VideoExtractor", "Switched to alternative CDN candidate: $finalUrl")
        return finalUrl
    }

    /**
     * Invalida la URL de stream cacheada en memoria para un embed cuando el player detectó
     * que el servidor la rechazó (código HTTP) o el contenido no se pudo reproducir. El
     * próximo intento re-extracte en vez de reutilizar el enlace roto.
     */
    fun invalidateCachedUrl(embedUrl: String) {
        DataCache.invalidateStreamUrl(embedUrl)
        Log.d("VideoExtractor", "Invalidated cached stream URL for embed: $embedUrl")
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

