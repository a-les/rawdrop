package com.ales.rawdrop

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var editUrl: EditText
    private lateinit var btnPaste: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var txtStatus: TextView
    private lateinit var btnOpenGallery: MaterialButton

    private val resolver = TikTokResolver()
    private lateinit var downloadHelper: DownloadHelper
    private var lastSavedVideoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadHelper = DownloadHelper(this)

        initViews()
        setupListeners()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun initViews() {
        editUrl = findViewById(R.id.editUrl)
        btnPaste = findViewById(R.id.btnPaste)
        btnDownload = findViewById(R.id.btnDownload)
        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)
        btnOpenGallery = findViewById(R.id.btnOpenGallery)
    }

    private fun setupListeners() {
        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pastedText = item?.text?.toString() ?: ""
            val extracted = extractUrl(pastedText)

            if (extracted != null) {
                editUrl.setText(extracted)
                txtStatus.text = "URL pasted from clipboard"
            } else {
                Toast.makeText(this, "No valid TikTok link in clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownload.setOnClickListener {
            val rawInput = editUrl.text.toString().trim()
            if (rawInput.isEmpty()) {
                showError("Please enter a TikTok URL")
                return@setOnClickListener
            }
            val validUrl = extractUrl(rawInput)
            if (validUrl == null) {
                showError("Invalid URL. Must be an official tiktok.com link")
                return@setOnClickListener
            }
            startDownloadProcess(validUrl)
        }

        btnOpenGallery.setOnClickListener {
            lastSavedVideoUri?.let { uri ->
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(viewIntent, "Play Video"))
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val extracted = extractUrl(sharedText)
            if (extracted != null) {
                editUrl.setText(extracted)
                txtStatus.text = "Shared from TikTok. Starting download..."
                startDownloadProcess(extracted)
            }
        }
    }

    private fun startDownloadProcess(rawUrl: String) {
        btnDownload.isEnabled = false
        btnOpenGallery.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        txtStatus.text = "Connecting to TikTok CDN..."

        lifecycleScope.launch {
            // 1. Resolve Stream
            val resolveResult = resolver.resolveStream(rawUrl)
            if (resolveResult.isFailure) {
                val error = resolveResult.exceptionOrNull()?.message ?: "Failed to resolve stream"
                showError("Error: $error")
                return@launch
            }

            val details = resolveResult.getOrThrow()
            txtStatus.text = "Stream found: ${details.author}\nStarting download..."
            progressBar.isIndeterminate = false
            progressBar.progress = 0

            // 2. Download directly to Gallery
            val downloadResult = downloadHelper.downloadVideoToGallery(details) { percent ->
                lifecycleScope.launch(Dispatchers.Main) {
                    progressBar.progress = percent
                    txtStatus.text = "Downloading HD stream: $percent%"
                }
            }

            if (downloadResult.isSuccess) {
                lastSavedVideoUri = downloadResult.getOrThrow()
                progressBar.visibility = View.GONE
                btnDownload.isEnabled = true
                txtStatus.text = "Saved to Gallery (Movies/RawDrop)! 🎉"
                btnOpenGallery.visibility = View.VISIBLE
            } else {
                val error = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                showError("Download error: $error")
            }
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        btnDownload.isEnabled = true
        txtStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun extractUrl(text: String): String? {
        val pattern = Pattern.compile("https?://[^\\s\"'<>]+")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            var url = matcher.group(0)
            url = url.trimEnd('.', ',', ')', ']', ';', '>')
            if (url.contains("tiktok.com")) {
                return url
            }
        }
        return null
    }
}
