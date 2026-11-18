package com.openlauncher.app.mediastation.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MediaStationRepository(
    private val storage: MediaStationStorage,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    private val rawLibraryItems = ConcurrentHashMap<String, JsonObject>()

    companion object {
        private const val STREMIO_GET_URL = "https://api.strem.io/api/datastoreGet"
        private const val STREMIO_PUT_URL = "https://api.strem.io/api/datastorePut"
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EXPLICIT_ITA_REGEX = Regex("(?i)\\b(ita|italian|italiano)\\b")
        private val MULTI_DUAL_REGEX = Regex("(?i)\\b(multi|dual)\\b")
        private val EXCLUDED_LANG_REGEX =
            Regex("(?i)\\b(rus|russian|mvo|ukr|hin|hindi|tamil|kor|chi|jpn)\\b")
        private val UNSUPPORTED_STREAM_REGEX =
            Regex("(?i)\\b(4k|2160p|av1|dts|truehd|remux)\\b")
        private val UNCACHED_DEBRID_REGEX =
            Regex("(?i)\\[(rd|ad|pm|dl)\\s+download\\]")
    }

    suspend fun fetchContinueWatching(authKey: String): List<MediaPosterItem> = withContext(Dispatchers.IO) {
        val cleanKey = authKey.trim()
        if (cleanKey.isEmpty()) return@withContext emptyList()

        val payload = gson.toJson(StremioDatastoreGetRequest(authKey = cleanKey))
        val responseBody = executePostJson(STREMIO_GET_URL, payload)
        val root = JsonParser.parseString(responseBody).asJsonObject

        if (root.has("error") && !root.get("error").isJsonNull) {
            val err = gson.fromJson(root.get("error"), StremioApiErrorDto::class.java)
            throw IOException(err?.message ?: "Errore Stremio API")
        }

        val resultArray = root.getAsJsonArray("result") ?: JsonArray()
        val parsed = mutableListOf<MediaPosterItem>()

        for (element in resultArray) {
            if (!element.isJsonObject) continue
            val rawObj = element.asJsonObject
            val dto = gson.fromJson(rawObj, StremioLibraryItemDto::class.java)
            val itemId = dto?.id?.takeIf { it.isNotBlank() }
                ?: rawObj.get("_id")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (itemId.startsWith("tt")) {
                rawLibraryItems[itemId] = rawObj.deepCopy()
            } else {
                continue
            }

            // Do NOT discard items with "temp": true or "removed": true — Stremio sets both to true
            // on titles started without adding them to the library first.
            val stateObj = rawObj.getAsJsonObject("state")
            val state = dto?.state
            if (state == null && stateObj == null) continue

            val timeOffset = state?.timeOffset
                ?: stateObj?.get("timeOffset")?.takeIf { !it.isJsonNull }?.asLong
                ?: 0L
            val duration = state?.duration
                ?: stateObj?.get("duration")?.takeIf { !it.isJsonNull }?.asLong
                ?: 0L
            val flaggedWatched = state?.flaggedWatched
                ?: stateObj?.get("flaggedWatched")?.takeIf { !it.isJsonNull }?.asInt
                ?: 0
            val rawVideoId = state?.videoId?.takeIf { it.isNotBlank() }
                ?: stateObj?.get("video_id")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                ?: itemId

            val stateSeason = stateObj?.get("season")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val stateEpisode = stateObj?.get("episode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val (parsedSeason, parsedEpisode) = parseSeasonEpisode(rawVideoId)
            val season = if (stateSeason > 0) stateSeason else parsedSeason
            val episode = if (stateEpisode > 0) stateEpisode else parsedEpisode

            val resolvedType = dto?.type?.takeIf { it == "movie" || it == "series" }
                ?: rawObj.get("type")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it == "movie" || it == "series" }
                ?: if (season != null && episode != null) "series" else "movie"

            val isInProgressByTime = timeOffset > 30_000L &&
                flaggedWatched != 1 &&
                (duration <= 0L || (timeOffset.toDouble() / duration.toDouble()) < 0.92)
            val hasActiveSeriesEpisode = resolvedType == "series" &&
                rawVideoId.contains(":") &&
                season != null && season > 0 &&
                episode != null && episode > 0 &&
                flaggedWatched != 1

            if (!isInProgressByTime && !hasActiveSeriesEpisode) continue

            val itemName = dto?.name?.takeIf { it.isNotBlank() }
                ?: rawObj.get("name")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                ?: itemId
            val itemPoster = dto?.poster?.takeIf { it.isNotBlank() }
                ?: rawObj.get("poster")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            val lastWatched = state?.lastWatched
                ?: stateObj?.get("lastWatched")?.takeIf { !it.isJsonNull }?.asString

            parsed.add(
                MediaPosterItem(
                    id = itemId,
                    type = resolvedType,
                    title = itemName,
                    posterUrl = itemPoster,
                    year = dto?.year,
                    videoId = rawVideoId,
                    season = season,
                    episode = episode,
                    timeOffsetMs = timeOffset,
                    durationMs = duration,
                    lastWatched = lastWatched
                )
            )
        }

        val sorted = parsed.sortedByDescending { parseLastWatchedInstant(it.lastWatched) }
        storage.saveContinueWatchingCache(sorted)
        sorted
    }

    suspend fun syncWatchProgress(
        authKey: String,
        item: MediaPosterItem,
        videoId: String,
        timeOffsetMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        val cleanKey = authKey.trim()
        if (cleanKey.isEmpty() || timeOffsetMs <= 0L) return@withContext

        val nowIso = currentIsoUtc()
        val existingObj = rawLibraryItems[item.id]?.deepCopy() ?: JsonObject().apply {
            addProperty("_id", item.id)
            addProperty("name", item.title)
            addProperty("type", item.type)
            if (!item.posterUrl.isNullOrBlank()) addProperty("poster", item.posterUrl)
            addProperty("removed", false)
            addProperty("temp", false)
            addProperty("_ctime", nowIso)
        }

        existingObj.addProperty("_mtime", nowIso)
        val stateObj = existingObj.getAsJsonObject("state")?.deepCopy() ?: JsonObject()
        stateObj.addProperty("timeOffset", timeOffsetMs)
        if (durationMs > 0L) {
            stateObj.addProperty("duration", durationMs)
        }
        stateObj.addProperty("video_id", videoId)
        stateObj.addProperty("lastWatched", nowIso)
        if (!stateObj.has("flaggedWatched")) stateObj.addProperty("flaggedWatched", 0)
        if (!stateObj.has("timeWatched")) stateObj.addProperty("timeWatched", timeOffsetMs)
        if (!stateObj.has("overallTimeWatched")) stateObj.addProperty("overallTimeWatched", timeOffsetMs)
        existingObj.add("state", stateObj)

        val putPayload = JsonObject().apply {
            addProperty("authKey", cleanKey)
            addProperty("collection", "libraryItem")
            add("changes", JsonArray().apply { add(existingObj) })
        }

        executePostJson(STREMIO_PUT_URL, gson.toJson(putPayload))
        rawLibraryItems[item.id] = existingObj

        // Keep local "Continue Watching" cache aligned immediately.
        val currentCache = storage.continueWatchingCacheFlow.first().toMutableList()
        currentCache.removeAll { it.id == item.id }
        val completed = durationMs > 0L && (timeOffsetMs.toDouble() / durationMs.toDouble()) >= 0.92
        if (!completed && timeOffsetMs > 30_000L) {
            val (season, episode) = parseSeasonEpisode(videoId)
            currentCache.add(
                0,
                item.copy(
                    videoId = videoId,
                    season = season ?: item.season,
                    episode = episode ?: item.episode,
                    timeOffsetMs = timeOffsetMs,
                    durationMs = if (durationMs > 0L) durationMs else item.durationMs,
                    lastWatched = nowIso
                )
            )
        }
        storage.saveContinueWatchingCache(currentCache)
    }

    suspend fun fetchTopCatalog(type: String): List<MediaPosterItem> = withContext(Dispatchers.IO) {
        val url = "$CINEMETA_BASE/catalog/$type/top.json"
        fetchCatalogUrl(url, type)
    }

    suspend fun searchCatalog(query: String): List<MediaPosterItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")

        coroutineScope {
            val moviesDeferred = async {
                runCatching {
                    fetchCatalogUrl("$CINEMETA_BASE/catalog/movie/top/search=$encoded.json", "movie")
                }
            }
            val seriesDeferred = async {
                runCatching {
                    fetchCatalogUrl("$CINEMETA_BASE/catalog/series/top/search=$encoded.json", "series")
                }
            }

            val moviesResult = moviesDeferred.await()
            val seriesResult = seriesDeferred.await()

            if (moviesResult.isFailure && seriesResult.isFailure) {
                throw moviesResult.exceptionOrNull() ?: IOException("Ricerca fallita")
            }

            val movies = moviesResult.getOrDefault(emptyList())
            val series = seriesResult.getOrDefault(emptyList())
            val combined = mutableListOf<MediaPosterItem>()
            val seenIds = HashSet<String>()

            // Interleave movies and series so both types stay visible on compact screens.
            val maxSize = maxOf(movies.size, series.size)
            for (i in 0 until maxSize) {
                if (i < movies.size && seenIds.add(movies[i].id)) combined.add(movies[i])
                if (i < series.size && seenIds.add(series[i].id)) combined.add(series[i])
            }
            combined
        }
    }

    suspend fun fetchSeriesEpisodes(imdbId: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val url = "$CINEMETA_BASE/meta/series/$imdbId.json"
        val body = executeGet(url)
        val parsed = gson.fromJson(body, CinemetaMetaResponse::class.java)
        val videos = parsed?.meta?.videos.orEmpty()

        videos.mapNotNull { video ->
            val season = video.season ?: 0
            val episode = video.episode ?: video.number ?: 0
            if (season <= 0 || episode <= 0) return@mapNotNull null

            val videoId = video.id?.takeIf { it.isNotBlank() } ?: "$imdbId:$season:$episode"
            val title = video.name?.takeIf { it.isNotBlank() }
                ?: video.title?.takeIf { it.isNotBlank() }
                ?: "Episodio $episode"

            EpisodeItem(
                videoId = videoId,
                season = season,
                episode = episode,
                title = title,
                thumbnailUrl = video.thumbnail,
                overview = video.overview,
                released = video.released
            )
        }.sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
    }

    suspend fun resolveStreams(
        addonBaseUrl: String,
        type: String,
        videoId: String
    ): List<PlayableStream> = withContext(Dispatchers.IO) {
        val base = normalizeAddonBaseUrl(addonBaseUrl)
        if (base.isBlank()) {
            throw IllegalStateException("URL Addon Resolver non configurato")
        }

        val streamUrl = "$base/stream/$type/$videoId.json"
        val body = executeGet(streamUrl)
        val parsed = gson.fromJson(body, StremioStreamResponse::class.java)
        val rawStreams = parsed?.streams.orEmpty()

        rawStreams.mapNotNull { dto ->
            val url = dto.url?.trim().orEmpty()
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                return@mapNotNull null
            }

            val name = dto.name.orEmpty().trim()
            val title = dto.title.orEmpty().trim()
            val description = dto.description.orEmpty().trim()
            val rawText = "$name $title $description"
            val metadataLower = rawText.lowercase(Locale.ROOT)

            // Discard streams that are not already cached on Debrid.
            if (UNCACHED_DEBRID_REGEX.containsMatchIn(rawText) || rawText.contains("⏳")) {
                return@mapNotNull null
            }

            // SM6225 hardware decoder cannot reliably handle 4K/2160p, AV1, DTS, TrueHD or REMUX.
            if (metadataLower.contains("4k") ||
                metadataLower.contains("2160p") ||
                UNSUPPORTED_STREAM_REGEX.containsMatchIn(metadataLower)
            ) {
                return@mapNotNull null
            }

            // Strictly require an explicit Italian marker (\bita\b, italian, italiano, 🇮🇹);
            // completely exclude any non-Italian stream so it is never auto-played.
            val hasExplicitIta = EXPLICIT_ITA_REGEX.containsMatchIn(rawText) || rawText.contains("🇮🇹")
            if (!hasExplicitIta ||
                EXCLUDED_LANG_REGEX.containsMatchIn(rawText) && !hasExplicitIta
            ) {
                return@mapNotNull null
            }

            val headers = LinkedHashMap<String, String>()
            dto.behaviorHints?.proxyHeaders?.request?.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    headers[k] = v
                }
            }
            if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                headers["User-Agent"] = DEFAULT_MOBILE_UA
            }

            val score = computeStreamScore(
                url = url,
                rawText = rawText,
                lowerText = metadataLower
            )

            PlayableStream(
                url = url,
                name = name,
                title = title.ifEmpty { description },
                headers = headers,
                score = score
            )
        }.sortedByDescending { it.score }
    }

    private fun computeStreamScore(
        url: String,
        rawText: String,
        lowerText: String
    ): Int {
        var score = 300

        if (lowerText.contains("[rd+]") || lowerText.contains("rd+") || rawText.contains("⚡")) {
            score += 100
        }

        when {
            lowerText.contains("1080p") -> score += 50
            lowerText.contains("720p") -> score += 30
            lowerText.contains("480p") -> score += 10
        }
        val lowerUrl = url.lowercase(Locale.ROOT)
        when {
            lowerUrl.contains(".m3u8") || lowerText.contains(".m3u8") -> score += 20
            lowerUrl.contains(".mp4") || lowerText.contains(".mp4") -> score += 15
        }
        return score
    }

    private fun fetchCatalogUrl(url: String, defaultType: String): List<MediaPosterItem> {
        val body = executeGet(url)
        val parsed = gson.fromJson(body, CinemetaCatalogResponse::class.java)
        return parsed?.metas.orEmpty().mapNotNull { meta ->
            val id = meta.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = meta.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MediaPosterItem(
                id = id,
                type = meta.type?.takeIf { it == "movie" || it == "series" } ?: defaultType,
                title = name,
                posterUrl = meta.poster,
                year = meta.year ?: meta.releaseInfo,
                description = meta.description
            )
        }
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DEFAULT_MOBILE_UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: throw IOException("Risposta vuota")
        }
    }

    private fun executePostJson(url: String, jsonBody: String): String {
        val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", DEFAULT_MOBILE_UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: throw IOException("Risposta vuota")
        }
    }

    private fun parseSeasonEpisode(videoId: String): Pair<Int?, Int?> {
        val parts = videoId.split(":")
        if (parts.size >= 3) {
            val season = parts[parts.size - 2].toIntOrNull()
            val episode = parts[parts.size - 1].toIntOrNull()
            if (season != null && episode != null) {
                return season to episode
            }
        }
        return null to null
    }

    private fun parseLastWatchedInstant(raw: String?): Instant {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Instant.EPOCH
        return try {
            Instant.parse(text)
        } catch (_: Exception) {
            try {
                val normalized = text.replace(Regex("(\\.\\d{1,9})\\d+(Z|[+-].*)$"), "$1$2")
                Instant.parse(normalized)
            } catch (_: Exception) {
                Instant.EPOCH
            }
        }
    }

    private fun currentIsoUtc(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
