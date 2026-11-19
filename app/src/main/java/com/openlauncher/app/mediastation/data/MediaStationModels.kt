package com.openlauncher.app.mediastation.data

import com.google.gson.annotations.SerializedName

/**
 * Unified UI model for movies, series, and "Continue Watching" items.
 */
data class MediaPosterItem(
    val id: String,
    val type: String, // "movie" or "series"
    val title: String,
    val posterUrl: String? = null,
    val year: String? = null,
    val description: String? = null,
    val videoId: String = id,
    val season: Int? = null,
    val episode: Int? = null,
    val timeOffsetMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatched: String? = null
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
data class EpisodeItem(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val overview: String? = null,
    val released: String? = null
)

/**
 * Resolved, filtered, and ranked stream ready for Media3 ExoPlayer.
 */
data class PlayableStream(
    val url: String,
    val name: String,
    val title: String,
    val headers: Map<String, String>,
    val score: Int,
    val languageTag: String? = null
) {
    val displayName: String
        get() = listOf(name, title)
            .map { it.replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" • ")
            .ifEmpty { "HTTP Stream" }
}

// ── Cinemeta DTOs ─────────────────────────────────────────────────────────────

data class CinemetaCatalogResponse(
    val metas: List<CinemetaMetaDto>? = null
)

data class CinemetaMetaDto(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val description: String? = null
)

data class CinemetaMetaResponse(
    val meta: CinemetaSeriesMetaDto? = null
)

data class CinemetaSeriesMetaDto(
    val id: String? = null,
    val name: String? = null,
    val poster: String? = null,
    val videos: List<CinemetaVideoDto>? = null
)

data class CinemetaVideoDto(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val number: Int? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val released: String? = null
)

// ── Stremio Datastore API DTOs ────────────────────────────────────────────────

data class StremioDatastoreGetRequest(
    val authKey: String,
    val collection: String = "libraryItem",
    val all: Boolean = true
)

data class StremioApiErrorDto(
    val message: String? = null,
    val code: Int? = null
)

data class StremioLibraryItemDto(
    @SerializedName("_id") val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val poster: String? = null,
    val year: String? = null,
    val removed: Boolean? = null,
    val temp: Boolean? = null,
    @SerializedName("_ctime") val ctime: String? = null,
    @SerializedName("_mtime") val mtime: String? = null,
    val state: StremioLibraryStateDto? = null
)

data class StremioLibraryStateDto(
    val lastWatched: String? = null,
    val timeWatched: Long? = null,
    val timeOffset: Long? = null,
    val overallTimeWatched: Long? = null,
    val timesWatched: Int? = null,
    val flaggedWatched: Int? = null,
    val duration: Long? = null,
    @SerializedName("video_id") val videoId: String? = null,
    val watched: String? = null
)

// ── Stremio Addon Stream Protocol DTOs ────────────────────────────────────────

data class StremioStreamResponse(
    val streams: List<StremioStreamDto>? = null
)

data class StremioStreamDto(
    val url: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val behaviorHints: StremioBehaviorHintsDto? = null
)

data class StremioBehaviorHintsDto(
    val proxyHeaders: StremioProxyHeadersDto? = null,
    val notWebReady: Boolean? = null
)

data class StremioProxyHeadersDto(
    val request: Map<String, String>? = null
)
