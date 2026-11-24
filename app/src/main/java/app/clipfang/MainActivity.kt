package app.clipfang

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val input by lazy { findViewById<EditText>(R.id.input) }
    private val status by lazy { findViewById<TextView>(R.id.status) }
    private val result by lazy { findViewById<View>(R.id.result) }
    private val items by lazy { findViewById<LinearLayout>(R.id.items) }
    private val thumb by lazy { findViewById<ImageView>(R.id.thumb) }
    private val downloads by lazy { getSystemService(DownloadManager::class.java) }
    private lateinit var extractor: Extractor
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        applyInsets()
        extractor = Extractor(this, findViewById(R.id.webHost), ::showResult, ::showFailure)

        findViewById<Button>(R.id.paste).setOnClickListener { paste() }
        findViewById<Button>(R.id.search).setOnClickListener { search() }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) search()
            action == EditorInfo.IME_ACTION_SEARCH
        }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onDestroy() {
        extractor.cancel()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        input.setText(text)
        search()
    }

    private fun paste() {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isNotBlank()) {
            input.setText(text.trim())
            search()
        }
    }

    private fun search() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        requestId++
        extractor.cancel()
        result.visibility = View.GONE
        when (val link = LinkParser.parse(input.text.toString())) {
            is LinkParser.Post -> {
                // Teilen-Kennung auch sichtbar entfernen.
                input.setText("https://www.instagram.com/${link.kind}/${link.shortcode}/")
                begin(link)
            }
            is LinkParser.Share -> begin(link)
            LinkParser.Story -> showError(R.string.err_story)
            LinkParser.Invalid -> showError(R.string.err_invalid)
        }
    }

    private fun begin(link: LinkParser.Result) {
        setLoading(true)
        status.setTextColor(getColor(R.color.ink_soft))
        status.setText(R.string.loading)
        status.visibility = View.VISIBLE
        extractor.start(link)
    }

    private fun setLoading(on: Boolean) {
        findViewById<View>(R.id.progress).visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun showError(message: Int) {
        setLoading(false)
        status.setTextColor(getColor(R.color.danger))
        status.setText(message)
        status.visibility = View.VISIBLE
    }

    private fun showFailure(reason: Failure) = showError(
        when (reason) {
            Failure.UNAVAILABLE -> R.string.err_unavailable
            Failure.LOGIN -> R.string.err_login
            Failure.BLOCKED -> R.string.err_blocked
            Failure.TIMEOUT -> R.string.err_timeout
            Failure.NETWORK -> R.string.err_network
        },
    )

    private fun showResult(shortcode: String, post: PostData) {
        setLoading(false)
        status.visibility = View.GONE
        findViewById<TextView>(R.id.owner).apply {
            text = "@${post.owner}"
            visibility = if (post.owner.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.caption).apply {
            text = post.caption
            visibility = if (post.caption.isEmpty()) View.GONE else View.VISIBLE
        }
        thumb.setImageDrawable(null)
        post.items.firstOrNull { it.thumb.isNotEmpty() }?.let { loadThumb(it.thumb) }

        items.removeAllViews()
        post.items.forEachIndexed { i, item ->
            val name = MediaHosts.fileName(post.owner, shortcode, i, post.items.size, item.video)
            items.addView(createButton(item, name, i, post.items.size))
        }
        result.visibility = View.VISIBLE
    }

    private fun createButton(item: MediaItem, fileName: String, index: Int, total: Int): Button {
        val base = getString(if (item.video) R.string.save_video else R.string.save_photo)
        val label = if (total > 1) getString(R.string.save_numbered, base, index + 1, total) else base
        val dp = resources.displayMetrics.density
        return Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(getColor(R.color.on_accent))
            setBackgroundResource(R.drawable.btn_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt())
                .apply { topMargin = (12 * dp).toInt() }
            setOnClickListener { download(this, item, fileName) }
        }
    }

    private fun download(button: Button, item: MediaItem, fileName: String) {
        val folder = if (item.video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val id = runCatching {
            downloads.enqueue(
                DownloadManager.Request(Uri.parse(item.url))
                    .setTitle(fileName)
                    .setMimeType(if (item.video) "video/mp4" else "image/jpeg")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(folder, "Clipfang/$fileName"),
            )
        }.getOrElse {
            button.setText(R.string.err_download)
            return
        }
        button.isEnabled = false
        track(button, id, item, fileName)
    }

    private fun track(button: Button, id: Long, item: MediaItem, fileName: String) {
        var state = DownloadManager.STATUS_FAILED
        var done = 0L
        var total = -1L
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                state = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            }
        }
        when (state) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                button.isEnabled = true
                button.setText(R.string.open)
                button.setOnClickListener { open(id, item) }
            }
            DownloadManager.STATUS_FAILED -> {
                downloads.remove(id)
                button.isEnabled = true
                button.setText(R.string.err_download)
                button.setOnClickListener { download(button, item, fileName) }
            }
            else -> {
                button.text = if (state == DownloadManager.STATUS_PAUSED) getString(R.string.waiting)
                else if (total > 0) getString(R.string.downloading, (done * 100 / total).toInt())
                else getString(R.string.downloading_unknown)
                main.postDelayed({ track(button, id, item, fileName) }, 400)
            }
        }
    }

    private fun open(id: Long, item: MediaItem) {
        val uri = downloads.getUriForDownloadedFile(id)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, if (item.video) "video/mp4" else "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadThumb(url: String) {
        if (!MediaHosts.allowed(url, BuildConfig.EXTRA_MEDIA_HOST)) return
        val request = requestId
        thread {
            val bitmap = runCatching { fetchBitmap(url) }.getOrNull() ?: return@thread
            main.post { if (request == requestId && !isDestroyed) thumb.setImageBitmap(bitmap) }
        }
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun applyInsets() {
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
    }
}
