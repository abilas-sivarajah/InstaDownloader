package app.clipfang

import java.net.URI

/** Nur Instagrams eigene Medienserver sind als Download-Quelle erlaubt. */
object MediaHosts {
    fun allowed(url: String, extraHost: String = ""): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (extraHost.isNotEmpty() && host == extraHost) return true
        return uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null &&
            (host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net"))
    }

    fun fileName(owner: String, shortcode: String, index: Int, total: Int, video: Boolean): String {
        val safe = owner.replace(Regex("[^A-Za-z0-9._-]"), "").trim('.').take(40).ifEmpty { "instagram" }
        val number = if (total > 1) "_${index + 1}" else ""
        return "${safe}_$shortcode$number.${if (video) "mp4" else "jpg"}"
    }
}
