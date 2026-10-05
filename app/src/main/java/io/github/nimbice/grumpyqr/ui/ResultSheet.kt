package io.github.nimbice.grumpyqr.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nimbice.grumpyqr.Actions
import io.github.nimbice.grumpyqr.R
import io.github.nimbice.grumpyqr.content.Content
import io.github.nimbice.grumpyqr.content.EventTime
import io.github.nimbice.grumpyqr.content.LinkNote
import io.github.nimbice.grumpyqr.content.LinkReport
import io.github.nimbice.grumpyqr.content.PaymentField
import io.github.nimbice.grumpyqr.content.WifiSecurity
import io.github.nimbice.grumpyqr.content.isSensitive
import io.github.nimbice.grumpyqr.scan.Scan
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    scans: List<Scan>,
    fromHistory: Boolean,
    linkNotes: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            if (scans.size > 1) {
                Text(
                    pluralStringResource(R.plurals.found_codes, scans.size, scans.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
            }
            scans.forEachIndexed { index, scan ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 24.dp))
                ResultCard(scan, fromHistory, linkNotes)
            }
        }
    }
}

private class ActionSpec(
    val label: String,
    val icon: ImageVector,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(scan: Scan, fromHistory: Boolean, linkNotes: Boolean) {
    val context = LocalContext.current
    val content = scan.content
    val actions = mutableListOf<ActionSpec>()

    val copyLabel = stringResource(R.string.action_copy)
    val shareLabel = stringResource(R.string.action_share)
    val copyText = ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, scan.text) }
    val shareText = ActionSpec(shareLabel, Icons.Outlined.Share) { Actions.share(context, scan.text).orComplain(context) }

    Header(scan, content, fromHistory)
    Spacer(Modifier.height(16.dp))

    when (content) {
        is Content.Link -> {
            LinkBody(content.report, linkNotes)
            content.report.openUrl?.let { url ->
                val opensApp = LinkNote.OPENS_APP in content.report.notes
                actions += ActionSpec(
                    stringResource(if (opensApp) R.string.action_open_in_app else R.string.action_open_link),
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    primary = true,
                ) { Actions.openLink(context, url).orComplain(context) }
            }
            actions += copyText
            actions += shareText
        }

        is Content.Wifi -> {
            LabeledValue(stringResource(R.string.field_network), content.ssid, emphasized = true)
            LabeledValue(stringResource(R.string.field_security), wifiSecurityLabel(content))
            content.password?.let { SecretValue(stringResource(R.string.field_password), it) }
            val canConnect = Actions.canConnectWifi(content)
            if (!canConnect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Notice(stringResource(R.string.wifi_manual_setup), warning = false)
            }
            val copyPasswordThenSettings = {
                content.password?.let { copy(context, it, sensitive = true, quiet = true) }
                Actions.openWifiSettings(context).orComplain(context)
                if (content.password != null) toast(context, R.string.message_wifi_password_copied)
            }
            actions += if (canConnect) {
                ActionSpec(stringResource(R.string.action_connect), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                    if (!Actions.connectWifi(context, content)) copyPasswordThenSettings()
                }
            } else {
                ActionSpec(stringResource(R.string.action_wifi_settings), Icons.Outlined.Settings, primary = true) {
                    copyPasswordThenSettings()
                }
            }
            content.password?.let { password ->
                actions += ActionSpec(stringResource(R.string.action_copy_password), Icons.Outlined.ContentCopy) {
                    copy(context, password, sensitive = true)
                }
            }
        }

        is Content.Contact -> {
            content.name?.let { LabeledValue(stringResource(R.string.field_name), it, emphasized = true) }
            listOfNotNull(content.title, content.organization).joinToString(", ").ifEmpty { null }
                ?.let { LabeledValue(stringResource(R.string.field_organization), it) }
            content.phones.forEach { LabeledValue(stringResource(R.string.field_phone), it) }
            content.emails.forEach { LabeledValue(stringResource(R.string.field_email), it) }
            content.urls.forEach { LabeledValue(stringResource(R.string.field_website), it) }
            content.address?.let { LabeledValue(stringResource(R.string.field_address), it) }
            content.note?.let { LabeledValue(stringResource(R.string.field_note), it) }
            actions += ActionSpec(stringResource(R.string.action_add_contact), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.addContact(context, content).orComplain(context)
            }
            actions += copyText
            actions += shareText
        }

        is Content.Email -> {
            LabeledValue(stringResource(R.string.field_to), content.to, emphasized = true)
            content.subject?.let { LabeledValue(stringResource(R.string.field_subject), it) }
            content.body?.let { LabeledValue(stringResource(R.string.field_message), it) }
            actions += ActionSpec(stringResource(R.string.action_write_email), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.email(context, content).orComplain(context)
            }
            actions += ActionSpec(stringResource(R.string.action_copy_address), Icons.Outlined.ContentCopy) {
                copy(context, content.to)
            }
        }

        is Content.Phone -> {
            LabeledValue(stringResource(R.string.field_phone), content.number, emphasized = true)
            actions += ActionSpec(stringResource(R.string.action_dial), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.dial(context, content.number).orComplain(context)
            }
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, content.number) }
        }

        is Content.Sms -> {
            LabeledValue(stringResource(R.string.field_to), content.number, emphasized = true)
            content.body?.let { LabeledValue(stringResource(R.string.field_message), it) }
            actions += ActionSpec(stringResource(R.string.action_write_message), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.sms(context, content).orComplain(context)
            }
            actions += copyText
        }

        is Content.Geo -> {
            val coordinates = String.format(Locale.ROOT, "%.5f, %.5f", content.latitude, content.longitude)
            content.query?.let { LabeledValue(stringResource(R.string.field_place), it, emphasized = true) }
            LabeledValue(stringResource(R.string.field_coordinates), coordinates, mono = true)
            actions += ActionSpec(stringResource(R.string.action_open_map), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.map(context, content).orComplain(context)
            }
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, coordinates) }
        }

        is Content.Event -> {
            content.title?.let { LabeledValue(stringResource(R.string.field_event), it, emphasized = true) }
            content.start?.let { start ->
                val whenText = listOfNotNull(formatEventTime(start), content.end?.let(::formatEventTime)).joinToString(" – ")
                LabeledValue(stringResource(R.string.field_when), whenText)
            }
            content.location?.let { LabeledValue(stringResource(R.string.field_where), it) }
            content.description?.let { LabeledValue(stringResource(R.string.field_details), it) }
            actions += ActionSpec(stringResource(R.string.action_add_event), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.addEvent(context, content).orComplain(context)
            }
            actions += copyText
        }

        is Content.OneTimePassword -> {
            content.issuer?.let { LabeledValue(stringResource(R.string.field_service), it, emphasized = true) }
            content.account?.let { LabeledValue(stringResource(R.string.field_account_name), it) }
            Notice(stringResource(R.string.otp_notice), warning = true)
            actions += ActionSpec(stringResource(R.string.action_open_authenticator), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                Actions.openInApp(context, content.uri).orComplain(context)
            }
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, content.uri, sensitive = true) }
        }

        is Content.Passkey -> {
            Notice(stringResource(R.string.passkey_notice), warning = true)
            actions += ActionSpec(stringResource(R.string.action_continue), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                if (!Actions.openInApp(context, content.uri)) toast(context, R.string.message_passkey_unsupported)
            }
        }

        is Content.Payment -> {
            content.fields.forEach { (field, value) ->
                LabeledValue(field.label(), value, emphasized = field == PaymentField.AMOUNT, mono = field == PaymentField.ACCOUNT)
            }
            Notice(stringResource(R.string.payment_notice), warning = true)
            content.uri?.let { uri ->
                actions += ActionSpec(stringResource(R.string.action_open_payment_app), Icons.AutoMirrored.Outlined.OpenInNew, primary = true) {
                    Actions.openInApp(context, uri).orComplain(context)
                }
            }
            content.fields.firstOrNull { it.first == PaymentField.ACCOUNT }?.let { (_, account) ->
                actions += ActionSpec(stringResource(R.string.action_copy_account), Icons.Outlined.ContentCopy) {
                    copy(context, account.replace(" ", ""))
                }
            }
            actions += copyText
        }

        is Content.Product -> {
            LabeledValue(stringResource(R.string.field_number), content.code, emphasized = true, mono = true)
            actions += ActionSpec(stringResource(R.string.action_search_web), Icons.Outlined.Search, primary = true) {
                Actions.webSearch(context, content.code).orComplain(context)
            }
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, content.code) }
        }

        is Content.RecoveryPhrase -> {
            Notice(
                pluralStringResource(R.plurals.recovery_phrase_notice, content.wordCount, content.wordCount),
                warning = true,
            )
            SecretValue(stringResource(R.string.field_words), scan.text.trim())
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy) { copy(context, scan.text.trim(), sensitive = true) }
        }

        is Content.Text -> {
            LongText(scan.text)
            actions += ActionSpec(copyLabel, Icons.Outlined.ContentCopy, primary = true) { copy(context, scan.text) }
            actions += shareText
            actions += ActionSpec(stringResource(R.string.action_search_web), Icons.Outlined.Search) {
                Actions.webSearch(context, scan.text.trim().take(500)).orComplain(context)
            }
        }
    }

    // Always show exactly what the code says, unless the card already shows it word for word.
    val shownVerbatim = when (content) {
        is Content.Text -> scan.text
        is Content.Link -> content.report.url
        is Content.Product -> content.code
        is Content.RecoveryPhrase -> scan.text
        else -> null
    }
    val showContents = shownVerbatim?.trim() != scan.text.trim()
    // Mask the contents whenever the card masks something, e.g. a Wi-Fi password.
    val masked = content.isSensitive || (content is Content.Wifi && content.password != null)
    if (showContents) CodeContents(scan.text, secret = masked)

    Spacer(Modifier.height(16.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (action in actions) {
            if (action.primary) {
                Button(onClick = action.onClick) { ButtonContent(action) }
            } else {
                FilledTonalButton(onClick = action.onClick) { ButtonContent(action) }
            }
        }
    }

    if (!content.isSensitive) Details(scan, includeRaw = !showContents)
}

