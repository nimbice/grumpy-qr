package io.github.nimbice.grumpyqr.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.IDN

class LinkCheckTest {

    private fun notes(url: String) = LinkCheck.inspect(url).notes

    @Test
    fun ordinaryHttpsLinkHasNoNotes() {
        val r = LinkCheck.inspect("https://www.example.com/menu?table=4")
        assertEquals(emptyList<LinkNote>(), r.notes)
        assertEquals("www.example.com", r.host)
        assertEquals("example.com", r.site)
    }

    @Test
    fun siteIsTheRegistrableDomain() {
        assertEquals("example.co.uk", LinkCheck.inspect("https://shop.example.co.uk").site)
        // Phishing pages love long subdomains: the site is what matters.
        assertEquals("evil.example", LinkCheck.inspect("https://paypal.com.login.evil.example/").site)
        // Free hosting platforms: anyone can get a subdomain, so show it.
        assertEquals("paypal-login.web.app", LinkCheck.inspect("https://paypal-login.web.app").site)
    }

    @Test
    fun hiddenUsernameTrick() {
        val r = LinkCheck.inspect("https://yourbank.com@evil.example/login")
        assertTrue(LinkNote.HIDDEN_USERNAME in r.notes)
        assertEquals("evil.example", r.host)
    }

    @Test
    fun backslashEndsTheHostLikeInBrowsers() {
        assertEquals("evil.example", LinkCheck.inspect("https://evil.example\\@good.example").host)
    }

    @Test
    fun unencryptedAndPorts() {
        assertEquals(listOf(LinkNote.NOT_ENCRYPTED), notes("http://example.com"))
        assertTrue(LinkNote.UNUSUAL_PORT in notes("https://example.com:8443/"))
        assertEquals(emptyList<LinkNote>(), notes("https://example.com:443/"))
    }

    @Test
    fun ipAddresses() {
        assertTrue(LinkNote.IP_ADDRESS in notes("http://192.168.1.1/admin"))
        assertTrue(LinkNote.IP_ADDRESS in notes("http://[::1]:8080/"))
        assertTrue(LinkNote.IP_ADDRESS in notes("http://3232235777/"))
    }

    @Test
    fun lookalikeCharacters() {
        // Cyrillic "а" instead of Latin "a".
        val punycode = IDN.toASCII("аpple.com")
        val r = LinkCheck.inspect("https://аpple.com")
        assertTrue(LinkNote.LOOKALIKE_CHARACTERS in r.notes)
        // The headline must be the unambiguous form, never the disguise.
        assertEquals(punycode, r.host)
        assertEquals(punycode, r.site)
        assertEquals("аpple.com", r.lookalikeHost)

        val p = LinkCheck.inspect("https://$punycode")
        assertTrue(LinkNote.LOOKALIKE_CHARACTERS in p.notes)
        assertEquals(punycode, p.site)
        assertEquals("аpple.com", p.lookalikeHost)
        assertNull(LinkCheck.inspect("https://example.com").lookalikeHost)
    }

    @Test
    fun shorteners() {
        assertTrue(LinkNote.SHORTENER in notes("https://bit.ly/3abc"))
        assertTrue(LinkNote.SHORTENER in notes("https://l.ead.me/xyz"))
    }

    @Test
    fun blockedAndAppSchemes() {
        val js = LinkCheck.inspect("javascript:alert(1)")
        assertNull(js.openUrl)
        assertEquals(listOf(LinkNote.BLOCKED_SCHEME), js.notes)
        assertNull(LinkCheck.inspect("intent://scan/#Intent;scheme=zxing;end").openUrl)

        val market = LinkCheck.inspect("market://details?id=org.example")
        assertEquals("market://details?id=org.example", market.openUrl)
        assertEquals(listOf(LinkNote.OPENS_APP), market.notes)
    }

    @Test
    fun notesAreOrderedBySeverity() {
        val n = notes("http://user@bit.ly:8080/")
        assertEquals(
            listOf(LinkNote.HIDDEN_USERNAME, LinkNote.NOT_ENCRYPTED, LinkNote.SHORTENER, LinkNote.UNUSUAL_PORT),
            n,
        )
    }
}
