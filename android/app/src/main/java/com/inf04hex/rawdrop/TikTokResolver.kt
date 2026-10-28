package com.inf04hex.rawdrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class VideoDetails(
    val videoId: String,
    val title: String,
    val author: String,
    val downloadUrl: String
)

class TikTokResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolveStream(rawUrl: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = extractUrl(rawUrl) ?: return@withContext Result.failure(
                IllegalArgumentException("No valid TikTok URL found in input")
            )

            // Step 1: Follow redirects to get canonical URL and extract video ID
            val canonicalUrl = resolveRedirects(cleanUrl)
            val videoId = extractVideoId(canonicalUrl)

            // Step 2: Try direct extraction via official TikTok Aweme API
            if (!videoId.isNullOrEmpty()) {
                val directResult = fetchViaOfficialApi(videoId)
                if (directResult != null) {
                    return@withContext Result.success(directResult)
                }
            }

            // Step 3: Fallback extraction via direct resolver
            val fallbackResult = fetchViaFallback(cleanUrl)
            if (fallbackResult != null) {
                return@withContext Result.success(fallbackResult)
            }

            Result.failure(Exception("Could not extract clean video stream from TikTok CDN"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractUrl(input: String): String? {
        val pattern = Pattern.compile("https?://[a-zA-Z0-9./?=_%&~+-]+")
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val url = matcher.group(0)
            if (url.contains("tiktok.com")) {
                return url
            }
        }
        return null
    }

    private fun resolveRedirects(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            url
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "/video/(\\d+)",
            "/v/(\\d+)",
            "item_id=(\\d+)"
        )
        for (patternStr in patterns) {
            val p = Pattern.compile(patternStr)
            val m = p.matcher(url)
            if (m.find()) {
                return m.group(1)
            }
        }
        return null
    }

    private fun fetchViaOfficialApi(videoId: String): VideoDetails? {
        return try {
            val apiUrl = "https://api16-normal-c-useast1a.tiktokv.com/aweme/v1/feed/?aweme_id=$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "com.ss.android.ugc.trill/260103 (Linux; U; Android 13; en_US; Pixel 7; Build/TQ2A.230505.002; Cronet/58.0.2991.0)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)
                val awemeList = root.optJSONArray("aweme_list") ?: return null
                if (awemeList.length() == 0) return null

                val item = awemeList.getJSONObject(0)
                val desc = item.optString("desc", "TikTok Video")
                val author = item.optJSONObject("author")?.optString("nickname", "TikTok User") ?: "TikTok User"
                val video = item.optJSONObject("video") ?: return null

                // play_addr contains uncompressed streams without watermark
                val playAddr = video.optJSONObject("play_addr")
                val urlList = playAddr?.optJSONArray("url_list")
                if (urlList != null && urlList.length() > 0) {
                    val streamUrl = urlList.getString(0)
                    return VideoDetails(videoId, desc, author, streamUrl)
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchViaFallback(originalUrl: String): VideoDetails? {
        return try {
            val encoded = URLEncoder.encode(originalUrl, "UTF-8")
            val apiUrl = "https://tikwm.com/api/?url=$encoded"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)
                if (root.optInt("code", -1) != 0) return null

                val data = root.optJSONObject("data") ?: return null
                val id = data.optString("id", System.currentTimeMillis().toString())
                val title = data.optString("title", "TikTok Video")
                val author = data.optJSONObject("author")?.optString("nickname", "TikTok User") ?: "TikTok User"

                // 'play' is the clean stream with no watermark
                val streamUrl = data.optString("play", "")
                if (streamUrl.isNotEmpty()) {
                    return VideoDetails(id, title, author, streamUrl)
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
