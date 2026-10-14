package com.cine3estrellas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object StreamExtractor {
    private const val TAG = "StreamExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val preflightClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val evalPattern = Pattern.compile(
        "eval\\s*\\(\\s*function\\s*\\(\\s*p\\s*,\\s*a\\s*,\\s*c\\s*,\\s*k\\s*,\\s*e\\s*,\\s*d\\s*\\).*?\\}\\s*\\(\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'\\.split\\(\\s*['\"]\\|['\"]\\s*\\)",
        Pattern.DOTALL
    )

    /**
     * Decodes a Dean Edwards packed Javascript block.
     * Extracts tokens matching words, converting base-a string representations
     * to their actual index in vocabulary dictionary list [k].
     */
    fun unpack(p: String, a: Int, c: Int, k: List<String>): String {
        val wordRegex = Regex("\\b[0-9a-zA-Z]+\\b")
        return wordRegex.replace(p) { matchResult ->
            val word = matchResult.value
            val index = toInt(word, a)
            if (index in k.indices && k[index].isNotEmpty()) {
                k[index]
            } else {
                word
            }
        }
    }

    private fun toInt(s: String, base: Int): Int {
        var res = 0
        for (char in s) {
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in 'a'..'z' -> char - 'a' + 10
                in 'A'..'Z' -> char - 'A' + 36
                else -> return -1
            }
            if (digit >= base) return -1
            res = res * base + digit
        }
        return res
    }

    /**
     * Resolves relative paths (e.g. /stream/...) against the embed URL origin.
     */
    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            relativeOrAbsolute
        } else {
            try {
                val uri = URI(baseUrl)
                val portStr = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                val path = if (relativeOrAbsolute.startsWith("/")) relativeOrAbsolute else "/$relativeOrAbsolute"
                "${uri.scheme}://${uri.host}$portStr$path"
            } catch (e: Exception) {
                relativeOrAbsolute
            }
        }
    }

    /**
     * Validates if an m3u8 URL responds with HTTP 200 OK and contains #EXTM3U.
     */
    private fun validateM3u8(m3u8Url: String, referer: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(m3u8Url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", referer)
                .build()

            preflightClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream() ?: return@use false
                    val buffer = ByteArray(128)
                    val read = stream.read(buffer)
                    if (read > 0) {
                        String(buffer, 0, read).contains("#EXTM3U")
                    } else false
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Candidate streams (ordered) found in the last extraction per embed URL.
     * Vidhide sirve el mismo video en varios CDNs (hls4 /stream → TikTok CDN,
     * hls3/hls2 en acek-cdn, etc.): si el CDN elegido devuelve 5xx sostenidos
     * (p. ej. segmentos vía redirector.cdnsync.cloud caído con 522), el player
     * puede conmutar al siguiente candidato de esta lista.
     */
    private val lastOrderedCandidates = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /**
     * Devuelve el siguiente candidato validado (200 OK + #EXTM3U) posterior a
     * [currentUrl] en la lista ordenada de la extracción previa de [embedUrl],
     * o null si no hay alternativa funcional.
     */
    suspend fun tryNextCandidate(embedUrl: String, currentUrl: String, referer: String): String? = withContext(Dispatchers.IO) {
        val list = lastOrderedCandidates[embedUrl] ?: return@withContext null
        val startIdx = list.indexOf(currentUrl).let { if (it >= 0) it + 1 else 0 }
        for (candidate in list.drop(startIdx)) {
            if (candidate == currentUrl) continue
            if (validateM3u8(candidate, referer)) {
                Log.d(TAG, "Alternativa de CDN validada: $candidate")
                return@withContext candidate
            }
        }
        null
    }

    /**
     * Attempts to fetch and extract the direct stream URL statically.
     * Works on any thread (suspends to Dispatchers.IO).
     */
    suspend fun extractWithStaticParser(embedUrl: String): String? = withContext(Dispatchers.IO) {
        val targetUrl = if (embedUrl.startsWith("http://")) {
            embedUrl.replace("http://", "https://")
        } else if (!embedUrl.startsWith("http")) {
            "https://$embedUrl"
        } else {
            embedUrl
        }

        val embedHost = try {
            URI(targetUrl).host?.lowercase()
        } catch (e: Exception) {
            null
        }

        val referer = try {
            val uri = URI(targetUrl)
            "${uri.scheme}://${uri.host}/"
        } catch (e: Exception) {
            "https://minochinos.com/"
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "es-ES,es;q=0.8,en-US;q=0.5,en;q=0.3")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP request failed with status: ${response.code}")
                    return@withContext null
                }
                val html = response.body?.string() ?: return@withContext null

                val candidates = mutableListOf<String>()
                val prioritizedFromScript = mutableListOf<String>()

                val matcher = evalPattern.matcher(html)
                if (matcher.find()) {
                    val p = matcher.group(1) ?: ""
                    val aStr = matcher.group(2) ?: "36"
                    val cStr = matcher.group(3) ?: "0"
                    val kStr = matcher.group(4) ?: ""

                    val a = aStr.toIntOrNull() ?: 36
                    val c = cStr.toIntOrNull() ?: 0
                    val k = kStr.split("|")

                    val unpacked = unpack(p, a, c, k)
                    Log.d(TAG, "Unpacked successfully! Characters: ${unpacked.length}")

                    // 1. Buscar objeto links = {"hls4":"...", "hls3":"...", "hls2":"..."}
                    // En el reproductor web oficial de Vidhide el orden es: links.hls4 || links.hls3 || links.hls2
                    // hls4 (/stream/) almacena los chunks en la CDN global de TikTok (sin caídas ni errores 502)
                    val linksPattern = Pattern.compile("(?:var\\s+links\\s*=\\s*\\{([^}]+)\\}|links\\s*=\\s*\\{([^}]+)\\})")
                    val linksMatcher = linksPattern.matcher(unpacked)
                    if (linksMatcher.find()) {
                        val linksBody = linksMatcher.group(1) ?: linksMatcher.group(2) ?: ""
                        fun extractField(body: String, field: String): String? {
                            val fPattern = Pattern.compile("\"?$field\"?\\s*:\\s*\"([^\"]+)\"")
                            val fMatcher = fPattern.matcher(body)
                            return if (fMatcher.find()) fMatcher.group(1)?.replace("\\", "") else null
                        }
                        val hls4 = extractField(linksBody, "hls4")
                        val hls3 = extractField(linksBody, "hls3")
                        val hls2 = extractField(linksBody, "hls2")

                        listOfNotNull(hls4, hls3, hls2).forEach { raw ->
                            val resolved = resolveUrl(targetUrl, raw)
                            if (!prioritizedFromScript.contains(resolved)) prioritizedFromScript.add(resolved)
                            if (!candidates.contains(resolved)) candidates.add(resolved)
                        }
                    }

                    // 2. Buscar objeto n={"18":"...", "16":"...", "1e":"..."} (streams en versiones legacy)
                    val nPattern = Pattern.compile("var\\s+n\\s*=\\s*\\{([^}]+)\\}")
                    val nMatcher = nPattern.matcher(unpacked)
                    if (nMatcher.find()) {
                        val nBody = nMatcher.group(1) ?: ""
                        val keyPattern = Pattern.compile("\"(18|16|1e)\"\\s*:\\s*\"([^\"]+)\"")
                        val keyMatcher = keyPattern.matcher(nBody)
                        while (keyMatcher.find()) {
                            val rawUrl = keyMatcher.group(2)
                            if (rawUrl != null && (rawUrl.contains(".m3u8") || rawUrl.startsWith("/stream/"))) {
                                val resolved = resolveUrl(targetUrl, rawUrl)
                                if (!candidates.contains(resolved)) candidates.add(resolved)
                            }
                        }
                    }

                    // 3. Buscar cualquier patrón de stream en el script desempacado
                    val genPattern = Pattern.compile("[\"'](/[^\"']+\\.m3u8[^\"']*|https?://[^\"']+\\.m3u8[^\"']*)[\"']")
                    val genMatcher = genPattern.matcher(unpacked)
                    while (genMatcher.find()) {
                        val rawUrl = genMatcher.group(1)?.replace("\\", "") ?: continue
                        val resolved = resolveUrl(targetUrl, rawUrl)
                        if (!candidates.contains(resolved)) candidates.add(resolved)
                    }

                    // 4. Mirrors de CDN con playlist renombrado (master.txt) o path .urlset/ (hls3 de vidhide)
                    val renamedPattern = Pattern.compile("https?://[^\"'\\s]+?(?:\\.urlset/|master\\.txt)[^\"'\\s]*")
                    val renamedMatcher = renamedPattern.matcher(unpacked)
                    while (renamedMatcher.find()) {
                        val rawUrl = renamedMatcher.group(0)?.replace("\\", "") ?: continue
                        if (!candidates.contains(rawUrl)) candidates.add(rawUrl)
                    }
                } else {
                    Log.w(TAG, "No eval block matching Packer signature was found in the page.")
                }

                // Fallback: Buscar enlaces m3u8 directamente en el HTML
                val htmlStreamPattern = Pattern.compile("(https?://[^\"'\\s]+?\\.m3u8[^\"'\\s]*)")
                val htmlMatcher = htmlStreamPattern.matcher(html)
                while (htmlMatcher.find()) {
                    val rawUrl = htmlMatcher.group(1) ?: continue
                    if (!candidates.contains(rawUrl)) candidates.add(rawUrl)
                }

                Log.d(TAG, "Candidatas encontradas: ${candidates.size}")

                // Ordenar respetando la prioridad oficial de Vidhide:
                // 1) Si hubo orden explícito en `links` (hls4 primero, etc.), se respeta.
                // 2) Streams con /stream/ (hls4 en TikTok CDN, el más estable sin 502)
                // 3) Playlists .m3u8 estándar
                // 4) Playlists renombrados (.txt)
                val ordered = candidates.sortedWith { a, b ->
                    fun score(url: String): Int {
                        val prioIdx = prioritizedFromScript.indexOf(url)
                        if (prioIdx != -1) return prioIdx
                        return when {
                            url.contains("/stream/") -> 10
                            url.contains(".m3u8", ignoreCase = true) -> 20
                            url.contains(".txt", ignoreCase = true) -> 30
                            else -> 40
                        }
                    }
                    score(a).compareTo(score(b))
                }
                Log.d(TAG, "Orden de validación de streams: $ordered")

                // Guardar la lista ordenada: si el CDN elegido falla en reproducción
                // (5xx sostenidos), el player podrá conmutar a la siguiente alternativa.
                lastOrderedCandidates[targetUrl] = ordered

                // Pre-flight check: Probar cada candidata hasta encontrar una funcional (200 OK + #EXTM3U)
                for (candidate in ordered) {
                    if (validateM3u8(candidate, targetUrl)) {
                        Log.d(TAG, "Stream URL resolved & validated: $candidate")
                        return@withContext candidate
                    } else {
                        Log.w(TAG, "Candidata descartada (no respondió 200 OK / #EXTM3U): $candidate")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to extract static URLs", e)
        }
        return@withContext null
    }

    /**
     * Checks if a URL matches common ad / tracking / telemetry domains
     * to perform surgical ad-blocking.
     */
    fun isAdOrTracking(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("yandex.ru") ||
                lowerUrl.contains("googletagmanager") ||
                lowerUrl.contains("google-analytics") ||
                lowerUrl.contains("cloudflareinsights") ||
                lowerUrl.contains("beacon.min.js") ||
                lowerUrl.contains("static.js?type=mainstream") ||
                lowerUrl.contains("/ad?type=") ||
                lowerUrl.contains("directlink") ||
                lowerUrl.contains("pop3done") ||
                lowerUrl.contains("vastdone") ||
                lowerUrl.contains("clickunder") ||
                lowerUrl.contains("exoclick") ||
                lowerUrl.contains("popads") ||
                lowerUrl.contains("juicyads") ||
                lowerUrl.contains("a.pixibay.cc")
    }
}
