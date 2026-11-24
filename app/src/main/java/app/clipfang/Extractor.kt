package app.clipfang

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

data class MediaItem(val video: Boolean, val url: String, val thumb: String)
data class PostData(val owner: String, val caption: String, val items: List<MediaItem>)

enum class Failure { UNAVAILABLE, LOGIN, BLOCKED, TIMEOUT, NETWORK }

/** Öffnet Instagrams öffentliche Einbettungsseite in einer unsichtbaren WebView und liest die Medienadressen aus. */
class Extractor(
    private val activity: Activity,
    private val host: ViewGroup,
    private val onSuccess: (shortcode: String, PostData) -> Unit,
    private val onFailure: (Failure) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val script by lazy { activity.assets.open("extract.js").bufferedReader().use { it.readText() } }
    private var web: WebView? = null
    private var shortcode = ""
    private var deadline = 0L

    fun start(link: LinkParser.Result) {
        cancel()
        deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        val view = createWebView().also { web = it }
        host.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        when (link) {
            is LinkParser.Post -> {
                shortcode = link.shortcode
                view.loadUrl(link.embedUrl(BuildConfig.EMBED_BASE))
            }
            is LinkParser.Share -> view.loadUrl(BuildConfig.EMBED_BASE + link.path)
            else -> return fail(Failure.UNAVAILABLE)
        }
        main.postDelayed(poll, POLL_MS)
    }

    fun cancel() {
        main.removeCallbacks(poll)
        shortcode = ""
        web?.let {
            it.webViewClient = WebViewClient()
            it.stopLoading()
            host.removeView(it)
            it.destroy()
        }
        web = null
    }

    private val poll = object : Runnable {
        override fun run() {
            val view = web ?: return
            if (SystemClock.elapsedRealtime() > deadline) return fail(Failure.TIMEOUT)
            if (shortcode.isEmpty()) {
                // Kurzlink: warten, bis die Weiterleitung den Beitrag zeigt.
                when (val target = LinkParser.parse(view.url.orEmpty())) {
                    is LinkParser.Post -> {
                        shortcode = target.shortcode
                        view.loadUrl(target.embedUrl(BuildConfig.EMBED_BASE))
                    }
                    else -> if (view.url.orEmpty().contains("/accounts/login")) return fail(Failure.LOGIN)
                }
                main.postDelayed(this, POLL_MS)
                return
            }
            view.evaluateJavascript(script) { raw ->
                if (web !== view) return@evaluateJavascript
                handle(raw)
            }
        }
    }

    private fun handle(raw: String?) {
        val json = runCatching { JSONObject(raw ?: "") }.getOrNull()
        when (json?.optString("status")) {
            "ok" -> {
                val data = parse(json)
                if (data.items.isEmpty()) fail(Failure.UNAVAILABLE) else {
                    val code = shortcode
                    cancel()
                    onSuccess(code, data)
                }
            }
            "blocked" -> fail(Failure.BLOCKED)
            "login" -> fail(Failure.LOGIN)
            "unavailable" -> fail(Failure.UNAVAILABLE)
            else -> main.postDelayed(poll, POLL_MS)
        }
    }

    private fun parse(json: JSONObject): PostData {
        val arr = json.getJSONArray("items")
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { MediaHosts.allowed(it.getString("url"), BuildConfig.EXTRA_MEDIA_HOST) }
            .map { MediaItem(it.getString("type") == "video", it.getString("url"), it.optString("thumb")) }
        return PostData(json.optString("owner"), json.optString("caption"), items)
    }

    private fun fail(reason: Failure) {
        cancel()
        onFailure(reason)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(activity).apply {
        val self = this
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        isFocusable = false
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            setAcceptThirdPartyCookies(self, false)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !isAllowedPage(request.url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && web === view) fail(Failure.NETWORK)
            }
        }
    }

    private fun isAllowedPage(uri: Uri): Boolean {
        val base = Uri.parse(BuildConfig.EMBED_BASE)
        return uri.scheme == base.scheme && (uri.host == base.host || uri.host == "instagram.com")
    }

    private companion object {
        const val POLL_MS = 400L
        const val TIMEOUT_MS = 25_000L
    }
}
