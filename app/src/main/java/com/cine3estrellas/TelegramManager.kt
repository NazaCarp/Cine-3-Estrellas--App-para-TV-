package com.cine3estrellas

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object TelegramManager {
    // TODO: Provide the actual bot token here
    private const val BOT_TOKEN = "7393250047:AAEW7hLKi3cuaBvBn4Y8V8cubti_cympo7Q"
    private const val BASE_URL = "https://api.telegram.org/bot$BOT_TOKEN"
    private const val GROUP_ID = "@Cine_3Estrellas"

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun checkGroupMembership(telegramId: String): Boolean {
        return try {
            val response: TelegramResponse<ChatMember> = httpClient.get("$BASE_URL/getChatMember") {
                parameter("chat_id", GROUP_ID)
                parameter("user_id", telegramId)
            }.body()

            if (response.ok) {
                val status = response.result?.status
                status == "member" || status == "administrator" || status == "creator"
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getUserInfo(telegramId: String): User? {
        return try {
            val response: TelegramResponse<TelegramUser> = httpClient.get("$BASE_URL/getChat") {
                parameter("chat_id", telegramId)
            }.body()

            if (response.ok && response.result != null) {
                val tUser = response.result
                User(
                    telegramId = telegramId.toLongOrNull() ?: 0L,
                    firstName = tUser.first_name,
                    lastName = tUser.last_name,
                    username = tUser.username,
                    photoUrl = null // Simplified for now
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null
)

@Serializable
data class ChatMember(
    val status: String
)

@Serializable
data class TelegramUser(
    val id: Long,
    val first_name: String? = null,
    val last_name: String? = null,
    val username: String? = null
)
