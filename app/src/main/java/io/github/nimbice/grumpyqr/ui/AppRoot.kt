package io.github.nimbice.grumpyqr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import io.github.nimbice.grumpyqr.AppViewModel
import io.github.nimbice.grumpyqr.LaunchMode
import io.github.nimbice.grumpyqr.Screen
import io.github.nimbice.grumpyqr.scan.Scan
import io.github.nimbice.grumpyqr.ui.theme.GrumpyQRTheme

@Composable
fun AppRoot(
    viewModel: AppViewModel,
    callerName: String?,
    onFinish: () -> Unit,
    onAnswerCaller: (Scan) -> Unit,
) {
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }

    val answer = viewModel.answerForCaller
    LaunchedEffect(answer) {
        if (answer != null) onAnswerCaller(answer)
    }

    GrumpyQRTheme {
        if (viewModel.mode == LaunchMode.SHARED_IMAGE) {
            SharedImageScreen(viewModel = viewModel, snackbar = snackbar, onClose = onFinish)
        } else {
            when (viewModel.screen) {
                Screen.SCANNER -> ScannerScreen(viewModel = viewModel, snackbar = snackbar, callerName = callerName)
                Screen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    snackbar = snackbar,
                    onBack = { viewModel.screen = Screen.SCANNER },
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.screen = Screen.SCANNER },
                )
            }
            BackHandler(enabled = viewModel.screen != Screen.SCANNER) { viewModel.screen = Screen.SCANNER }
        }

        viewModel.results?.let { scans ->
            ResultSheet(
                scans = scans,
                fromHistory = viewModel.resultsFromHistory,
                linkNotes = viewModel.settings.linkNotes,
                onDismiss = {
                    viewModel.dismissResults()
                    if (viewModel.mode == LaunchMode.SHARED_IMAGE) onFinish()
                },
            )
        }
    }
}
