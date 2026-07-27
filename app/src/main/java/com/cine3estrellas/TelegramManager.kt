package com.cine3estrellas

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object TelegramManager {
    private const val PROXY_BASE_URL = "${WebConfig.BASE_URL}/api/telegram"

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
            val response: TelegramResponse<ChatMember> = httpClient.get("$PROXY_BASE_URL/membership") {
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
            val response: TelegramResponse<TelegramUser> = httpClient.get("$PROXY_BASE_URL/user-info") {
                parameter("telegram_id", telegramId)
            }.body()

            if (response.ok && response.result != null) {
                val tUser = response.result
                User(
                    telegramId = telegramId.toLongOrNull() ?: 0L,
                    firstName = tUser.first_name,
                    lastName = tUser.last_name,
                    username = tUser.username,
                    photoUrl = null
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
