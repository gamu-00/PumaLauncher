package com.openlauncher.app.mediastation.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Unified UI model for movies, series, and "Continue Watching" items.
 */
@Keep
data class MediaPosterItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // "movie" or "series"
    @SerializedName("title") val title: String,
    @SerializedName("posterUrl") val posterUrl: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("videoId") val videoId: String = id,
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("timeOffsetMs") val timeOffsetMs: Long = 0L,
    @SerializedName("durationMs") val durationMs: Long = 0L,
    @SerializedName("lastWatched") val lastWatched: String? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (timeOffsetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val episodeBadge: String?
        get() = if (season != null && episode != null) "S${season}:E${episode}" else null
}

/**
 * Single episode entry for a TV series.
 */
@Keep
data class EpisodeItem(
    @SerializedName("videoId") val videoId: String,
    @SerializedName("season") val season: Int,
    @SerializedName("episode") val episode: Int,
    @SerializedName("title") val title: String,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("released") val released: String? = null
)

/**
 * Resolved, filtered, and ranked stream ready for Media3 ExoPlayer.
 */
@Keep
data class PlayableStream(
    @SerializedName("url") val url: String,
    @SerializedName("name") val name: String,
    @SerializedName("title") val title: String,
    @SerializedName("headers") val headers: Map<String, String>,
    @SerializedName("score") val score: Int,
    @SerializedName("languageTag") val languageTag: String? = null,
    @SerializedName("qualityBadge") val qualityBadge: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null
) {
    val displayName: String
        get() = listOf(name, title)
            .map { it.replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" • ")
            .ifEmpty { "HTTP Stream" }
}

// ── Cinemeta DTOs ─────────────────────────────────────────────────────────────

@Keep
data class CinemetaCatalogResponse(
    @SerializedName("metas") val metas: List<CinemetaMetaDto>? = null
)

@Keep
data class CinemetaMetaDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("description") val description: String? = null
)

@Keep
data class CinemetaMetaResponse(
    @SerializedName("meta") val meta: CinemetaSeriesMetaDto? = null
)

@Keep
data class CinemetaSeriesMetaDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("videos") val videos: List<CinemetaVideoDto>? = null
)

@Keep
data class CinemetaVideoDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("released") val released: String? = null
)

// ── Stremio Datastore API DTOs ────────────────────────────────────────────────

@Keep
data class StremioDatastoreGetRequest(
    @SerializedName("authKey") val authKey: String,
    @SerializedName("collection") val collection: String = "libraryItem",
    @SerializedName("all") val all: Boolean = true
)

@Keep
data class StremioApiErrorDto(
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: Int? = null
)

@Keep
data class StremioLibraryItemDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("removed") val removed: Boolean? = null,
    @SerializedName("temp") val temp: Boolean? = null,
    @SerializedName("_ctime") val ctime: String? = null,
    @SerializedName("_mtime") val mtime: String? = null,
    @SerializedName("state") val state: StremioLibraryStateDto? = null
)

@Keep
data class StremioLibraryStateDto(
    @SerializedName("lastWatched") val lastWatched: String? = null,
    @SerializedName("timeWatched") val timeWatched: Long? = null,
    @SerializedName("timeOffset") val timeOffset: Long? = null,
    @SerializedName("overallTimeWatched") val overallTimeWatched: Long? = null,
    @SerializedName("timesWatched") val timesWatched: Int? = null,
    @SerializedName("flaggedWatched") val flaggedWatched: Int? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("video_id") val videoId: String? = null,
    @SerializedName("watched") val watched: String? = null
)

// ── Stremio Addon Stream Protocol DTOs ────────────────────────────────────────

@Keep
data class StremioStreamResponse(
    @SerializedName("streams") val streams: List<StremioStreamDto>? = null
)

@Keep
data class StremioStreamDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("behaviorHints") val behaviorHints: StremioBehaviorHintsDto? = null
)

@Keep
data class StremioBehaviorHintsDto(
    @SerializedName("proxyHeaders") val proxyHeaders: StremioProxyHeadersDto? = null,
    @SerializedName("notWebReady") val notWebReady: Boolean? = null
)

@Keep
data class StremioProxyHeadersDto(
    @SerializedName("request") val request: Map<String, String>? = null
)
