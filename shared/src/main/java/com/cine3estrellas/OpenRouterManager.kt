package com.cine3estrellas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Log

object OpenRouterManager {
    // RECUERDA: Obtén tu API Key en https://openrouter.ai/keys
    private const val API_KEY = "sk-or-v1-..." 
    private const val BASE_URL = "https://openrouter.ai/api/v1"

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    /**
     * Envía una consulta al modelo ox-alpha de OpenRouter.
     * @param prompt El texto que quieres enviar al modelo.
     * @return La respuesta del modelo o null si hay un error.
     */
    suspend fun chat(prompt: String): String? {
        return try {
            val request = OpenRouterRequest(
                model = "stealth/ox-alpha",
                messages = listOf(
                    OpenRouterMessage(role = "user", content = prompt)
                )
            )

            val response: OpenRouterResponse = httpClient.post("$BASE_URL/chat/completions") {
                header("Authorization", "Bearer $API_KEY")
                // OpenRouter recomienda incluir estos headers para identificar tu app
                header("HTTP-Referer", "https://cine3estrellas.com")
                header("X-Title", "Cine 3 Estrellas")
                
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            response.choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            Log.e("OpenRouterManager", "Error en la petición: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
