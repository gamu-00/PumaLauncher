package com.openlauncher.app.mediastation.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openlauncher.app.R
import com.openlauncher.app.util.AppLanguageManager
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
    private val appContext: Context,
    private val storage: MediaStationStorage,
    internal val client: OkHttpClient = OkHttpClient.Builder()
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
        private const val MAX_STREAM_SIZE_GB = 8.5
        private const val PREFERRED_STREAM_SIZE_GB = 4.5
        private val UNSUPPORTED_STREAM_KEYWORDS = listOf(
            "4k",
            "2160p",
            "av1",
            "remux",
            "bdmv",
            "10bit",
            "10-bit"
        )
        private val UNCACHED_DEBRID_REGEX =
            Regex("(?i)\\[(rd|ad|pm|dl)\\s+download\\]")
        private val CACHED_DEBRID_REGEX =
            Regex("(?i)\\[(rd|ad|pm|dl)\\+\\]")
        private val FILE_SIZE_REGEX =
            Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(tb|tib|gb|gib|mb|mib)\\b")
        private val UNICODE_FLAG_REGEX =
            Regex("(?:\\uD83C[\\uDDE6-\\uDDFF]){2}")

        private val LANG_FLAGS = mapOf(
            "it" to setOf("\uD83C\uDDEE\uD83C\uDDF9"),
            "de" to setOf("\uD83C\uDDE9\uD83C\uDDEA"),
            "fr" to setOf("\uD83C\uDDEB\uD83C\uDDF7"),
            "es" to setOf("\uD83C\uDDEA\uD83C\uDDF8", "\uD83C\uDDF2\uD83C\uDDFD"),
            "pt" to setOf("\uD83C\uDDF5\uD83C\uDDF9", "\uD83C\uDDE7\uD83C\uDDF7"),
            "en" to setOf("\uD83C\uDDEC\uD83C\uDDE7", "\uD83C\uDDFA\uD83C\uDDF8")
        )

        private val LANG_TAG_REGEX = mapOf(
            "it" to Regex("(?<![\\p{L}\\p{N}])(ita|italian|italiano)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "de" to Regex("(?<![\\p{L}\\p{N}])(ger|deu|german|deutsch)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "fr" to Regex("(?<![\\p{L}\\p{N}])(fre|fra|french|francais|français|vostfr|truefrench)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "es" to Regex("(?<![\\p{L}\\p{N}])(esp|spa|spanish|espanol|español|latino|castellano)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "pt" to Regex("(?<![\\p{L}\\p{N}])(por|portuguese|portugues|português|dublado)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE),
            "en" to Regex("(?<![\\p{L}\\p{N}])(eng|english)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        )

        private val MULTI_LANG_REGEX = Regex(
            "(?<![\\p{L}\\p{N}])(multi|dual(?:\\s+audio)?)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )

        private val SOBER_LANG_TAGS = mapOf(
            "it" to "ITA",
            "de" to "GER",
            "fr" to "FRA",
            "es" to "ESP",
            "pt" to "POR",
            "en" to "ENG"
        )
    }

    suspend fun fetchContinueWatching(authKey: String): List<MediaPosterItem> = withContext(Dispatchers.IO) {
        val cleanKey = authKey.trim()
        if (cleanKey.isEmpty()) return@withContext emptyList()

        val payload = gson.toJson(StremioDatastoreGetRequest(authKey = cleanKey))
        val responseBody = executePostJson(STREMIO_GET_URL, payload)
        val root = JsonParser.parseString(responseBody).asJsonObject

        if (root.has("error") && !root.get("error").isJsonNull) {
            val err = gson.fromJson(root.get("error"), StremioApiErrorDto::class.java)
            throw IOException(err?.message ?: AppLanguageManager.localizedString(appContext, R.string.media_error_stremio_api))
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
                throw moviesResult.exceptionOrNull()
                    ?: IOException(AppLanguageManager.localizedString(appContext, R.string.media_error_search_failed))
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
                ?: AppLanguageManager.localizedString(appContext, R.string.movies_episode_fallback, episode)

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
            throw IllegalStateException(
                AppLanguageManager.localizedString(appContext, R.string.media_error_addon_not_configured)
            )
        }

        val preferredLang = AppLanguageManager.getSavedLanguage(appContext)
        val streamUrl = "$base/stream/$type/$videoId.json"
        val body = executeGet(streamUrl)
        val parsed = gson.fromJson(body, StremioStreamResponse::class.java)
        val rawStreams = parsed?.streams.orEmpty()

        data class RankedStream(
            val tier: Int,
            val exactLangRank: Int,
            val codecRank: Int,
            val sizeRank: Int,
            val originalIndex: Int,
            val stream: PlayableStream
        )

        val candidates = mutableListOf<RankedStream>()

        // Filter unsupported heavy/10-bit streams and rank survivors by user language + cache + x264/size priority.
        rawStreams.forEachIndexed { index, dto ->
            val url = dto.url?.trim().orEmpty()
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                return@forEachIndexed
            }

            val rawName = dto.name.orEmpty().trim()
            val rawTitle = dto.title.orEmpty().trim()
            val rawDescription = dto.description.orEmpty().trim()
            val fullText = "$rawName $rawTitle $rawDescription"
            val metadataLower = fullText.lowercase(Locale.ROOT)

            // Discard 4K/2160p/AV1/REMUX/BDMV and 10-bit streams unsupported by the emulator / SM6225 decoder.
            if (UNSUPPORTED_STREAM_KEYWORDS.any { metadataLower.contains(it) }) {
                return@forEachIndexed
            }

            // Discard streams larger than 8.5 GB.
            val fileSizeGb = extractFileSizeGb(fullText)
            if (fileSizeGb != null && fileSizeGb > MAX_STREAM_SIZE_GB) {
                return@forEachIndexed
            }

            val headers = LinkedHashMap<String, String>()
            dto.behaviorHints?.proxyHeaders?.request?.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    headers[k] = v
                }
            }

            val detectedLangs = detectStreamLanguages(fullText)
            val isMulti = MULTI_LANG_REGEX.containsMatchIn(fullText)
            val isUncachedDownload =
                UNCACHED_DEBRID_REGEX.containsMatchIn(fullText) || fullText.contains("⏳")
            val isCachedInstant =
                !isUncachedDownload &&
                    (CACHED_DEBRID_REGEX.containsMatchIn(fullText) ||
                        fullText.contains("⚡") ||
                        !fullText.contains("download", ignoreCase = true))

            val englishFlags = LANG_FLAGS["en"].orEmpty()
            val hasOtherFlag = UNICODE_FLAG_REGEX.findAll(fullText).any { it.value !in englishFlags }
            val isPreferred = if (preferredLang == "en") {
                val otherLangs = detectedLangs - "en"
                ("en" in detectedLangs) || isMulti || (otherLangs.isEmpty() && !hasOtherFlag)
            } else {
                (preferredLang in detectedLangs) || isMulti
            }

            val exactLangRank = if (preferredLang == "en") {
                0
            } else if (preferredLang in detectedLangs) {
                0
            } else {
                1
            }

            val tier = when {
                isPreferred && isCachedInstant -> 1
                isPreferred && !isCachedInstant -> 2
                !isPreferred && isCachedInstant -> 3
                else -> 4
            }

            val soberTag = when {
                preferredLang in detectedLangs -> SOBER_LANG_TAGS[preferredLang]
                isMulti || detectedLangs.size > 1 -> "MULTI"
                detectedLangs.size == 1 -> SOBER_LANG_TAGS[detectedLangs.first()]
                else -> null
            }

            val cleanName = stripFlagsAndNormalize(rawName, " ")
            val detailSource = rawTitle.ifEmpty { rawDescription }
            val cleanTitle = stripFlagsAndNormalize(detailSource, " • ")

            val isX265OrHevc = metadataLower.contains("x265") ||
                metadataLower.contains("hevc") ||
                metadataLower.contains("h265") ||
                metadataLower.contains("h.265")
            val isX264OrH264 = metadataLower.contains("x264") ||
                metadataLower.contains("h264") ||
                metadataLower.contains("h.264") ||
                metadataLower.contains("avc")
            val codecRank = when {
                isX264OrH264 && !isX265OrHevc -> 0
                isX265OrHevc -> 2
                else -> 1
            }
            val sizeRank = if (fileSizeGb == null || fileSizeGb <= PREFERRED_STREAM_SIZE_GB) 0 else 1

            val tierBaseScore = (5 - tier) * 10_000
            val playable = PlayableStream(
                url = url,
                name = cleanName,
                title = cleanTitle,
                headers = headers,
                score = tierBaseScore - (exactLangRank * 3_000) - (codecRank * 2_000) - (sizeRank * 1_000) - index,
                languageTag = soberTag
            )

            candidates.add(
                RankedStream(
                    tier = tier,
                    exactLangRank = exactLangRank,
                    codecRank = codecRank,
                    sizeRank = sizeRank,
                    originalIndex = index,
                    stream = playable
                )
            )
        }

        val sortedStreams = candidates
            .sortedWith(
                compareBy<RankedStream> { it.tier }
                    .thenBy { it.exactLangRank }
                    .thenBy { it.codecRank }
                    .thenBy { it.sizeRank }
                    .thenBy { it.originalIndex }
            )
            .map { it.stream }

        Log.d(
            "MediaStation",
            "resolveStreams($type/$videoId, lang=$preferredLang): ricevuti ${rawStreams.size} stream, filtrati ${sortedStreams.size}"
        )
        sortedStreams.take(5).forEachIndexed { idx, stream ->
            Log.d("MediaStation", "Top #${idx + 1} [${stream.languageTag ?: "NONE"}]: ${stream.name} | ${stream.title} | ${stream.url}")
        }

        sortedStreams
    }

    private fun detectStreamLanguages(text: String): Set<String> = buildSet {
        for ((lang, flags) in LANG_FLAGS) {
            if (flags.any { text.contains(it) }) add(lang)
        }
        for ((lang, regex) in LANG_TAG_REGEX) {
            if (regex.containsMatchIn(text)) add(lang)
        }
    }

    private fun stripFlagsAndNormalize(raw: String, lineJoiner: String): String {
        return raw.lines()
            .map { line ->
                UNICODE_FLAG_REGEX.replace(line, "")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
                    .trim('|', '•', '/')
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString(lineJoiner)
    }

    private fun extractFileSizeGb(text: String): Double? {
        return FILE_SIZE_REGEX.findAll(text)
            .mapNotNull { match ->
                val rawValue = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    ?: return@mapNotNull null
                when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "tb", "tib" -> rawValue * 1024.0
                    "gb", "gib" -> rawValue
                    "mb", "mib" -> rawValue / 1024.0
                    else -> null
                }
            }
            .maxOrNull()
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
            return response.body?.string()
                ?: throw IOException(AppLanguageManager.localizedString(appContext, R.string.media_error_empty_response))
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
            return response.body?.string()
                ?: throw IOException(AppLanguageManager.localizedString(appContext, R.string.media_error_empty_response))
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
