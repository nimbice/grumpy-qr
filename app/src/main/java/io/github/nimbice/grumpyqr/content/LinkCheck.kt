package io.github.nimbice.grumpyqr.content

import java.net.IDN

/**
 * What we can tell about a link without visiting it. This is all offline: we
 * only look at the text of the link itself.
 */
data class LinkReport(
    /** The link exactly as scanned (trimmed). */
    val url: String,
    /** What we'd hand to the system to open, or null if we refuse to open it. */
    val openUrl: String?,
    /** Lower-case scheme, e.g. "https". */
    val scheme: String,
    /**
     * The host in its unambiguous form. Lookalike (international) addresses
     * stay in their raw "xn--" form here, so they can't pass for something
     * they're not. Null for non-web links.
     */
    val host: String?,
    /** What a lookalike address pretends to be (its Unicode rendering), or null. */
    val lookalikeHost: String?,
    /**
     * The part of the host that decides who you're actually talking to, e.g.
     * "example.co.uk". Always in unambiguous ASCII form.
     */
    val site: String?,
    /** Things worth pointing out, most important first. */
    val notes: List<LinkNote>,
)

/** Ordered from most to least important. */
enum class LinkNote {
    /** javascript:, data:, intent:, file: ... We won't open these at all. */
    BLOCKED_SCHEME,
    /** Not a web page: it opens some other app. */
    OPENS_APP,
    /** "https://yourbank.com@evil.example" really goes to evil.example. */
    HIDDEN_USERNAME,
    /** International characters, some of which look exactly like Latin letters. */
    LOOKALIKE_CHARACTERS,
    /** A bare IP address instead of a name. */
    IP_ADDRESS,
    /** Plain http://, so the connection isn't encrypted. */
    NOT_ENCRYPTED,
    /** A link shortener or redirect service that hides the real destination. */
    SHORTENER,
    /** A non-standard port, which ordinary websites rarely use. */
    UNUSUAL_PORT,
}

object LinkCheck {

    /** Schemes we never open: they can run code or reach into other apps. */
    val BLOCKED_SCHEMES = setOf(
        "javascript", "vbscript", "data", "file", "content", "intent", "android-app", "about", "chrome", "blob", "jar",
    )

