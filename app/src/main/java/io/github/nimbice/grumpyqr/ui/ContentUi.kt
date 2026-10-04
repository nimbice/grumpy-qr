package io.github.nimbice.grumpyqr.ui

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.nimbice.grumpyqr.R
import io.github.nimbice.grumpyqr.content.Content
import io.github.nimbice.grumpyqr.content.LinkNote
import io.github.nimbice.grumpyqr.content.PaymentField
import io.github.nimbice.grumpyqr.content.PaymentKind
import java.util.Locale

val Content.icon: ImageVector
    get() = when (this) {
        is Content.Link -> Icons.Outlined.Link
        is Content.Wifi -> Icons.Outlined.Wifi
        is Content.Contact -> Icons.Outlined.Person
        is Content.Email -> Icons.Outlined.Email
        is Content.Phone -> Icons.Outlined.Call
        is Content.Sms -> Icons.Outlined.Sms
        is Content.Geo -> Icons.Outlined.Place
        is Content.Event -> Icons.Outlined.Event
        is Content.OneTimePassword -> Icons.Outlined.Password
        is Content.Passkey -> Icons.Outlined.Key
        is Content.Payment -> Icons.Outlined.Payments
        is Content.Product -> Icons.Outlined.ShoppingCart
        is Content.RecoveryPhrase -> Icons.Outlined.WarningAmber
        is Content.Text -> Icons.Outlined.TextFields
    }

@Composable
fun Content.label(): String = when (this) {
    is Content.Link -> stringResource(
        when {
            LinkNote.BLOCKED_SCHEME in report.notes -> R.string.type_link_blocked
            LinkNote.OPENS_APP in report.notes -> R.string.type_app_link
            else -> R.string.type_link
        },
    )
    is Content.Wifi -> stringResource(R.string.type_wifi)
    is Content.Contact -> stringResource(R.string.type_contact)
    is Content.Email -> stringResource(R.string.type_email)
    is Content.Phone -> stringResource(R.string.type_phone)
    is Content.Sms -> stringResource(R.string.type_sms)
    is Content.Geo -> stringResource(R.string.type_geo)
    is Content.Event -> stringResource(R.string.type_event)
    is Content.OneTimePassword -> stringResource(R.string.type_otp)
    is Content.Passkey -> stringResource(R.string.type_passkey)
    is Content.Payment -> when (kind) {
        PaymentKind.UPI -> stringResource(R.string.type_payment_upi)
        PaymentKind.CRYPTO -> stringResource(
            R.string.type_payment_crypto,
            scheme.orEmpty().replaceFirstChar(Char::uppercaseChar),
        )
        PaymentKind.EPC -> stringResource(R.string.type_payment_epc)
        PaymentKind.SWISS_QR_BILL -> stringResource(R.string.type_payment_swiss)
    }
    is Content.Product -> stringResource(if (isBook) R.string.type_book else R.string.type_product)
    is Content.RecoveryPhrase -> stringResource(R.string.type_recovery_phrase)
    is Content.Text -> stringResource(R.string.type_text)
}

/** A one-line description for lists. */
fun Content.summary(rawText: String): String = when (this) {
    is Content.Link -> report.site ?: report.url
    is Content.Wifi -> ssid
    is Content.Contact -> name ?: organization ?: phones.firstOrNull() ?: emails.firstOrNull() ?: rawText
    is Content.Email -> to
    is Content.Phone -> number
    is Content.Sms -> number
    is Content.Geo -> query ?: String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)
    is Content.Event -> title ?: rawText.lineSequence().first()
    is Content.OneTimePassword -> listOfNotNull(issuer, account).joinToString(" · ")
    is Content.Passkey -> rawText.take(40)
    is Content.Payment -> fields.firstOrNull { it.first == PaymentField.RECIPIENT }?.second
        ?: fields.firstOrNull()?.second ?: rawText
    is Content.Product -> code
    is Content.RecoveryPhrase -> "•••"
    is Content.Text -> rawText.trim().lineSequence().first().take(120)
}

/** "Just now", "5 minutes ago", "Yesterday"... */
@Composable
fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.just_now)
    return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
fun PaymentField.label(): String = stringResource(
    when (this) {
        PaymentField.RECIPIENT -> R.string.field_recipient
        PaymentField.ACCOUNT -> R.string.field_account
        PaymentField.BIC -> R.string.field_bic
        PaymentField.AMOUNT -> R.string.field_amount
        PaymentField.REFERENCE -> R.string.field_reference
        PaymentField.MESSAGE -> R.string.field_message
    },
)
