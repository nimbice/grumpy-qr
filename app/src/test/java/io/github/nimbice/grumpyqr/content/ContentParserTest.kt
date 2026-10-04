package io.github.nimbice.grumpyqr.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ContentParserTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private inline fun <reified T : Content> parse(text: String, retail: Boolean = false): T {
        val content = ContentParser.parse(text, retail, utc)
        assertTrue("Expected ${T::class.simpleName} but got $content", content is T)
        return content as T
    }

    @Test
    fun plainText() {
        assertEquals("hello world", parse<Content.Text>("hello world").text)
        assertTrue(ContentParser.parse("Note: buy milk") is Content.Text)
    }

    @Test
    fun webLinks() {
        assertEquals("example.com", parse<Content.Link>("https://example.com/path").report.site)
        assertEquals("https://www.example.com", parse<Content.Link>("www.example.com").report.openUrl)
        // QR alphanumeric mode makes upper-case URLs common.
        assertEquals("https://EXAMPLE.COM/A", parse<Content.Link>("HTTPS://EXAMPLE.COM/A").report.openUrl)
        assertTrue(ContentParser.parse("www.") is Content.Text)
    }

    @Test
    fun blockedSchemesAreLinksEvenWithSpaces() {
        val link = parse<Content.Link>("javascript:alert('hi there')")
        assertNull(link.report.openUrl)
        assertTrue(LinkNote.BLOCKED_SCHEME in link.report.notes)
    }

    @Test
    fun wifi() {
        val wifi = parse<Content.Wifi>("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss;H:true;;")
        assertEquals("My;Net", wifi.ssid)
        assertEquals("pa:ss", wifi.password)
        assertEquals(WifiSecurity.WPA, wifi.security)
        assertTrue(wifi.hidden)
    }

    @Test
    fun wifiVariants() {
        assertEquals(WifiSecurity.OPEN, parse<Content.Wifi>("WIFI:S:Cafe;T:nopass;;").security)
        assertNull(parse<Content.Wifi>("WIFI:S:Cafe;T:nopass;P:ignored;;").password)
        assertEquals(WifiSecurity.WPA3, parse<Content.Wifi>("WIFI:T:SAE;S:x;P:12345678;;").security)
        assertEquals(WifiSecurity.ENTERPRISE, parse<Content.Wifi>("WIFI:T:WPA2-EAP;S:x;E:PEAP;;").security)
        assertEquals("quoted", parse<Content.Wifi>("WIFI:S:\"quoted\";T:WPA;P:12345678;;").ssid)
        assertTrue(ContentParser.parse("WIFI:T:WPA;P:nossid;;") is Content.Text)
    }

    @Test
    fun meCard() {
        val c = parse<Content.Contact>("MECARD:N:Doe,Jane;TEL:+15551234;EMAIL:jane@example.com;ORG:Acme;;")
        assertEquals("Jane Doe", c.name)
        assertEquals(listOf("+15551234"), c.phones)
        assertEquals(listOf("jane@example.com"), c.emails)
        assertEquals("Acme", c.organization)
    }

    @Test
    fun vCard() {
        val card = """
            BEGIN:VCARD
            VERSION:3.0
            N:Doe;Jane;;Dr.;
            ORG:Acme\, Inc.;R&D
            TITLE:Boss
            item1.TEL;TYPE=CELL:+1 555 1234
            EMAIL;TYPE=INTERNET:jane@example.com
            ADR;TYPE=WORK:;;1 Main St;Springfield;IL;62701;USA
            NOTE:Line one\nline two
            END:VCARD
        """.trimIndent()
        val c = parse<Content.Contact>(card)
        assertEquals("Dr. Jane Doe", c.name)
        assertEquals("Acme, Inc., R&D", c.organization)
        assertEquals("Boss", c.title)
        assertEquals(listOf("+1 555 1234"), c.phones)
        assertEquals("1 Main St, Springfield, IL, 62701, USA", c.address)
        assertEquals("Line one\nline two", c.note)
    }

    @Test
    fun vCardPrefersFullNameAndUnfoldsLines() {
        val c = parse<Content.Contact>("BEGIN:VCARD\r\nFN:Jane\r\n  Q. Doe\r\nN:Doe;Jane\r\nEND:VCARD")
        assertEquals("Jane Q. Doe", c.name)
    }

    @Test
    fun email() {
        val e = parse<Content.Email>("mailto:a@b.com?subject=Hi%20there&body=x+y")
        assertEquals("a@b.com", e.to)
        assertEquals("Hi there", e.subject)
        assertEquals("x+y", e.body) // '+' is literal in mailto
        assertEquals("a@b.com", parse<Content.Email>("MATMSG:TO:a@b.com;SUB:S;BODY:B;;").to)
        assertEquals("someone@example.org", parse<Content.Email>("someone@example.org").to)
    }

    @Test
    fun phoneAndSms() {
        assertEquals("+15551234", parse<Content.Phone>("tel:+15551234").number)
        val a = parse<Content.Sms>("SMSTO:+15551234:Hello: world")
        assertEquals("+15551234", a.number)
        assertEquals("Hello: world", a.body)
        val b = parse<Content.Sms>("sms:+15551234?body=Hi%20you")
        assertEquals("+15551234", b.number)
        assertEquals("Hi you", b.body)
        assertNull(parse<Content.Sms>("sms:+15551234").body)
    }

    @Test
    fun geo() {
        val g = parse<Content.Geo>("geo:48.8584,2.2945?q=Eiffel+Tower")
        assertEquals(48.8584, g.latitude, 1e-9)
        assertEquals(2.2945, g.longitude, 1e-9)
        assertEquals("Eiffel Tower", g.query)
        assertTrue(ContentParser.parse("geo:999,0") is Content.Text)
    }

    @Test
    fun calendarEvent() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:19701025T030000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY:Launch party
            DTSTART;TZID=Europe/Berlin:20261101T190000
            DTEND:20261101T220000Z
            LOCATION:Rooftop
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val e = parse<Content.Event>(ics)
        assertEquals("Launch party", e.title)
        assertEquals("Rooftop", e.location)
        val berlin = ZonedDateTime.of(2026, 11, 1, 19, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(berlin.toInstant().toEpochMilli(), e.start?.epochMillis)
        assertFalse(e.start!!.allDay)
        assertEquals(ZonedDateTime.of(2026, 11, 1, 22, 0, 0, 0, utc).toInstant().toEpochMilli(), e.end?.epochMillis)
    }

    @Test
    fun allDayEvent() {
        val e = parse<Content.Event>("BEGIN:VEVENT\nSUMMARY:Holiday\nDTSTART;VALUE=DATE:20261225\nEND:VEVENT")
        assertTrue(e.start!!.allDay)
    }

    @Test
    fun twoFactorIsSensitive() {
        val otp = parse<Content.OneTimePassword>("otpauth://totp/Example:alice%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")
        assertEquals("Example", otp.issuer)
        assertEquals("alice@example.com", otp.account)
        assertTrue(otp.isSensitive)
        assertTrue(ContentParser.parse("otpauth-migration://offline?data=abc").isSensitive)
    }

    @Test
    fun passkeyIsSensitive() {
        assertTrue(parse<Content.Passkey>("FIDO:/1234567890").isSensitive)
    }

    @Test
    fun upi() {
        val p = parse<Content.Payment>("upi://pay?pa=shop@bank&pn=Corner+Shop&am=120.50&cu=INR&tn=Tea")
        assertEquals(PaymentKind.UPI, p.kind)
        assertEquals("Corner Shop", p.fields.toMap()[PaymentField.RECIPIENT])
        assertEquals("shop@bank", p.fields.toMap()[PaymentField.ACCOUNT])
        assertEquals("INR 120.50", p.fields.toMap()[PaymentField.AMOUNT])
    }

    @Test
    fun bitcoin() {
        val p = parse<Content.Payment>("bitcoin:bc1qxyz?amount=0.01&label=Donation")
        assertEquals("bitcoin", p.scheme)
        assertEquals("bc1qxyz", p.fields.toMap()[PaymentField.ACCOUNT])
        assertEquals("0.01", p.fields.toMap()[PaymentField.AMOUNT])
    }

    @Test
    fun epcGiroCode() {
        val text = "BCD\n002\n1\nSCT\nBFSWDE33BER\nWikimedia Foerdergesellschaft\nDE33100205000001194700\nEUR123.45\n\n\nSpende fuer Wikipedia"
        val p = parse<Content.Payment>(text)
        assertEquals(PaymentKind.EPC, p.kind)
        val f = p.fields.toMap()
        assertEquals("Wikimedia Foerdergesellschaft", f[PaymentField.RECIPIENT])
        assertEquals("DE33 1002 0500 0001 1947 00", f[PaymentField.ACCOUNT])
        assertEquals("EUR 123.45", f[PaymentField.AMOUNT])
        assertEquals("Spende fuer Wikipedia", f[PaymentField.MESSAGE])
        assertNull(p.uri)
    }

    @Test
    fun retailBarcodes() {
        assertFalse(parse<Content.Product>("4006381333931", retail = true).isBook)
        assertTrue(parse<Content.Product>("9780141036144", retail = true).isBook)
        // Digits from a QR code are just text.
        assertTrue(ContentParser.parse("4006381333931", isRetailCode = false) is Content.Text)
    }

    @Test
    fun recoveryPhrase() {
        val seed = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        assertEquals(12, parse<Content.RecoveryPhrase>(seed).wordCount)
        // An ordinary twelve word sentence is not a seed phrase.
        assertTrue(ContentParser.parse("the quick brown foxes jumped over lazy dogs while their owner slept") is Content.Text)
    }

    @Test
    fun percentDecodingHandlesUtf8() {
        assertEquals("café ☕", ContentParser.percentDecode("caf%C3%A9 %E2%98%95"))
        assertEquals("100%", ContentParser.percentDecode("100%"))
        assertEquals("a b", ContentParser.percentDecode("a+b", plusAsSpace = true))
    }
}
