package com.ales.rawdrop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolveStream(rawUrl: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = extractUrl(rawUrl) ?: return@withContext Result.failure(
                IllegalArgumentException("No valid TikTok URL found in input")
            )

            // Step 1: Follow redirects if it's a short link
            val resolvedUrl = if (cleanUrl.contains("vt.tiktok.com") || cleanUrl.contains("vm.tiktok.com") || cleanUrl.contains("/t/")) {
                resolveRedirects(cleanUrl)
            } else {
                cleanUrl
            }

            // Step 2: Try Primary Resolver (TikWM POST with HD stream extraction)
            val primaryResult = fetchViaPostApi(resolvedUrl)
            if (primaryResult != null) {
                return@withContext Result.success(primaryResult)
            }

            // Step 3: Try Secondary Resolver (TikWM GET)
            val secondaryResult = fetchViaGetApi(resolvedUrl)
            if (secondaryResult != null) {
                return@withContext Result.success(secondaryResult)
            }

            // Step 4: Try Aweme Direct API using Video ID
            val videoId = extractVideoId(resolvedUrl)
            if (!videoId.isNullOrEmpty()) {
                val directResult = fetchViaAwemeApi(videoId)
                if (directResult != null) {
                    return@withContext Result.success(directResult)
                }
            }

            Result.failure(Exception("Could not extract clean video stream from TikTok CDN. Please try again."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractUrl(input: String): String? {
        // Correct regex that supports @username, query parameters, and special characters
        val pattern = Pattern.compile("https?://[^\\s\"'<>]+")
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            var url = matcher.group(0)
            // Trim any trailing punctuation
            url = url.trimEnd('.', ',', ')', ']', ';', '>')
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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

    private fun fetchViaPostApi(url: String): VideoDetails? {
        return try {
            val formBody = FormBody.Builder()
                .add("url", url)
                .add("count", "12")
                .add("cursor", "0")
                .add("web", "1")
                .add("hd", "1")
                .build()

            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                parseTikWmJson(jsonStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchViaGetApi(url: String): VideoDetails? {
        return try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val apiUrl = "https://www.tikwm.com/api/?url=$encoded&hd=1"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                parseTikWmJson(jsonStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTikWmJson(jsonStr: String): VideoDetails? {
        return try {
            val root = JSONObject(jsonStr)
            if (root.optInt("code", -1) != 0) return null

            val data = root.optJSONObject("data") ?: return null
            val id = data.optString("id", System.currentTimeMillis().toString())
            val title = data.optString("title", "TikTok Video")
            val author = data.optJSONObject("author")?.optString("nickname", "TikTok User") ?: "TikTok User"

            // Prefer HD stream, otherwise standard clean stream without watermark
            var streamUrl = data.optString("hdplay", "")
            if (streamUrl.isEmpty()) {
                streamUrl = data.optString("play", "")
            }
            if (streamUrl.isEmpty()) {
                streamUrl = data.optString("wmplay", "")
            }

            if (streamUrl.isNotEmpty()) {
                if (streamUrl.startsWith("/")) {
                    streamUrl = "https://www.tikwm.com$streamUrl"
                }
                return VideoDetails(id, title, author, streamUrl)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchViaAwemeApi(videoId: String): VideoDetails? {
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
}
