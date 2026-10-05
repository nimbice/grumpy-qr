package io.github.nimbice.grumpyqr.ui

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nimbice.grumpyqr.Actions
import io.github.nimbice.grumpyqr.R

/** Everything Grumpy QR is built on, with the notices their licenses require. */
private data class Component(val name: String, val notice: String, val license: String)

private const val APACHE = "Apache License 2.0"

private val COMPONENTS = listOf(
    Component("zxing-cpp", "Copyright ZXing authors and Axel Waggershauser", APACHE),
    Component("libzueci (part of zxing-cpp)", "Copyright (C) 2022 gitlost", "BSD 3-Clause License"),
    Component(
        "Android Jetpack (AndroidX Core, Activity, Lifecycle, Compose, Material 3, CameraX and their dependencies)",
        "Copyright The Android Open Source Project",
        APACHE,
    ),
    Component("Material Design icons", "Copyright Google LLC", APACHE),
    Component("Kotlin standard library", "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors", APACHE),
    Component("kotlinx.coroutines, kotlinx.serialization, atomicfu", "Copyright JetBrains s.r.o. and contributors", APACHE),
    Component("Guava, failureaccess, ListenableFuture", "Copyright The Guava Authors", APACHE),
    Component("Dagger", "Copyright The Dagger Authors", APACHE),
    Component("Error Prone, J2ObjC and AutoValue annotations", "Copyright Google LLC", APACHE),
    Component("JetBrains Java annotations", "Copyright JetBrains s.r.o.", APACHE),
    Component("JSpecify annotations", "Copyright The JSpecify Authors", APACHE),
    Component("Jakarta Dependency Injection and javax.inject", "Copyright Eclipse Foundation and the JSR-330 Expert Group", APACHE),
    Component(
        "JSR-305 annotations",
        "javax.annotation.concurrent annotations: Copyright (c) 2005 Brian Goetz and Tim Peierls, " +
            "Creative Commons Attribution 2.5 (creativecommons.org/licenses/by/2.5)",
        APACHE,
    ),
    Component("Checker Framework qualifiers", "Copyright 2004-present by the Checker Framework developers", "MIT License"),
)

private enum class LicenseText(@StringRes val title: Int, @RawRes val text: Int) {
    APACHE_2(R.string.license_apache, R.raw.license_apache_2_0),
    BSD_3(R.string.license_bsd3, R.raw.license_bsd_3_clause_libzueci),
    MIT(R.string.license_mit, R.raw.license_mit_checker_framework),
    NOTICES(R.string.license_notices, R.raw.notices),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.url_source)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(Modifier.padding(16.dp)) {
                    LegalText(rawText(R.raw.license_gpl_notice))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { Actions.openLink(context, GPL_URL) }) {
                            Text(stringResource(R.string.licenses_read_gpl))
                        }
                        FilledTonalButton(onClick = { Actions.openLink(context, sourceUrl) }) {
                            Text(stringResource(R.string.about_source))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.trademark_qr_code), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { SectionTitle(R.string.licenses_third_party) }
            items(COMPONENTS) { component ->
                ListItem(
                    headlineContent = { Text(component.name) },
                    supportingContent = { Text("${component.notice}\n${component.license}") },
                )
            }
            item { SectionTitle(R.string.licenses_texts) }
            items(LicenseText.entries) { ExpandableLicense(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun ExpandableLicense(license: LicenseText) {
    var open by rememberSaveable(license) { mutableStateOf(false) }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(license.title)) },
            trailingContent = {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            },
            modifier = Modifier.clickable { open = !open },
        )
        AnimatedVisibility(open) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    LegalText(rawText(license.text))
                }
            }
        }
    }
}

@Composable
private fun LegalText(text: String) {
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Reads a bundled license file, re-flowing its 80-column line breaks into paragraphs that fit a phone. */
@Composable
private fun rawText(@RawRes id: Int): String {
    val resources = LocalResources.current
    return remember(id, resources) {
        resources.openRawResource(id).bufferedReader().use { it.readText() }
            .replace("\r\n", "\n")
            .split(BLANK_LINE)
            .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
            .trim()
    }
}

private val BLANK_LINE = Regex("\n\\s*\n")

private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
