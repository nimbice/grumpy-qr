package io.github.nimbice.grumpyqr.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nimbice.grumpyqr.Actions
import io.github.nimbice.grumpyqr.AppViewModel
import io.github.nimbice.grumpyqr.BuildConfig
import io.github.nimbice.grumpyqr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val settings = viewModel.settings
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.url_source)
    val privacyUrl = stringResource(R.string.url_privacy)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchRow(R.string.setting_history, R.string.setting_history_desc, settings.keepHistory) {
                settings.keepHistory = it
            }
            SwitchRow(R.string.setting_link_notes, R.string.setting_link_notes_desc, settings.linkNotes) {
                settings.linkNotes = it
            }
            SwitchRow(R.string.setting_vibrate, R.string.setting_vibrate_desc, settings.vibrate) {
                settings.vibrate = it
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.about_heading, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_why), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.about_access_heading), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_access), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.about_signoff),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinkRow(Icons.Outlined.Code, R.string.about_source, R.string.about_source_desc) {
                Actions.openLink(context, sourceUrl)
            }
            LinkRow(Icons.Outlined.Description, R.string.about_privacy, R.string.about_privacy_desc) {
                Actions.openLink(context, privacyUrl)
            }
            LinkRow(Icons.Outlined.Gavel, R.string.about_licenses, R.string.about_licenses_desc, onOpenLicenses)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun LinkRow(icon: ImageVector, @StringRes title: Int, @StringRes description: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
