package com.cine3estrellas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class OkVideo(
    val id: String,
    val title: String,
    val posterUrl: String,
    val streams: List<OkStream>
)

data class OkStream(
    val quality: String,     // e.g. "mobile", "lowest", "low", "sd", "hd", "full"
    val displayName: String, // e.g. "720p (Alta Definición HD)"
    val url: String
)

object OkExtractor {
    private const val TAG = "OkExtractor"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Extracts numeric video ID from an OK.ru URL.
     */
    fun extractVideoId(urlOrId: String): String? {
        val cleaned = urlOrId.trim()
        if (cleaned.all { it.isDigit() }) return cleaned

        // Patrones comunes:
        // https://ok.ru/video/13045483309731
        // https://ok.ru/videoembed/13045483309731?autoplay=1
        // https://m.ok.ru/video/13045483309731
        val patterns = listOf(
            """/video/(\d+)""".toRegex(),
            """/videoembed/(\d+)""".toRegex(),
            """/live/(\d+)""".toRegex(),
            """\bid=(\d+)""".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(cleaned)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Fetches the video page HTML and parses information.
     */
    suspend fun extractVideo(urlOrId: String): Result<OkVideo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(urlOrId)
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo extraer una ID de video válida del enlace."))

            val embedUrl = "https://ok.ru/videoembed/$videoId"
            
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error de conexión con OK.ru: código ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            if (html.isEmpty()) {
                return@withContext Result.failure(Exception("La página web de OK.ru devolvió un contenido vacío."))
            }

            // 1. Extraer título
            var title = "Video de OK.ru ($videoId)"
            val ogTitleMatch = """<meta[^>]*property="og:title"[^>]*content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE).find(html)
            if (ogTitleMatch != null) {
                title = decodeHtmlEntities(ogTitleMatch.groupValues[1])
            } else {
                val jsonTitleMatch = """[\\]*"title[\\]*"\s*:\s*[\\]*"((?:[^"\\]|\\.)+)"""".toRegex().find(html)
                if (jsonTitleMatch != null) {
                    title = decodeHtmlEntities(jsonTitleMatch.groupValues[1])
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                }
            }

            // 2. Extraer póster
            var posterUrl = ""
            val ogImageMatch = """<meta[^>]*property="og:image"[^>]*content="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE).find(html)
            if (ogImageMatch != null) {
                posterUrl = decodeHtmlEntities(ogImageMatch.groupValues[1])
            } else {
                val jsonPosterMatch = """[\\]*"poster[\\]*"\s*:\s*[\\]*"((?:[^"\\]|\\.)+)"""".toRegex().find(html)
                if (jsonPosterMatch != null) {
                    posterUrl = decodeHtmlEntities(jsonPosterMatch.groupValues[1])
                        .replace("\\/", "/")
                        .replace("\\u0026", "&")
                }
            }

            // 3. Extraer streams de video
            // Decodificamos las entidades HTML de la página completa para facilitar la búsqueda
            val decodedHtml = decodeHtmlEntities(html)

            // Buscamos la lista de videos con o sin comillas escapadas
            val videosPattern = """[\\]*"videos[\\]*"\s*:\s*\[([\s\S]*?)\]""".toRegex()
            val videosBlockMatch = videosPattern.find(decodedHtml)

            val streams = mutableListOf<OkStream>()

            if (videosBlockMatch != null) {
                val videosArrayStr = videosBlockMatch.groupValues[1]
                
                // Sanear el bloque completo para tener un formato JSON limpio y sin comillas escapadas ni urls con dobles barras
                val cleanBlocks = videosArrayStr
                    .replace("""[\\]+"""".toRegex(), "\"")
                    .replace("""[\\]+/""".toRegex(), "/")
                    .replace("""[\\]+u0026""".toRegex(), "&")
                    .replace("&amp;", "&")
                
                // Buscamos bloques individuales entre llaves: { ... }
                val blockPattern = """\{([^}]+)\}""".toRegex()
                val namePattern = """"name"\s*:\s*"([^"]+)"""".toRegex()
                val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
                
                val blocks = blockPattern.findAll(cleanBlocks)
                for (block in blocks) {
                    val content = block.value
                    val nameMatch = namePattern.find(content)
                    val urlMatch = urlPattern.find(content)
                    
                    if (nameMatch != null && urlMatch != null) {
                        val rawName = nameMatch.groupValues[1]
                        val rawUrl = urlMatch.groupValues[1]
                        
                        val displayName = mapQualityName(rawName)
                        streams.add(OkStream(
                            quality = rawName,
                            displayName = displayName,
                            url = rawUrl
                        ))
                    }
                }
            }

            // Fallback si no se encontró nada: busquemos cualquier ocurrencia directa de url que contenga hls o mp4 firma de okru
            if (streams.isEmpty()) {
                val directUrlPattern = """[\\]*"url[\\]*"\s*:\s*[\\]*"([^"]+okcdn\b[^"]+|[^"]+ok\.ru\b[^"]+)"""".toRegex()
                val matches = directUrlPattern.findAll(decodedHtml)
                var count = 1
                for (match in matches) {
                    val rawUrl = match.groupValues[1]
                        .replace("""[\\]+"""".toRegex(), "")
                        .replace("""[\\]+/""".toRegex(), "/")
                        .replace("""[\\]+u0026""".toRegex(), "&")
                        .replace("&amp;", "&")
                    if (streams.none { it.url == rawUrl }) {
                        streams.add(OkStream(
                            quality = "sd_$count",
                            displayName = "Calidad Estándar $count",
                            url = rawUrl
                        ))
                        count++
                    }
                }
            }

            // Ordenar streams por calidad de mayor a menor
            val qualityOrder = listOf("ultra", "quad", "full", "hd", "sd", "low", "lowest", "mobile")
            val sortedStreams = streams.sortedWith(Comparator { a, b ->
                val indexA = qualityOrder.indexOf(a.quality)
                val indexB = qualityOrder.indexOf(b.quality)
                when {
                    indexA == -1 && indexB == -1 -> 0
                    indexA == -1 -> 1
                    indexB == -1 -> -1
                    else -> indexA.compareTo(indexB)
                }
            })

            if (sortedStreams.isEmpty()) {
                return@withContext Result.failure(Exception("No se encontraron enlaces de reproducción públicos para este video en OK.ru."))
            }

            Log.d(TAG, "Extracción exitosa: ID=$videoId, Título=$title, Arroyos=${sortedStreams.size}")
            Result.success(OkVideo(
                id = videoId,
                title = title,
                posterUrl = posterUrl,
                streams = sortedStreams
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error en la extracción para $urlOrId", e)
            Result.failure(e)
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
    }

    private fun mapQualityName(name: String): String {
        return when (name.lowercase()) {
            "mobile" -> "144p (Móvil)"
            "lowest" -> "240p (Muy baja)"
            "low" -> "360p (Baja)"
            "sd" -> "480p (Estándar SD)"
            "hd" -> "720p (Alta Definición HD)"
            "full" -> "1080p (Alta Definición Full HD)"
            "quad" -> "1440p (2K Ultra HD)"
            "ultra" -> "2160p (4K Ultra HD)"
            else -> name.uppercase()
        }
    }
}
