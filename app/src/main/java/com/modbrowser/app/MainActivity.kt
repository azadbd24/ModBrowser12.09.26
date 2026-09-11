package com.modbrowser.app

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar

    private var isVideoPlaying = false
    private var adBlockEnabled = true
    private var darkModeEnabled = true

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        urlBar = findViewById(R.id.url_bar)
        progressBar = findViewById(R.id.progress_bar)

        AdBlocker.init(this)
        requestNotifPermissionIfNeeded()
        setupWebView()
        setupToolbar()

        val startUrl = intent?.dataString ?: HOME_URL
        webView.loadUrl(startUrl)
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // Required so YouTube (and other sites) can start/continue audio & video
        // without the user tapping play again after a state change.
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        // Keep the normal mobile UA at all times - no desktop-mode switch is ever needed
        // for any feature in this app.

        applyDarkMode()

        webView.addJavascriptInterface(JsBridge(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (adBlockEnabled && AdBlocker.isAd(request.url)) {
                    return AdBlocker.blockedResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                urlBar.setText(url)
                // Runs before page scripts - keeps the page thinking it's always visible,
                // which is what stops sites (incl. YouTube) from auto-pausing media
                // when the app is backgrounded or the screen turns off.
                webView.evaluateJavascript(AssetLoader.readAsset(this@MainActivity, "visibility_override.js"), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(VIDEO_WATCHER_JS, null)
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    webView.evaluateJavascript(
                        AssetLoader.readAsset(this@MainActivity, "youtube_audio_track.js"), null
                    )
                }
                if (darkModeEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    webView.evaluateJavascript(
                        AssetLoader.readAsset(this@MainActivity, "dark_mode_fallback.js"), null
                    )
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
    }

    private fun applyDarkMode() {
        val settings = webView.settings
        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) -> {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkModeEnabled)
            }
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) -> {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    settings,
                    if (darkModeEnabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
            // else: handled per-page by dark_mode_fallback.js injected in onPageFinished
        }
    }

    /** Called from JsBridge (UI thread) whenever any <video> on the page starts/stops. */
    fun onVideoPlayStateChanged(playing: Boolean) {
        isVideoPlaying = playing
        if (playing) PlaybackService.start(this) else PlaybackService.stop(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoPlaying) enterPip()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btn_forward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btn_reload).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_pip).setOnClickListener { enterPip() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu(it) }

        urlBar.setOnEditorActionListener { _, _, _ ->
            var input = urlBar.text.toString().trim()
            input = when {
                input.startsWith("http://") || input.startsWith("https://") -> input
                input.contains(".") && !input.contains(" ") -> "https://$input"
                else -> "https://www.google.com/search?q=" + Uri.encode(input)
            }
            webView.loadUrl(input)
            true
        }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.menu.findItem(R.id.action_adblock).isChecked = adBlockEnabled
        popup.menu.findItem(R.id.action_darkmode).isChecked = darkModeEnabled
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_adblock -> {
                    adBlockEnabled = !adBlockEnabled
                    true
                }
                R.id.action_darkmode -> {
                    darkModeEnabled = !darkModeEnabled
                    applyDarkMode()
                    webView.reload()
                    true
                }
                R.id.action_home -> {
                    webView.loadUrl(HOME_URL)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackService.stop(this)
    }

    companion object {
        const val HOME_URL = "https://www.google.com"

        /** Injected on every page: watches all <video> elements (present + future) and
         *  reports play/pause state back to native so we can hold a wake lock / show
         *  the background-playback notification only while something is actually playing. */
        const val VIDEO_WATCHER_JS = """
        (function() {
          if (window.__modbrowser_watcher_installed) return;
          window.__modbrowser_watcher_installed = true;
          function attach(video) {
            if (video.__modbrowser_attached) return;
            video.__modbrowser_attached = true;
            video.addEventListener('play', function() { AndroidBridge.onVideoStateChanged(true); });
            video.addEventListener('playing', function() { AndroidBridge.onVideoStateChanged(true); });
            video.addEventListener('pause', function() { AndroidBridge.onVideoStateChanged(false); });
            video.addEventListener('ended', function() { AndroidBridge.onVideoStateChanged(false); });
          }
          document.querySelectorAll('video').forEach(attach);
          var mo = new MutationObserver(function() {
            document.querySelectorAll('video').forEach(attach);
          });
          mo.observe(document.documentElement, { childList: true, subtree: true });
        })();
        """
    }
}
