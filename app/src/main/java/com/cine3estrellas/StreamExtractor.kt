package com.cine3estrellas

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object StreamExtractor {
    private const val TAG = "StreamExtractor"
    
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Decodes a Dean Edwards packed Javascript block.
     * Extracts tokens matching words, converting base-a string representations
     * to their actual index in vocabulary dictionary list [k].
     */
    fun unpack(p: String, a: Int, c: Int, k: List<String>): String {
        val wordRegex = Regex("\\b\\w+\\b")
        return wordRegex.replace(p) { matchResult ->
            val word = matchResult.value
            try {
                // Convert base-a word to integer
                val index = word.toInt(radix = a)
                if (index in k.indices && k[index].isNotEmpty()) {
                    k[index]
                } else {
                    word
                }
            } catch (e: NumberFormatException) {
                word
            }
        }
    }

    /**
     * Attempts to fetch and extract the direct stream URL statically.
     * Works on any thread (suspends to Dispatchers.IO).
     */
    suspend fun extractWithStaticParser(embedUrl: String): String? = withContext(Dispatchers.IO) {
        // Enforce https if needed
        val targetUrl = if (embedUrl.startsWith("http://")) {
            embedUrl.replace("http://", "https://")
        } else if (!embedUrl.startsWith("http")) {
            "https://$embedUrl"
        } else {
            embedUrl
        }

        // Derive the referer from the embed URL's origin so each domain gets its own correct header
        val referer = try {
            val uri = java.net.URI(targetUrl)
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

                // Matches eval(function(p,a,c,k,e,d){... return p}('p_packed', a_base, c_count, 'k_list'.split('|')))
                val evalPattern = Pattern.compile(
                    "eval\\s*\\(\\s*function\\s*\\(\\s*p\\s*,\\s*a\\s*,\\s*c\\s*,\\s*k\\s*,\\s*e\\s*,\\s*d\\s*\\).*?\\}\\s*\\(\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'\\.split\\(\\s*['\"]\\|['\"]\\s*\\)",
                    Pattern.DOTALL
                )

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

                    // Match HLS multi-stream format starting with http/https containing .m3u8
                    val streamPattern = Pattern.compile("(https?://[^\"'\\s]+?\\.m3u8[^\"'\\s]*)")
                    val streamMatcher = streamPattern.matcher(unpacked)

                    val urlsFound = ArrayList<String>()
                    while (streamMatcher.find()) {
                        val foundUrl = streamMatcher.group(1)?.replace("\\", "") ?: continue
                        urlsFound.add(foundUrl)
                    }

                    if (urlsFound.isNotEmpty()) {
                        // Prefer URLs containing quality setups (urlset) or tokens (t=)
                        val bestUrl = urlsFound.firstOrNull { it.contains("token=") || it.contains("?t=") }
                            ?: urlsFound.firstOrNull { it.contains("/urlset/") }
                            ?: urlsFound.first()

                        Log.d(TAG, "Stream URL resolved: $bestUrl")
                        return@withContext bestUrl
                    }
                } else {
                    Log.w(TAG, "No eval block matching Packer signature was found in the page.")
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
