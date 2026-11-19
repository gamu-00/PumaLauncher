package com.openlauncher.app.mediastation

import android.util.Log
import com.openlauncher.app.mediastation.data.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Request as OkHttpRequest

/**
 * Ad-free direct YouTube stream extractor capped at 720p using NewPipeExtractor.
 * Filters exclusively muxed audio+video streams (<= 720p), prioritizing 720p (MP4), then 480p, then 360p.
 */
class YouTubeStreamExtractor(
    okHttpClient: OkHttpClient
) {

    init {
        ensureInitialized(okHttpClient)
    }

    suspend fun extract720pStream(youtubeUrl: String): Result<PlayableStream> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = youtubeUrl.trim()
            require(cleanUrl.isNotEmpty()) { "Empty YouTube URL" }

            val info = StreamInfo.getInfo(ServiceList.YouTube, cleanUrl)
            val videoTitle = info.name?.trim()?.takeIf { it.isNotEmpty() } ?: "YouTube"

            val muxed = info.videoStreams.orEmpty().mapNotNull { rank(it) }
            val dashVideo = info.videoOnlyStreams.orEmpty().mapNotNull { rank(it) }

            // 1) Muxed 720p (prefer mp4)
            val muxed720 = muxed.filter { it.height == 720 }
                .sortedBy { it.formatPriority }.firstOrNull()

            var audio: String? = null
            val best: RankedYouTubeStream = muxed720 ?: run {
                // 2) DASH video-only <= 720p (720p mp4/avc1 first, then 480p) + best audio
                val dash = dashVideo.sortedWith(
                    compareBy<RankedYouTubeStream> { it.resolutionPriority }
                        .thenBy { it.formatPriority }
                        .thenByDescending { it.height }
                ).firstOrNull()
                val audioStream = info.audioStreams.orEmpty()
                    .filter { s -> s.content?.trim()?.startsWith("http", ignoreCase = true) == true }
                    .sortedWith(
                        compareBy<AudioStream> {
                            val f = it.format
                            val isM4a = f == MediaFormat.M4A ||
                                f?.suffix.equals("m4a", ignoreCase = true) ||
                                it.codec?.contains("mp4a", ignoreCase = true) == true
                            if (isM4a) 0 else 1
                        }.thenByDescending { it.averageBitrate }
                    ).firstOrNull()
                if (dash != null && audioStream != null && dash.height >= 480) {
                    audio = audioStream.content.trim()
                    dash
                } else {
                    // 3) Fallback muxed 480p / 360p (or best muxed <= 720p)
                    muxed.sortedWith(
                        compareBy<RankedYouTubeStream> { it.resolutionPriority }
                            .thenBy { it.formatPriority }
                            .thenByDescending { it.height }
                    ).firstOrNull()
                        ?: dash?.takeIf { audioStream != null }?.also { audio = audioStream!!.content.trim() }
                        ?: throw IllegalStateException("Nessuno stream <= 720p disponibile per $cleanUrl")
                }
            }

            val qualityBadge = "${best.height}p"

            Log.d(
                TAG,
                "Extracted YouTube stream [$qualityBadge${if (audio != null) " DASH" else ""}] for '$videoTitle': ${best.url.take(96)}..."
            )

            PlayableStream(
                url = best.url,
                name = qualityBadge,
                title = videoTitle,
                headers = mapOf("User-Agent" to DEFAULT_USER_AGENT),
                score = best.height,
                languageTag = qualityBadge,
                qualityBadge = qualityBadge,
                audioUrl = audio
            )
        }.onFailure { err ->
            Log.w(TAG, "extract720pStream failed for $youtubeUrl: ${err.message}", err)
        }
    }


    private fun rank(stream: VideoStream): RankedYouTubeStream? {
        val directUrl = stream.content?.trim()?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return null
        val height = resolveStreamHeight(stream)
        if (height <= 0 || height > MAX_ALLOWED_HEIGHT) return null
        val resolutionPriority = when (height) { 720 -> 0; 480 -> 1; 360 -> 2; else -> 3 }
        val isMp4 = stream.format == MediaFormat.MPEG_4 ||
            stream.format?.suffix.equals("mp4", ignoreCase = true) ||
            stream.format?.mimeType?.contains("mp4", ignoreCase = true) == true ||
            stream.codec?.contains("avc1", ignoreCase = true) == true
        return RankedYouTubeStream(stream, directUrl, height, resolutionPriority, if (isMp4) 0 else 1)
    }
    private data class RankedYouTubeStream(
        val stream: VideoStream,
        val url: String,
        val height: Int,
        val resolutionPriority: Int,
        val formatPriority: Int
    )

    companion object {
        private const val TAG = "YouTubeExtractor"
        private const val MAX_ALLOWED_HEIGHT = 720
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val RESOLUTION_REGEX = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
        private val initialized = AtomicBoolean(false)

        @Synchronized
        fun ensureInitialized(okHttpClient: OkHttpClient) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(OkHttpNewPipeDownloader(okHttpClient))
            }
        }

        private fun resolveStreamHeight(stream: VideoStream): Int {
            val rawResolution = stream.getResolution().orEmpty()
            val parsedFromLabel = RESOLUTION_REGEX.find(rawResolution)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (parsedFromLabel != null && parsedFromLabel > 0) {
                return parsedFromLabel
            }
            if (stream.height > 0) {
                return stream.height
            }
            val itagHeight = stream.itagItem?.height ?: -1
            if (itagHeight > 0) {
                return itagHeight
            }
            return -1
        }
    }

    private class OkHttpNewPipeDownloader(
        private val client: OkHttpClient
    ) : Downloader() {

        override fun execute(request: Request): Response {
            val httpMethod = request.httpMethod()
            val url = request.url()
            val headers = request.headers()
            val dataToSend = request.dataToSend()

            val requestBody = when {
                dataToSend != null -> dataToSend.toRequestBody(null)
                httpMethod.equals("POST", ignoreCase = true) ||
                    httpMethod.equals("PUT", ignoreCase = true) -> ByteArray(0).toRequestBody(null)
                else -> null
            }

            val builder = OkHttpRequest.Builder()
                .url(url)
                .method(httpMethod, requestBody)

            var hasUserAgent = false
            for ((headerName, headerValueList) in headers) {
                if (headerName.equals("User-Agent", ignoreCase = true)) {
                    hasUserAgent = true
                }
                builder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    builder.addHeader(headerName, headerValue)
                }
            }
            if (!hasUserAgent) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
            }

            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha Challenge requested", url)
                }
                val responseBodyToReturn = response.body?.string()
                val latestUrl = response.request.url.toString()
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBodyToReturn,
                    latestUrl
                )
            }
        }
    }
}