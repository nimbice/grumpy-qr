package io.github.nimbice.grumpyqr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nimbice.grumpyqr.AppViewModel
import io.github.nimbice.grumpyqr.R
import io.github.nimbice.grumpyqr.SharedImageState
import io.github.nimbice.grumpyqr.message

/** Shown when a picture or PDF is shared to Grumpy QR from another app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedImageScreen(viewModel: AppViewModel, snackbar: SnackbarHostState, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shared_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val state = viewModel.sharedImage) {
                null, SharedImageState.Working -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.looking_for_codes), style = MaterialTheme.typography.bodyLarge)
                }
                is SharedImageState.Done -> {
                    state.preview?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.shared_preview_description),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    if (!state.found) {
                        Spacer(Modifier.height(24.dp))
                        Message(
                            title = stringResource(R.string.nothing_found_title),
                            body = stringResource(R.string.nothing_found_body),
                            warning = false,
                            onClose = onClose,
                        )
                    }
                }
                is SharedImageState.Failed -> Message(
                    title = stringResource(R.string.shared_failed_title),
                    body = stringResource(state.reason.message),
                    warning = true,
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, warning: Boolean, onClose: () -> Unit) {
    Icon(
        if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.SearchOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onClose) { Text(stringResource(R.string.close)) }
}