    fun inspect(raw: String): LinkReport {
        val url = raw.trim()
        if (url.lowercase().startsWith("www.")) {
            return inspect("https://$url").copy(url = url)
        }

        val scheme = SCHEME.find(url)?.groupValues?.get(1)?.lowercase()
            ?: return LinkReport(url, null, "", null, null, null, listOf(LinkNote.BLOCKED_SCHEME))
        // Android matches schemes case-sensitively, and QR codes often shout ("HTTPS://...").
        val normalized = scheme + url.substring(scheme.length)

        if (scheme in BLOCKED_SCHEMES) {
            return LinkReport(url, null, scheme, null, null, null, listOf(LinkNote.BLOCKED_SCHEME))
        }
        if (scheme != "http" && scheme != "https") {
            return LinkReport(url, normalized, scheme, null, null, null, listOf(LinkNote.OPENS_APP))
        }

        val notes = mutableSetOf<LinkNote>()
        // Browsers treat "\" like "/" in web links, so stop the authority there too.
        val authority = url.substring(scheme.length + 1).trimStart('/', '\\').takeWhile { it !in "/?#\\" }

        var hostPort = authority
        if ('@' in authority) {
            notes += LinkNote.HIDDEN_USERNAME
            hostPort = authority.substringAfterLast('@')
        }

        val rawHost: String
        val port: Int?
        if (hostPort.startsWith("[")) {
            rawHost = hostPort.substringBefore(']') + "]"
            port = hostPort.substringAfter("]:", "").toIntOrNull()
        } else {
            rawHost = hostPort.substringBefore(':')
            port = hostPort.substringAfter(':', "").toIntOrNull()
        }
        val host = rawHost.lowercase().trimEnd('.')
        if (host.isEmpty()) {
            return LinkReport(url, null, scheme, null, null, null, emptyList())
        }

        var unicodeHost = host
        var asciiHost = host
        if (host.any { it.code > 127 }) {
            notes += LinkNote.LOOKALIKE_CHARACTERS
            asciiHost = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        } else if (host.split('.').any { it.startsWith("xn--") }) {
            notes += LinkNote.LOOKALIKE_CHARACTERS
            unicodeHost = runCatching { IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        }

        val isIp = host.startsWith("[") || IPV4.matches(host) || host.all(Char::isDigit) || host.startsWith("0x")
        if (isIp) notes += LinkNote.IP_ADDRESS
        if (scheme == "http") notes += LinkNote.NOT_ENCRYPTED
        if (port != null && port != if (scheme == "https") 443 else 80) notes += LinkNote.UNUSUAL_PORT

        val asciiSite = if (isIp) host else registrableDomain(asciiHost)
        if (asciiSite in SHORTENERS || asciiHost in SHORTENERS) notes += LinkNote.SHORTENER

        return LinkReport(
            url = url,
            openUrl = normalized,
            scheme = scheme,
            host = asciiHost,
            lookalikeHost = unicodeHost.takeIf { it != asciiHost },
            // Never let lookalike letters into the headline: "аррӏе.com" must not read as apple.com.
            site = asciiSite,
            notes = notes.sortedBy { it.ordinal },
        )
    }

    /**
     * Best-effort "registrable domain": the name someone actually had to buy.
     * A full Public Suffix List would be more precise, but this small list
     * covers the common country suffixes and the free hosting platforms that
     * phishing pages like to hide on.
     */
    internal fun registrableDomain(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_PART_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
    }

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    private val SHORTENERS = setOf(
        "bit.ly", "bitly.com", "bit.do", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "v.gd",
        "buff.ly", "rebrand.ly", "cutt.ly", "shorturl.at", "tiny.cc", "rb.gy", "s.id", "t.ly", "lnkd.in",
        "surl.li", "urlz.fr", "shorturl.com", "short.gy", "tiny.one",
        // Common "dynamic QR code" redirect services.
        "qrco.de", "q-r.to", "qr.net", "ead.me", "me-qr.com", "qr1.be", "qr.codes",
    )

    private val MULTI_PART_SUFFIXES = setOf(
        // Country second-level domains.
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "sch.uk", "nhs.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "asn.au", "id.au",
        "co.nz", "org.nz", "net.nz", "govt.nz", "ac.nz",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "gr.jp",
        "com.br", "net.br", "org.br", "gov.br",
        "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
        "com.hk", "org.hk", "edu.hk", "gov.hk",
        "com.tw", "org.tw", "edu.tw", "gov.tw",
        "com.sg", "org.sg", "edu.sg", "gov.sg", "com.my", "gov.my",
        "co.in", "net.in", "org.in", "gov.in", "ac.in", "firm.in", "gen.in", "ind.in",
        "co.za", "org.za", "gov.za", "ac.za",
        "com.mx", "org.mx", "gob.mx", "com.ar", "gob.ar", "com.co", "gov.co", "com.pe", "gob.pe",
        "com.tr", "org.tr", "gov.tr", "edu.tr",
        "co.kr", "or.kr", "go.kr", "ac.kr",
        "co.il", "org.il", "ac.il", "gov.il",
        "co.id", "or.id", "go.id", "ac.id",
        "com.ph", "gov.ph", "com.pk", "gov.pk", "com.ng", "gov.ng", "com.eg", "gov.eg",
        "com.sa", "gov.sa", "com.ua", "gov.ua", "co.th", "or.th", "go.th", "ac.th", "com.vn", "gov.vn",
        "co.ke", "or.ke", "go.ke", "ac.ke", "com.gh", "com.np", "com.bd", "gov.bd", "com.lk",
        "com.qa", "com.kw", "com.om", "com.bh", "com.lb", "com.jo", "com.cy", "com.mt",
        "co.at", "or.at", "gv.at", "ac.at", "com.pl", "net.pl", "org.pl", "gov.pl",
        // Free hosting platforms: anyone can get "<anything>.<platform>".
        "github.io", "gitlab.io", "blogspot.com", "wordpress.com", "herokuapp.com", "web.app",
        "firebaseapp.com", "appspot.com", "pages.dev", "workers.dev", "vercel.app", "netlify.app",
        "azurewebsites.net", "cloudfront.net", "onrender.com", "glitch.me", "ngrok.io", "ngrok-free.app",
        "wixsite.com", "weebly.com", "square.site", "carrd.co", "notion.site", "webflow.io",
        "framer.website", "duckdns.org", "translate.goog",
    )
}
