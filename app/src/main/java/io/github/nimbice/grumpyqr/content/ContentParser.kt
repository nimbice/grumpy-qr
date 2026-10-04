package io.github.nimbice.grumpyqr.content

import java.io.ByteArrayOutputStream
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Turns the raw text of a code into something more useful. Anything we don't
 * recognise (or can't parse cleanly) falls back to [Content.Text].
 */
object ContentParser {

    fun parse(raw: String, isRetailCode: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): Content {
        val text = raw.trim()
        val lower = text.lowercase()
        val parsed: Content? = when {
            text.isEmpty() -> null
            lower.startsWith("fido:/") -> Content.Passkey(text)
            lower.startsWith("otpauth://") || lower.startsWith("otpauth-migration://") -> parseOtp(text)
            lower.startsWith("wifi:") -> parseWifi(text)
            lower.startsWith("begin:vcard") -> parseVCard(text)
            lower.startsWith("mecard:") -> parseMeCard(text)
            lower.startsWith("begin:vevent") || lower.startsWith("begin:vcalendar") -> parseEvent(text, zone)
            lower.startsWith("mailto:") -> parseMailto(text)
            lower.startsWith("matmsg:") -> parseMatmsg(text)
            lower.startsWith("tel:") -> percentDecode(text.substring(4)).trim().ifEmpty { null }?.let(Content::Phone)
            lower.startsWith("smsto:") || lower.startsWith("mmsto:") -> parseSmsTo(text)
            lower.startsWith("sms:") || lower.startsWith("mms:") -> parseSmsUri(text)
            lower.startsWith("geo:") -> parseGeo(text)
            lower.startsWith("upi://") -> parseUpi(text)
            CRYPTO_SCHEMES.any { lower.startsWith("$it:") } -> parseCrypto(text)
            text.startsWith("BCD\n") || text.startsWith("BCD\r\n") -> parseEpc(text)
            text.startsWith("SPC\n") || text.startsWith("SPC\r\n") -> parseSwissBill(text)
            lower.startsWith("urlto:") -> text.substring(6).trim().ifEmpty { null }?.let(::link)
            lower.startsWith("mebkm:") -> parseFields(text.substring(6)).first("URL")?.let(::link)
            looksLikeLink(text) -> link(text)
            EMAIL.matches(text) -> Content.Email(text, null, null)
            isRetailCode && text.all(Char::isDigit) -> Content.Product(
                code = text,
                isBook = text.length == 13 && (text.startsWith("978") || text.startsWith("979")),
            )
            else -> recoveryPhraseOrNull(text)
        }
        return parsed ?: Content.Text(raw)
    }

    private fun link(url: String) = Content.Link(LinkCheck.inspect(url))

    private fun looksLikeLink(text: String): Boolean {
        val lower = text.lowercase()
        // Dangerous schemes are flagged as links (and then blocked) even if they contain spaces.
        if (LinkCheck.BLOCKED_SCHEMES.any { lower.startsWith("$it:") }) return true
        if (text.any(Char::isWhitespace)) return false
        return URL_WITH_AUTHORITY.matches(text) ||
            (lower.startsWith("www.") && text.length > 5 && '.' in text.substring(4)) ||
            APP_SCHEMES.any { lower.startsWith("$it:") }
    }

    // --- Wi-Fi ---------------------------------------------------------------

    private fun parseWifi(text: String): Content? {
        val fields = parseFields(text.substring(5))
        val ssid = fields.first("S") ?: return null
        val type = fields.first("T")?.uppercase().orEmpty()
        val password = fields.first("P")?.takeIf { it.isNotEmpty() }
        val security = when {
            type == "NOPASS" -> WifiSecurity.OPEN
            type == "WEP" -> WifiSecurity.WEP
            type == "SAE" || type == "WPA3" -> WifiSecurity.WPA3
            "EAP" in type || fields.first("E") != null -> WifiSecurity.ENTERPRISE
            type.isEmpty() && password == null -> WifiSecurity.OPEN
            else -> WifiSecurity.WPA
        }
        val hidden = fields.first("H").equals("true", ignoreCase = true)
        return Content.Wifi(
            ssid = ssid,
            password = password.takeIf { security != WifiSecurity.OPEN },
            security = security,
            hidden = hidden,
        )
    }

    // --- Contacts ------------------------------------------------------------

    private fun parseMeCard(text: String): Content? {
        val fields = parseFields(text.substring(7))
        val name = fields.first("N")?.let { n ->
            // MECARD names are "Last,First".
            if (',' in n) n.split(',', limit = 2).let { (last, first) -> "${first.trim()} ${last.trim()}".trim() } else n
        }
        return contactOrNull(
            name = name ?: fields.first("NICKNAME"),
            organization = fields.first("ORG"),
            title = null,
            phones = fields.all("TEL"),
            emails = fields.all("EMAIL"),
            urls = fields.all("URL"),
            address = fields.first("ADR"),
            note = fields.first("NOTE"),
        )
    }

    private fun parseVCard(text: String): Content? {
        var fullName: String? = null
        var structuredName: String? = null
        var organization: String? = null
        var title: String? = null
        var address: String? = null
        var note: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()

        for (line in parseContentLines(text)) {
            val value = line.value
            when (line.name) {
                "FN" -> fullName = unescapeText(value).trim()
                "N" -> {
                    // family;given;additional;prefix;suffix -> "prefix given additional family suffix"
                    val p = splitEscaped(value, ';').map { unescapeText(it).trim() }
                    structuredName = listOf(3, 1, 2, 0, 4).mapNotNull { p.getOrNull(it) }
                        .filter { it.isNotEmpty() }.joinToString(" ")
                }
                "ORG" -> organization = splitEscaped(value, ';').map { unescapeText(it).trim() }
                    .filter { it.isNotEmpty() }.joinToString(", ")
                "TITLE" -> title = unescapeText(value).trim()
                "TEL" -> phones += unescapeText(value).trim().removePrefix("tel:")
                "EMAIL" -> emails += unescapeText(value).trim()
                "URL" -> urls += unescapeText(value).trim()
                "ADR" -> address = splitEscaped(value, ';').map { unescapeText(it).trim() }
                    .filter { it.isNotEmpty() }.joinToString(", ")
                "NOTE" -> note = unescapeText(value).trim()
            }
        }
        return contactOrNull(
            name = fullName?.ifEmpty { null } ?: structuredName,
            organization = organization,
            title = title,
            phones = phones,
            emails = emails,
            urls = urls,
            address = address,
            note = note,
        )
    }

    private fun contactOrNull(
        name: String?,
        organization: String?,
        title: String?,
        phones: List<String>,
        emails: List<String>,
        urls: List<String>,
        address: String?,
        note: String?,
    ): Content? {
        val contact = Content.Contact(
            name = name?.trim()?.ifEmpty { null },
            organization = organization?.trim()?.ifEmpty { null },
            title = title?.trim()?.ifEmpty { null },
            phones = phones.map(String::trim).filter(String::isNotEmpty),
            emails = emails.map(String::trim).filter(String::isNotEmpty),
            urls = urls.map(String::trim).filter(String::isNotEmpty),
            address = address?.trim()?.ifEmpty { null },
            note = note?.trim()?.ifEmpty { null },
        )
        val empty = contact.name == null && contact.organization == null &&
            contact.phones.isEmpty() && contact.emails.isEmpty()
        return contact.takeUnless { empty }
    }

    // --- Calendar ------------------------------------------------------------

    private fun parseEvent(text: String, zone: ZoneId): Content? {
        val stack = ArrayDeque<String>()
        var title: String? = null
        var location: String? = null
        var description: String? = null
        var start: EventTime? = null
        var end: EventTime? = null
        var sawEvent = false

        for (line in parseContentLines(text)) {
            when (line.name) {
                "BEGIN" -> {
                    val component = line.value.trim().uppercase()
                    if (component == "VEVENT") sawEvent = true
                    stack.addLast(component)
                }
                "END" -> stack.removeLastOrNull()
            }
            // Only read properties of the (first) event, not e.g. VTIMEZONE blocks.
            if (stack.lastOrNull() != "VEVENT") continue
            when (line.name) {
                "SUMMARY" -> title = title ?: unescapeText(line.value).trim()
                "LOCATION" -> location = location ?: unescapeText(line.value).trim()
                "DESCRIPTION" -> description = description ?: unescapeText(line.value).trim()
                "DTSTART" -> start = start ?: parseIcsTime(line.value, line.params["TZID"], zone)
                "DTEND" -> end = end ?: parseIcsTime(line.value, line.params["TZID"], zone)
            }
        }
        if (!sawEvent || (title.isNullOrEmpty() && start == null)) return null
        return Content.Event(
            title = title?.ifEmpty { null },
            start = start,
            end = end,
            location = location?.ifEmpty { null },
            description = description?.ifEmpty { null },
        )
    }

    internal fun parseIcsTime(value: String, tzid: String?, zone: ZoneId): EventTime? {
        val match = ICS_TIME.matchEntire(value.trim().replace("-", "").replace(":", "")) ?: return null
        val g = match.groupValues
        return try {
            if (g[4].isEmpty()) {
                val date = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                EventTime(date.atStartOfDay(zone).toInstant().toEpochMilli(), allDay = true)
            } else {
                val dateTime = LocalDateTime.of(
                    g[1].toInt(), g[2].toInt(), g[3].toInt(),
                    g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt(),
                )
                val instant = when {
                    g[7].isNotEmpty() -> dateTime.toInstant(ZoneOffset.UTC)
                    tzid != null -> dateTime.atZone(runCatching { ZoneId.of(tzid) }.getOrDefault(zone)).toInstant()
                    else -> dateTime.atZone(zone).toInstant()
                }
                EventTime(instant.toEpochMilli(), allDay = false)
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    // --- Email / phone / SMS / maps -----------------------------------------

    private fun parseMailto(text: String): Content? {
        val rest = text.substring(7)
        val query = parseQuery(rest.substringAfter('?', ""), plusAsSpace = false)
        val to = percentDecode(rest.substringBefore('?')).trim().ifEmpty { query["to"].orEmpty() }
        if (to.isEmpty()) return null
        return Content.Email(to, query["subject"], query["body"])
    }

    private fun parseMatmsg(text: String): Content? {
        val fields = parseFields(text.substring(7))
        val to = fields.first("TO") ?: return null
        return Content.Email(to, fields.first("SUB"), fields.first("BODY"))
    }

    private fun parseSmsTo(text: String): Content? {
        // smsto:NUMBER:BODY
        val rest = text.substring(6)
        val number = rest.substringBefore(':').trim()
        if (number.isEmpty()) return null
        return Content.Sms(number, rest.substringAfter(':', "").ifEmpty { null })
    }

    private fun parseSmsUri(text: String): Content? {
        // sms:NUMBER?body=BODY (also seen: "sms:NUMBER&body=" and "sms:NUMBER;?&body=")
        val rest = text.substringAfter(':')
        val number = percentDecode(rest.split('?', '&', ';').first()).trim()
        if (number.isEmpty()) return null
        val query = rest.substringAfter('?', rest.substringAfter('&', ""))
        return Content.Sms(number, parseQuery(query, plusAsSpace = false)["body"])
    }

    private fun parseGeo(text: String): Content? {
        val rest = text.substring(4)
        val parts = rest.substringBefore('?').substringBefore(';').split(',')
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val query = parseQuery(rest.substringAfter('?', ""), plusAsSpace = true)["q"]
        return Content.Geo(lat, lon, query, text)
    }

    // --- Two-factor ----------------------------------------------------------

    private fun parseOtp(text: String): Content {
        if (text.lowercase().startsWith("otpauth-migration://")) {
            return Content.OneTimePassword(issuer = null, account = null, uri = text)
        }
        // otpauth://totp/Issuer:account?secret=...&issuer=Issuer
        val afterScheme = text.substringAfter("://")
        val label = percentDecode(afterScheme.substringAfter('/', "").substringBefore('?'))
        val query = parseQuery(afterScheme.substringAfter('?', ""), plusAsSpace = false)
        val labelIssuer = label.substringBefore(':', "").trim().ifEmpty { null }
        val account = label.substringAfter(':').trim().ifEmpty { null }
        return Content.OneTimePassword(query["issuer"] ?: labelIssuer, account, text)
    }

    // --- Payments ------------------------------------------------------------

    private fun parseUpi(text: String): Content {
        val query = parseQuery(text.substringAfter('?', ""), plusAsSpace = true)
        val fields = buildList {
            query["pn"]?.let { add(PaymentField.RECIPIENT to it) }
            query["pa"]?.let { add(PaymentField.ACCOUNT to it) }
            query["am"]?.let { add(PaymentField.AMOUNT to listOfNotNull(query["cu"], it).joinToString(" ")) }
            query["tn"]?.let { add(PaymentField.MESSAGE to it) }
        }
        return Content.Payment(PaymentKind.UPI, scheme = null, fields = fields, uri = text)
    }

    private fun parseCrypto(text: String): Content {
        val scheme = text.substringBefore(':').lowercase()
        val rest = text.substringAfter(':').removePrefix("//")
        val query = parseQuery(rest.substringAfter('?', ""), plusAsSpace = true)
        val fields = buildList {
            query["label"]?.let { add(PaymentField.RECIPIENT to it) }
            rest.substringBefore('?').ifEmpty { null }?.let { add(PaymentField.ACCOUNT to it) }
            (query["amount"] ?: query["value"])?.let { add(PaymentField.AMOUNT to it) }
            query["message"]?.let { add(PaymentField.MESSAGE to it) }
        }
        return Content.Payment(PaymentKind.CRYPTO, scheme = scheme, fields = fields, uri = text)
    }

    /** EPC / "GiroCode" SEPA transfer. */
    private fun parseEpc(text: String): Content? {
        val l = text.lines()
        if (l.size < 7) return null
        val fields = buildList {
            l.nonBlank(5)?.let { add(PaymentField.RECIPIENT to it) }
            l.nonBlank(6)?.let { add(PaymentField.ACCOUNT to formatIban(it)) }
            l.nonBlank(4)?.let { add(PaymentField.BIC to it) }
            l.nonBlank(7)?.let { add(PaymentField.AMOUNT to EPC_AMOUNT.replace(it, "$1 $2")) }
            l.nonBlank(9)?.let { add(PaymentField.REFERENCE to it) }
            l.nonBlank(10)?.let { add(PaymentField.MESSAGE to it) }
        }
        return Content.Payment(PaymentKind.EPC, scheme = null, fields = fields, uri = null)
    }

    private fun parseSwissBill(text: String): Content? {
        val l = text.lines()
        if (l.size < 6) return null
        val fields = buildList {
            l.nonBlank(5)?.let { add(PaymentField.RECIPIENT to it) }
            l.nonBlank(3)?.let { add(PaymentField.ACCOUNT to formatIban(it)) }
            l.nonBlank(18)?.let { add(PaymentField.AMOUNT to listOfNotNull(l.nonBlank(19), it).joinToString(" ")) }
            l.nonBlank(28)?.let { add(PaymentField.REFERENCE to it) }
            l.nonBlank(29)?.let { add(PaymentField.MESSAGE to it) }
        }
        return Content.Payment(PaymentKind.SWISS_QR_BILL, scheme = null, fields = fields, uri = null)
    }

    private fun List<String>.nonBlank(index: Int): String? = getOrNull(index)?.trim()?.ifEmpty { null }

    private fun formatIban(iban: String): String = iban.replace(" ", "").chunked(4).joinToString(" ")

    // --- Recovery phrases ----------------------------------------------------

    private fun recoveryPhraseOrNull(text: String): Content? {
        val words = text.split(WHITESPACE)
        if (words.size !in SEED_LENGTHS) return null
        if (!words.all { w -> w.length in 3..8 && w.all { it in 'a'..'z' } }) return null
        // Seed word lists don't contain these, ordinary sentences almost always do.
        if (words.any { it in STOPWORDS }) return null
        return Content.RecoveryPhrase(words.size)
    }

    // --- Shared helpers ------------------------------------------------------

    /**
     * Parses MECARD-style "K:V;K:V;;" bodies (also used by WIFI:, MATMSG:, MEBKM:).
     * Backslash escapes the next character. Keys are upper-cased.
     */
    internal fun parseFields(body: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    current.append(body[i + 1])
                    i += 2
                    continue
                }
                c == ';' -> {
                    key?.let { result += it to unquote(current.toString()) }
                    key = null
                    current.clear()
                }
                c == ':' && key == null -> {
                    key = current.toString().trim().uppercase()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        key?.let { result += it to unquote(current.toString()) }
        return result
    }

    private fun List<Pair<String, String>>.first(key: String): String? =
        firstOrNull { it.first == key }?.second

    private fun List<Pair<String, String>>.all(key: String): List<String> =
        filter { it.first == key }.map { it.second }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value

    internal data class ContentLine(val name: String, val params: Map<String, String>, val value: String)

    /** Parses vCard / iCalendar content lines, including folded continuation lines. */
    internal fun parseContentLines(text: String): List<ContentLine> =
        text.replace(FOLDED_LINE, "").lines().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val head = line.substring(0, colon).split(';')
            // Drop vCard group prefixes like "item1.TEL".
            val name = head.first().substringAfterLast('.').trim().uppercase()
            val params = head.drop(1).associate { p ->
                val k = p.substringBefore('=').trim().uppercase()
                k to p.substringAfter('=', "").trim().removeSurrounding("\"")
            }
            ContentLine(name, params, line.substring(colon + 1))
        }

    /** Splits on [separator] unless it is backslash-escaped. Escapes are kept for [unescapeText]. */
    internal fun splitEscaped(value: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(c).append(value[i + 1])
                i += 2
                continue
            }
            if (c == separator) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        parts += current.toString()
        return parts
    }

    internal fun unescapeText(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                out.append(if (next == 'n' || next == 'N') '\n' else next)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    internal fun parseQuery(query: String, plusAsSpace: Boolean): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = percentDecode(pair.substringBefore('='), plusAsSpace).trim().lowercase()
            val value = percentDecode(pair.substringAfter('=', ""), plusAsSpace)
            if (key.isNotEmpty() && key !in result) result[key] = value
        }
        return result
    }

    /** Decodes %XX escapes as UTF-8. Unlike URLDecoder it leaves '+' alone unless asked. */
    internal fun percentDecode(s: String, plusAsSpace: Boolean = false): String {
        if ('%' !in s && !(plusAsSpace && '+' in s)) return s
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.write(hex)
                    i += 3
                    continue
                }
            }
            if (c == '+' && plusAsSpace) {
                out.write(' '.code)
                i++
                continue
            }
            val cp = s.codePointAt(i)
            out.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
            i += Character.charCount(cp)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL_WITH_AUTHORITY = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S+$")
    private val ICS_TIME = Regex("(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?")
    private val EPC_AMOUNT = Regex("^([A-Z]{3})(\\d.*)$")
    private val FOLDED_LINE = Regex("\r?\n[ \t]")
    private val WHITESPACE = Regex("\\s+")

    private val SEED_LENGTHS = setOf(12, 15, 18, 21, 24)
    private val STOPWORDS = setOf("the", "and", "for", "are", "this", "that", "you", "with", "your", "was", "has")

    /** Schemes that open another app. Shown as links with an "opens another app" note. */
    private val APP_SCHEMES = setOf("market", "spotify", "tg", "whatsapp", "zoommtg", "msteams", "skype", "steam", "magnet")

    private val CRYPTO_SCHEMES = setOf(
        "bitcoin", "bitcoincash", "litecoin", "ethereum", "dogecoin", "monero", "lightning", "solana",
    )
}
