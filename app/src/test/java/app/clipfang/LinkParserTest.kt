package app.clipfang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {
    private fun post(kind: String, code: String) = LinkParser.Post(kind, code)

    @Test fun acceptsCommonForms() {
        assertEquals(post("reel", "AbC_d-12345"), LinkParser.parse("https://www.instagram.com/reel/AbC_d-12345/?igsh=xyz"))
        assertEquals(post("reel", "AbCd1234567"), LinkParser.parse("https://instagram.com/reels/AbCd1234567"))
        assertEquals(post("p", "AbCd1234567"), LinkParser.parse("instagram.com/p/AbCd1234567/"))
        assertEquals(post("tv", "AbCd1234567"), LinkParser.parse("http://m.instagram.com/tv/AbCd1234567#x"))
        assertEquals(post("reel", "AbCd1234567"), LinkParser.parse("https://www.instagram.com/some.user/reel/AbCd1234567/"))
        assertEquals(post("p", "AbCd1234567"), LinkParser.parse("Schau mal: https://www.instagram.com/p/AbCd1234567/ toll!"))
    }

    @Test fun recognisesShareAndStory() {
        assertEquals(LinkParser.Share("/share/reel/BAabc12345/"), LinkParser.parse("https://www.instagram.com/share/reel/BAabc12345?x=1"))
        assertEquals(LinkParser.Story, LinkParser.parse("https://www.instagram.com/stories/user/123456/"))
    }

    @Test fun rejectsForeignAndBrokenLinks() {
        listOf(
            "", "hallo", "https://example.com/p/AbCd1234567/", "https://instagram.com.evil.com/p/AbCd1234567/",
            "https://evilinstagram.com/p/AbCd1234567/", "https://evil.instagram.com/p/AbCd1234567/",
            "https://evil.com/instagram.com/p/AbCd1234567/", "https://www.instagram.com/someuser/",
            "https://www.instagram.com/p/", "https://www.instagram.com/p/ab%2F..%2F/", "https://user@instagram.com/p/AbCd1234567/",
        ).forEach { assertEquals(it, LinkParser.Invalid, LinkParser.parse(it)) }
    }

    @Test fun embedUrl() {
        assertEquals("https://www.instagram.com/reel/AbCd1234567/embed/captioned/", post("reel", "AbCd1234567").embedUrl("https://www.instagram.com"))
    }

    @Test fun mediaHosts() {
        assertTrue(MediaHosts.allowed("https://scontent-fra3-1.cdninstagram.com/v/a.mp4?x=1"))
        assertTrue(MediaHosts.allowed("https://instagram.fabc1-1.fna.fbcdn.net/v/a.mp4"))
        assertFalse(MediaHosts.allowed("http://scontent.cdninstagram.com/a.mp4"))
        assertFalse(MediaHosts.allowed("https://cdninstagram.com.evil.com/a.mp4"))
        assertFalse(MediaHosts.allowed("https://evilcdninstagram.com/a.mp4"))
        assertFalse(MediaHosts.allowed("file:///sdcard/a.mp4"))
        assertFalse(MediaHosts.allowed("http://10.0.2.2/a.mp4"))
    }

    @Test fun fileNames() {
        assertEquals("a.b_c_CODE1.mp4", MediaHosts.fileName("a.b_c", "CODE1", 0, 1, true))
        assertEquals("instagram_CODE1_2.jpg", MediaHosts.fileName("../../", "CODE1", 1, 3, false))
    }
}
