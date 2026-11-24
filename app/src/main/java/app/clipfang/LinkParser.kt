package app.clipfang

import java.net.URI

/** Erkennt Instagram-Links in beliebigem Text. Zusätze hinter dem "?" (Teilen-Kennungen) werden verworfen. */
object LinkParser {
    sealed interface Result
    data class Post(val kind: String, val shortcode: String) : Result {
        fun embedUrl(base: String) = "$base/$kind/$shortcode/embed/captioned/"
    }
    /** Kurzlink (/share/...), der erst durch Weiterleitung den echten Beitrag verrät. */
    data class Share(val path: String) : Result
    data object Story : Result
    data object Invalid : Result

    private val urlRegex = Regex("""(?:https?://)?(?:[a-z0-9-]+\.)*(?:instagram\.com|instagr\.am)/[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val hosts = setOf("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am", "www.instagr.am")
    private val codeRegex = Regex("[A-Za-z0-9_-]{5,64}")
    private val kinds = mapOf("p" to "p", "reel" to "reel", "reels" to "reel", "tv" to "tv")

    fun parse(text: String): Result {
        val match = urlRegex.find(text) ?: return Invalid
        // Vor dem Treffer darf kein Teil eines anderen Hostnamens oder Pfads stehen (z. B. evil.com/instagram.com/p/…).
        val before = text.getOrNull(match.range.first - 1)
        if (before != null && !before.isWhitespace() && before !in "(<\"'") return Invalid
        val raw = if (match.value.startsWith("http", ignoreCase = true)) match.value else "https://${match.value}"
        val uri = runCatching { URI(raw.substringBefore('?').substringBefore('#')) }.getOrNull() ?: return Invalid
        val host = uri.host?.lowercase() ?: return Invalid
        if (host !in hosts) return Invalid

        val seg = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
        if (seg.isEmpty()) return Invalid
        if (seg[0] == "stories" || seg[0] == "s") return Story
        if (seg[0] == "share") {
            return if (seg.size >= 2 && seg.drop(1).all { codeRegex.matches(it) || it in kinds }) Share("/" + seg.joinToString("/") + "/") else Invalid
        }
        // /p/CODE, /reel/CODE oder /benutzer/reel/CODE
        for (i in 0..1) {
            val kind = kinds[seg.getOrNull(i)] ?: continue
            val code = seg.getOrNull(i + 1)?.trimEnd('.', ',', ')', '!') ?: continue
            if (codeRegex.matches(code)) return Post(kind, code)
        }
        return Invalid
    }
}
