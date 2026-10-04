package io.github.nimbice.grumpyqr

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.github.nimbice.grumpyqr.content.Content
import io.github.nimbice.grumpyqr.content.WifiSecurity

/**
 * Everything Grumpy QR "does" with a code goes through a standard system
 * intent, so the right app (browser, dialer, contacts, calendar, ...) takes
 * over. That's why Grumpy QR needs no contacts, calendar, phone or network
 * permissions.
 *
 * Every function returns false if no app on the phone could handle it.
 */
object Actions {

    /**
     * Opens a link the way a browser would. CATEGORY_BROWSABLE limits the
     * hand-off to apps that expect links from untrusted web pages, which is
     * exactly what a code on a random poster is.
     */
    fun openLink(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))

    /**
     * Hands a URI we've recognised (two-factor setup, payment request, passkey)
     * to whichever app registered for that scheme.
     */
    fun openInApp(context: Context, uri: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, uri.toUri()))

    fun copy(context: Context, label: String, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            // Keeps passwords out of the clipboard preview and keyboard suggestions.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(
                    if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                    true,
                )
            }
        }
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
    }

    fun share(context: Context, text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, null))
    }

    fun webSearch(context: Context, query: String): Boolean =
        start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            openLink(context, "https://duckduckgo.com/?q=" + Uri.encode(query))

    fun email(context: Context, email: Content.Email): Boolean {
        val uri = buildString {
            append("mailto:").append(Uri.encode(email.to, "@,+"))
            val params = listOfNotNull(
                email.subject?.let { "subject=" + Uri.encode(it) },
                email.body?.let { "body=" + Uri.encode(it) },
            )
            if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
        }
        val intent = Intent(Intent.ACTION_SENDTO, uri.toUri()).apply {
            email.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            email.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
        return start(context, intent)
    }

    /** Opens the dialer with the number filled in. It never places the call itself. */
    fun dial(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** Opens the messaging app with a draft. It never sends anything itself. */
    fun sms(context: Context, sms: Content.Sms): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", sms.number, null))
        sms.body?.let { intent.putExtra("sms_body", it) }
        return start(context, intent)
    }

    fun map(context: Context, geo: Content.Geo): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, geo.uri.toUri()))

    fun addContact(context: Context, contact: Content.Contact): Boolean {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            contact.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
            contact.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            contact.title?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
            contact.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
            val phoneKeys = listOf(
                ContactsContract.Intents.Insert.PHONE,
                ContactsContract.Intents.Insert.SECONDARY_PHONE,
                ContactsContract.Intents.Insert.TERTIARY_PHONE,
            )
            contact.phones.zip(phoneKeys).forEach { (phone, key) -> putExtra(key, phone) }
            val emailKeys = listOf(
                ContactsContract.Intents.Insert.EMAIL,
                ContactsContract.Intents.Insert.SECONDARY_EMAIL,
                ContactsContract.Intents.Insert.TERTIARY_EMAIL,
            )
            contact.emails.zip(emailKeys).forEach { (email, key) -> putExtra(key, email) }
            val notes = listOfNotNull(contact.note, contact.urls.joinToString("\n").ifEmpty { null })
            if (notes.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.NOTES, notes.joinToString("\n"))
        }
        return start(context, intent)
    }

    fun addEvent(context: Context, event: Content.Event): Boolean {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            event.title?.let { putExtra(CalendarContract.Events.TITLE, it) }
            event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            event.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            event.start?.let {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.epochMillis)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, it.allDay)
            }
            event.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.epochMillis) }
        }
        return start(context, intent)
    }

    /** Whether [connectWifi] can offer a one-tap "connect" for this network. */
    fun canConnectWifi(wifi: Content.Wifi): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            wifi.security in setOf(WifiSecurity.OPEN, WifiSecurity.WPA, WifiSecurity.WPA3)

    /**
     * Shows the system "Save this network?" dialog (Android 11+). No location
     * or Wi-Fi permission involved: the user confirms in a system screen.
     * Returns false on older Android versions.
     */
    fun connectWifi(context: Context, wifi: Content.Wifi): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid)
        try {
            when (wifi.security) {
                WifiSecurity.OPEN -> Unit
                WifiSecurity.WPA -> builder.setWpa2Passphrase(wifi.password.orEmpty())
                WifiSecurity.WPA3 -> builder.setWpa3Passphrase(wifi.password.orEmpty())
                else -> return false
            }
            if (wifi.hidden) builder.setIsHiddenSsid(true)
            val suggestion = builder.build()
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
            return start(context, intent)
        } catch (_: IllegalArgumentException) {
            return false // e.g. a password that isn't a valid WPA passphrase
        } catch (_: IllegalStateException) {
            return false
        }
    }

    fun openWifiSettings(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            start(context, Intent(Settings.Panel.ACTION_WIFI)) || start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        } else {
            start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        }

    fun openAppSettings(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        // Deliberately no resolveActivity() check first: with Android 11+ package
        // visibility rules it reports "nothing can handle this" even when something can.
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
