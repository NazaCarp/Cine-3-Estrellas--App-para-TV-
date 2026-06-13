package com.cine3estrellas

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object FavoriteItemSerializer : KSerializer<FavoriteItem> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FavoriteItem") {
        element<Int>("id")
        element<String?>("title", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): FavoriteItem {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Only JSON supported")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                FavoriteItem(id = element.int, title = null)
            }
            is JsonObject -> {
                val id = element["id"]?.jsonPrimitive?.intOrNull ?: throw SerializationException("FavoriteItem must have a valid numeric id")
                val title = element["title"]?.jsonPrimitive?.contentOrNull
                FavoriteItem(id = id, title = title)
            }
            else -> throw SerializationException("Unknown element type for FavoriteItem")
        }
    }

    override fun serialize(encoder: Encoder, value: FavoriteItem) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Only JSON supported")
        val json = buildJsonObject {
            put("id", value.id)
            if (value.title != null) {
                put("title", value.title)
            } else {
                put("title", JsonNull)
            }
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

@Serializable(with = FavoriteItemSerializer::class)
data class FavoriteItem(
    val id: Int,
    val title: String? = null
)

@Serializable
data class User(
    @SerialName("id") val telegramId: Long,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("allows_write_to_pm") val allowsWriteToPm: Boolean = false,
    @SerialName("platform") val platform: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("province") val province: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("browser") val browser: String? = null,
    @SerialName("screen_size") val screenSize: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("total_visits") val totalVisits: Int = 0,
    @SerialName("favorites") val favorites: List<FavoriteItem> = emptyList(),
    @SerialName("watch_history") val watchHistory: List<Int> = emptyList(),
    @SerialName("hidden_history") val hiddenHistory: List<Int> = emptyList(),
    @SerialName("welcome_sent") val welcomeSent: Boolean = false,
    @SerialName("watch_progress") val watchProgress: Map<String, WatchProgressVal> = emptyMap(),
    @SerialName("auth_code") val authCode: String? = null,
    @SerialName("auth_code_expires_at") val authCodeExpiresAt: String? = null,
    @Transient val photoUrl: String? = null
)

@Serializable
data class WatchProgressVal(
    val time: Double,
    val updatedAt: String
)

@Serializable
data class DbEvent(
    val user_id: Long,
    val first_name: String?,
    val event_name: String,
    val movie_id: Long?,
    val movie_title: String?,
    val language: String? = null
)

@Serializable
data class IncrementDailyStatsParams(
    val is_new: Boolean,
    val is_unique: Boolean
)

@Serializable
data class Movie(
    val id: Int,
    val title: String,
    val versions: Map<String, MovieVersion>? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val genre_ids: List<Int>? = null,
    val vote_average: Double? = null,
    val popularity: Double? = null,
    val release_date: String? = null,
    val certification: String? = null,
    val overview: String? = null, // From TMDB
    val runtime: Int? = null,    // From TMDB
    val genres: List<Genre>? = null, // From TMDB
    val original_language: String? = null, // From TMDB
    val created_at: String? = null,
    val keywords: List<String>? = null
)

@Serializable
data class MovieVersion(
    val url: String,
    val quality: String,
    val downloadUrl: String? = null
)

@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class HomeCategory(
    val id: String,
    val name: String,
    val icon: String,
    val order: Int,
    val show_in_home: Boolean,
    val genre_ids: List<Int>? = null,
    val keywords: List<String>? = null,
    val min_rating: Double? = 0.0,
    val sort_by: String? = "created_at"
)

data class MovieDetails(
    val movie: Movie,
    val similarMovies: List<Movie> = emptyList()
)

@Serializable
data class TvHomeResponse(
    val heroMovies: List<Movie>,
    val categories: List<TvHomeCategory>
)

@Serializable
data class TvHomeCategory(
    val id: String,
    val name: String,
    val icon: String = "",
    val order: Int = 0,
    val show_in_home: Boolean = true,
    val genre_ids: List<Int>? = null,
    val keywords: List<String>? = null,
    val min_rating: Double? = 0.0,
    val sort_by: String? = "created_at",
    val total_count: Long? = null,  // Real total from Supabase, used for VER MÁS counter
    val movies: List<Movie>
)
