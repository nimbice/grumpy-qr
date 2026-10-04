package io.github.nimbice.grumpyqr.content

/** What a scanned code turned out to be. Parsing never touches the network. */
sealed interface Content {

    data class Link(val report: LinkReport) : Content

    data class Wifi(
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : Content

    data class Contact(
        val name: String?,
        val organization: String?,
        val title: String?,
        val phones: List<String>,
        val emails: List<String>,
        val urls: List<String>,
        val address: String?,
        val note: String?,
    ) : Content

    data class Email(val to: String, val subject: String?, val body: String?) : Content

    data class Phone(val number: String) : Content

    data class Sms(val number: String, val body: String?) : Content

    data class Geo(
        val latitude: Double,
        val longitude: Double,
        val query: String?,
        val uri: String,
    ) : Content

    data class Event(
        val title: String?,
        val start: EventTime?,
        val end: EventTime?,
        val location: String?,
        val description: String?,
    ) : Content

    /** A two-factor (TOTP/HOTP) setup code. Sensitive: never saved to history. */
    data class OneTimePassword(val issuer: String?, val account: String?, val uri: String) : Content

    /** A FIDO passkey hand-off code ("FIDO:/..."). Sensitive: never saved to history. */
    data class Passkey(val uri: String) : Content

    data class Payment(
        val kind: PaymentKind,
        /** Lower-case URI scheme for crypto payments ("bitcoin", "ethereum", ...). */
        val scheme: String?,
        val fields: List<Pair<PaymentField, String>>,
        /** URI a payment app can open, or null when there's no standard handler. */
        val uri: String?,
    ) : Content

    data class Product(val code: String, val isBook: Boolean) : Content

    /** Looks like a crypto wallet recovery phrase. Sensitive: never saved to history. */
    data class RecoveryPhrase(val wordCount: Int) : Content

    data class Text(val text: String) : Content
}

/** Content that should never be written to history. */
val Content.isSensitive: Boolean
    get() = this is Content.OneTimePassword ||
        this is Content.Passkey ||
        this is Content.RecoveryPhrase

enum class WifiSecurity { OPEN, WEP, WPA, WPA3, ENTERPRISE }

enum class PaymentKind { UPI, CRYPTO, EPC, SWISS_QR_BILL }

enum class PaymentField { RECIPIENT, ACCOUNT, BIC, AMOUNT, REFERENCE, MESSAGE }

data class EventTime(val epochMillis: Long, val allDay: Boolean)
