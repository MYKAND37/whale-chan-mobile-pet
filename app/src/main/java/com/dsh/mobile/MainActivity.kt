package com.dsh.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dsh.mobile.databinding.ActivityMainBinding

/**
 * A thin WebView shell that points at a DeepSeek Harness web UI.
 *
 * The URL is remembered across launches so a phone can keep talking to the
 * same tunnel endpoint (e.g. http://127.0.0.1:3080 over an SSH forward)
 * without retyping it every time.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: android.content.SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        binding.webView.webViewClient = WebViewClient()
        binding.webView.webChromeClient = WebChromeClient()

        binding.fabSettings.setOnClickListener { showUrlDialog() }

        val saved = prefs.getString(KEY_URL, null)
        if (saved.isNullOrBlank()) {
            showUrlDialog()
        } else {
            binding.webView.loadUrl(saved)
        }
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            hint = "http://127.0.0.1:3080"
            setText(prefs.getString(KEY_URL, DEFAULT_URL))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_go) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val normalized =
                    if (url.startsWith("http://") || url.startsWith("https://")) url
                    else "http://$url"
                prefs.edit().putString(KEY_URL, normalized).apply()
                binding.webView.loadUrl(normalized)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private companion object {
        const val PREFS = "dsh_mobile"
        const val KEY_URL = "target_url"
        const val DEFAULT_URL = "http://127.0.0.1:3080"
    }
}
