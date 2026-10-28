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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolveStream(rawUrl: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = extractUrl(rawUrl) ?: return@withContext Result.failure(
                IllegalArgumentException("No valid TikTok link found in input")
            )

            // Step 1: Resolve redirects for short links (vt.tiktok.com, vm.tiktok.com, /t/)
            val canonicalUrl = if (cleanUrl.contains("vt.tiktok.com") || cleanUrl.contains("vm.tiktok.com") || cleanUrl.contains("/t/")) {
                resolveRedirects(cleanUrl)
            } else {
                cleanUrl
            }

            // Step 2: Extract numeric video ID
            val videoId = extractVideoId(canonicalUrl) ?: extractVideoId(cleanUrl)

            // Step 3: Strip heavy tracking query params down to clean canonical URL
            val pureUrl = if (!videoId.isNullOrEmpty()) {
                val authorMatch = Pattern.compile("(@[a-zA-Z0-9_.-]+)").matcher(canonicalUrl)
                val author = if (authorMatch.find()) authorMatch.group(1) else "@user"
                "https://www.tiktok.com/$author/video/$videoId"
            } else {
                canonicalUrl.split("?").first()
            }

            // Strategy 1: Direct Webpage Scrape (__UNIVERSAL_DATA_FOR_REHYDRATION__ / SIGI_STATE)
            // Same method used by yt-dlp: grabs the original video CDN stream straight from TikTok's HTML
            val webResult = fetchFromWebpage(pureUrl, videoId)
            if (webResult != null) {
                return@withContext Result.success(webResult)
            }

            // Strategy 2: Official Aweme Feed API with mobile app parameters
            if (!videoId.isNullOrEmpty()) {
                val awemeResult = fetchViaAwemeApi(videoId)
                if (awemeResult != null) {
                    return@withContext Result.success(awemeResult)
                }
            }

            // Strategy 3: LoveTik API
            val loveTikResult = fetchViaLoveTik(pureUrl)
            if (loveTikResult != null) {
                return@withContext Result.success(loveTikResult)
            }

            // Strategy 4: TikWM API with clean URL
            val tikwmResult = fetchViaTikwm(pureUrl)
            if (tikwmResult != null) {
                return@withContext Result.success(tikwmResult)
            }

            Result.failure(Exception("Could not extract clean video stream from TikTok CDN. Please verify the link."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractUrl(input: String): String? {
        val pattern = Pattern.compile("https?://[^\\s\"'<>]+")
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            var url = matcher.group(0)
            url = url.trimEnd('.', ',', ')', ']', ';', '>', '!')
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
            "item_id=(\\d+)",
            "share_item_id=(\\d+)"
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

    private fun fetchFromWebpage(pageUrl: String, videoId: String?): VideoDetails? {
        return try {
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null

                // 1. Check __UNIVERSAL_DATA_FOR_REHYDRATION__
                val rehydrationTag = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
                val rehydrationIdx = html.indexOf(rehydrationTag)
                if (rehydrationIdx != -1) {
                    val start = html.indexOf('>', rehydrationIdx) + 1
                    val end = html.indexOf("</script>", start)
                    if (start in 1 until end) {
                        val jsonStr = html.substring(start, end).trim()
                        val root = JSONObject(jsonStr)
                        val scope = root.optJSONObject("__DEFAULT_SCOPE__")
                        val videoDetail = scope?.optJSONObject("webapp.video-detail")
                        val itemStruct = videoDetail?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                        val video = itemStruct?.optJSONObject("video")

                        if (video != null) {
                            val id = itemStruct.optString("id", videoId ?: System.currentTimeMillis().toString())
                            val title = itemStruct.optString("desc", "TikTok Video")
                            val author = itemStruct.optJSONObject("author")?.optString("nickname", "TikTok User") ?: "TikTok User"

                            var streamUrl = ""
                            val bitrateInfo = video.optJSONArray("bitrateInfo")
                            if (bitrateInfo != null && bitrateInfo.length() > 0) {
                                for (i in 0 until bitrateInfo.length()) {
                                    val bObj = bitrateInfo.getJSONObject(i)
                                    val playAddr = bObj.optJSONObject("PlayAddr")
                                    val urlList = playAddr?.optJSONArray("UrlList")
                                    if (urlList != null && urlList.length() > 0) {
                                        streamUrl = urlList.getString(0)
                                        if (streamUrl.isNotEmpty()) break
                                    }
                                }
                            }

                            if (streamUrl.isEmpty()) {
                                streamUrl = video.optString("playAddr", "")
                            }

                            if (streamUrl.isNotEmpty()) {
                                return VideoDetails(id, title, author, streamUrl)
                            }
                        }
                    }
                }

                // 2. Check SIGI_STATE
                val sigiTag = "SIGI_STATE"
                val sigiIdx = html.indexOf(sigiTag)
                if (sigiIdx != -1) {
                    val start = html.indexOf('>', sigiIdx) + 1
                    val end = html.indexOf("</script>", start)
                    if (start in 1 until end) {
                        val jsonStr = html.substring(start, end).trim()
                        val root = JSONObject(jsonStr)
                        val itemModule = root.optJSONObject("ItemModule")
                        if (itemModule != null && !videoId.isNullOrEmpty()) {
                            val item = itemModule.optJSONObject(videoId)
                            val video = item?.optJSONObject("video")
                            val playAddr = video?.optString("playAddr", "") ?: ""
                            if (playAddr.isNotEmpty()) {
                                val desc = item.optString("desc", "TikTok Video")
                                val author = item.optString("author", "TikTok User")
                                return VideoDetails(videoId, desc, author, playAddr)
                            }
                        }
                    }
                }

                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchViaAwemeApi(videoId: String): VideoDetails? {
        return try {
            val apiUrl = "https://api16-normal-c-useast1a.tiktokv.com/aweme/v1/feed/?aweme_id=$videoId&aid=1233&version_name=26.1.3&version_code=260103&app_name=musical_ly&device_platform=android&device_type=Pixel%207&channel=googleplay"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "com.ss.android.ugc.trill/260103 (Linux; U; Android 13; en_US; Pixel 7; Build/TQ2A.230505.002; Cronet/58.0.2991.0)")
                .header("Accept", "application/json")
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

    private fun fetchViaLoveTik(pureUrl: String): VideoDetails? {
        return try {
            val formBody = FormBody.Builder()
                .add("query", pureUrl)
                .build()
            val request = Request.Builder()
                .url("https://lovetik.com/api/ajax/search")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: return null
                val root = JSONObject(jsonStr)
                if (root.optString("status") != "ok") return null

                val vid = root.optString("vid", System.currentTimeMillis().toString())
                val desc = root.optString("desc", "TikTok Video")
                val author = root.optString("author", "TikTok User")
                val links = root.optJSONArray("links") ?: return null

                for (i in 0 until links.length()) {
                    val linkObj = links.getJSONObject(i)
                    val streamUrl = linkObj.optString("a", "")
                    if (streamUrl.isNotEmpty() && linkObj.optString("format") == "mp4") {
                        return VideoDetails(vid, desc, author, streamUrl)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchViaTikwm(pureUrl: String): VideoDetails? {
        return try {
            val formBody = FormBody.Builder()
                .add("url", pureUrl)
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
                val root = JSONObject(jsonStr)
                if (root.optInt("code", -1) != 0) return null

                val data = root.optJSONObject("data") ?: return null
                val id = data.optString("id", System.currentTimeMillis().toString())
                val title = data.optString("title", "TikTok Video")
                val author = data.optJSONObject("author")?.optString("nickname", "TikTok User") ?: "TikTok User"

                var streamUrl = data.optString("hdplay", "")
                if (streamUrl.isEmpty()) {
                    streamUrl = data.optString("play", "")
                }
                if (streamUrl.isNotEmpty()) {
                    if (streamUrl.startsWith("/")) {
                        streamUrl = "https://www.tikwm.com$streamUrl"
                    }
                    return VideoDetails(id, title, author, streamUrl)
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
