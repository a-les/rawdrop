package com.ales.rawdrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DownloadHelper(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadVideoToGallery(
        videoDetails: VideoDetails,
        onProgress: (percent: Int) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(videoDetails.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://www.tiktok.com/")
                .header("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error during video download: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty video stream"))
            val totalBytes = body.contentLength()
            
            // Clean filename without invalid filesystem characters
            val sanitizedTitle = videoDetails.title.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ_\\-\\s]"), "").take(40).trim()
            val baseName = if (sanitizedTitle.isNotEmpty()) sanitizedTitle else "RawDrop"
            val fileName = "${baseName}_${videoDetails.videoId}.mp4"

            var outputStream: OutputStream? = null
            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped Storage: Android 10+ (API 29+)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RawDrop")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                savedUri = context.contentResolver.insert(collection, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                outputStream = context.contentResolver.openOutputStream(savedUri)
            } else {
                // Legacy Storage: Android 7 - 9
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "RawDrop"
                )
                if (!moviesDir.exists()) moviesDir.mkdirs()

                val file = File(moviesDir, fileName)
                outputStream = FileOutputStream(file)
                savedUri = Uri.fromFile(file)
            }

            outputStream?.use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Long = 0
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesRead += read

                        if (totalBytes > 0) {
                            val percent = ((bytesRead * 100) / totalBytes).toInt()
                            onProgress(percent.coerceIn(0, 100))
                        }
                    }
                    out.flush()
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream for video"))

            // Finish pending status on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedUri != null) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(savedUri, completeValues, null, null)
            }

            Result.success(savedUri!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