/**
 * Exactly what the code says. Hidden characters are made visible, and
 * secrets (two-factor and passkey codes) stay masked until you ask.
 */
@Composable
private fun CodeContents(text: String, secret: Boolean) {
    var shown by rememberSaveable { mutableStateOf(!secret) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = text.lines().size > 8 || text.length > 400

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.code_contents),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (secret) {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (shown) R.string.hide else R.string.show),
                )
            }
        }
    }
    if (!secret) Spacer(Modifier.height(4.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = if (shown) showInvisibleCharacters(text, markLineBreaks = false) else "•".repeat(minOf(text.length, 24)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = if (long && !expanded) 8 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
    if (long && shown) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.show_less else R.string.show_all))
        }
    }
}

@Composable
private fun ButtonContent(action: ActionSpec) {
    Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(action.label)
}

@Composable
private fun Header(scan: Scan, content: Content, fromHistory: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                content.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(content.label(), style = MaterialTheme.typography.titleLarge)
            val subtitle = if (fromHistory) {
                scan.formatLabel + " · " + relativeTime(scan.time)
            } else {
                scan.formatLabel
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinkBody(report: LinkReport, showNotes: Boolean) {
    report.site?.let { site ->
        Text(
            stringResource(R.string.link_goes_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(site, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
    }
    SelectionContainer {
        Text(
            showInvisibleCharacters(report.url),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
    report.lookalikeHost?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.link_looks_like, it),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    val notes = if (showNotes) report.notes else report.notes.filter { it == LinkNote.BLOCKED_SCHEME }
    if (notes.isNotEmpty()) Spacer(Modifier.height(12.dp))
    for (note in notes) {
        val warning = note in setOf(LinkNote.BLOCKED_SCHEME, LinkNote.HIDDEN_USERNAME, LinkNote.LOOKALIKE_CHARACTERS)
        val text = when (note) {
            LinkNote.BLOCKED_SCHEME -> stringResource(R.string.note_blocked_scheme, report.scheme)
            LinkNote.OPENS_APP -> stringResource(R.string.note_opens_app, report.scheme)
            LinkNote.HIDDEN_USERNAME -> stringResource(R.string.note_hidden_username, report.host.orEmpty())
            LinkNote.LOOKALIKE_CHARACTERS -> stringResource(R.string.note_lookalike)
            LinkNote.IP_ADDRESS -> stringResource(R.string.note_ip_address)
            LinkNote.NOT_ENCRYPTED -> stringResource(R.string.note_not_encrypted)
            LinkNote.SHORTENER -> stringResource(R.string.note_shortener)
            LinkNote.UNUSUAL_PORT -> stringResource(R.string.note_unusual_port)
        }
        Notice(text, warning)
    }
}

@Composable
private fun LongText(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = text.length > 600 || text.lines().size > 12
    SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (long && !expanded) 12 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (long) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.show_less else R.string.show_all))
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, emphasized: Boolean = false, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value,
                style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

/** A value that stays hidden until you ask, for passwords and the like. */
@Composable
private fun SecretValue(label: String, value: String) {
    var shown by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    if (shown) value else "•".repeat(10),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        IconButton(onClick = { shown = !shown }) {
            Icon(
                if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(if (shown) R.string.hide else R.string.show),
            )
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (warning) colors.errorContainer else colors.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(
                if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (warning) colors.onErrorContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (warning) colors.onErrorContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Details(scan: Scan, includeRaw: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { open = !open }) {
        Text(stringResource(R.string.details))
        Spacer(Modifier.width(4.dp))
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
    }
    AnimatedVisibility(open) {
        Column {
            DetailRow(R.string.detail_format, scan.formatLabel)
            scan.ecLevel?.takeIf { it.isNotBlank() }?.let { DetailRow(R.string.detail_error_correction, it) }
            DetailRow(R.string.detail_characters, scan.text.length.toString())
            scan.bytes?.let { DetailRow(R.string.detail_bytes, it.size.toString()) }
            DetailRow(
                R.string.detail_scanned,
                Instant.ofEpochMilli(scan.time).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)),
            )
            if (includeRaw) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.detail_raw), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        showInvisibleCharacters(scan.text),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            scan.bytes?.let { bytes ->
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.detail_hex), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(hexDump(bytes), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(@StringRes label: Int, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun wifiSecurityLabel(wifi: Content.Wifi): String {
    val security = stringResource(
        when (wifi.security) {
            WifiSecurity.OPEN -> R.string.wifi_open
            WifiSecurity.WEP -> R.string.wifi_wep
            WifiSecurity.WPA -> R.string.wifi_wpa
            WifiSecurity.WPA3 -> R.string.wifi_wpa3
            WifiSecurity.ENTERPRISE -> R.string.wifi_enterprise
        },
    )
    return if (wifi.hidden) "$security · ${stringResource(R.string.wifi_hidden)}" else security
}

private fun formatEventTime(time: EventTime): String {
    val zoned = Instant.ofEpochMilli(time.epochMillis).atZone(ZoneId.systemDefault())
    return if (time.allDay) {
        zoned.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    } else {
        zoned.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }
}

/**
 * Makes control characters, zero-width characters and right-to-left
 * overrides visible. They can be used to make a link look like it goes
 * somewhere it doesn't. Line breaks and tabs get markers too, unless
 * [markLineBreaks] is false.
 */
internal fun showInvisibleCharacters(text: String, markLineBreaks: Boolean = true): String = buildString {
    for (c in text) {
        when {
            c == '\n' -> append(if (markLineBreaks) "↵\n" else "\n")
            c == '\r' -> if (markLineBreaks) append("␍")
            c == '\t' -> append(if (markLineBreaks) "⇥" else "\t")
            c.code < 0x20 || c.code in 0x7F..0x9F || c.code in 0x200B..0x200F || c.code in 0x202A..0x202E ||
                c.code in 0x2066..0x2069 || c.code == 0x2028 || c.code == 0x2029 || c.code == 0xFEFF ->
                append(String.format(Locale.ROOT, "\\u%04X", c.code))
            else -> append(c)
        }
    }
}

private fun hexDump(bytes: ByteArray): String = bytes.take(1024).chunked(16).mapIndexed { row, chunk ->
    String.format(Locale.ROOT, "%04x  ", row * 16) + chunk.joinToString(" ") { String.format(Locale.ROOT, "%02x", it) }
}.joinToString("\n") + if (bytes.size > 1024) "\n…" else ""

private fun copy(context: Context, text: String, sensitive: Boolean = false, quiet: Boolean = false) {
    Actions.copy(context, context.getString(R.string.app_name), text, sensitive)
    // Android 13+ shows its own confirmation when something is copied.
    if (!quiet && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(context, R.string.message_copied)
}

/** The sheet sits above the snackbar, so it uses toasts for quick feedback. */
private fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun Boolean.orComplain(context: Context) {
    if (!this) toast(context, R.string.message_no_app)
}
