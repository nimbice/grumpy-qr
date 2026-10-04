package io.github.nimbice.grumpyqr

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nimbice.grumpyqr.data.HistoryItem
import io.github.nimbice.grumpyqr.data.HistoryStore
import io.github.nimbice.grumpyqr.data.Settings
import io.github.nimbice.grumpyqr.scan.ImageScanResult
import io.github.nimbice.grumpyqr.scan.ImageScanner
import io.github.nimbice.grumpyqr.scan.RepeatSuppressor
import io.github.nimbice.grumpyqr.scan.Scan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class Screen { SCANNER, HISTORY, SETTINGS }

enum class LaunchMode {
    /** Opened normally: live scanner. */
    NORMAL,

    /** Something was shared to us: show that picture's results, then get out of the way. */
    SHARED_IMAGE,

    /** Another app asked us to scan a code for it (ZXing SCAN intent). */
    SCAN_FOR_RESULT,
}

sealed interface SharedImageState {
    data object Working : SharedImageState
    data class Done(val preview: ImageBitmap?, val found: Boolean) : SharedImageState
    data class Failed(val reason: ImageScanResult.Reason) : SharedImageState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    val settings = Settings(application)
    val history = HistoryStore(application)
    private val imageScanner = ImageScanner(application)

    var mode by mutableStateOf(LaunchMode.NORMAL)
        private set
    var screen by mutableStateOf(Screen.SCANNER)
    var results by mutableStateOf<List<Scan>?>(null)
        private set
    var resultsFromHistory by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var sharedImage by mutableStateOf<SharedImageState?>(null)
        private set

    /** Set by the "Scan a picture" launcher shortcut; the scanner opens the picker once. */
    var pickImageOnStart by mutableStateOf(false)

    /** We ask for the camera at most once per launch on our own. */
    var askedForCamera = false

    /** Remembers codes you've already seen, across screens, so they don't pop up again. */
    val cameraSuppressor = RepeatSuppressor()

    /** The answer for an app that asked us to scan on its behalf. */
    var answerForCaller by mutableStateOf<Scan?>(null)
        private set

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { history.load() }
    }

    fun startScanForResult() {
        mode = LaunchMode.SCAN_FOR_RESULT
    }

    fun onScanned(scans: List<Scan>) {
        if (scans.isEmpty()) return
        if (settings.keepHistory) {
            viewModelScope.launch { scans.forEach { history.add(it) } }
        }
        if (mode == LaunchMode.SCAN_FOR_RESULT) {
            answerForCaller = scans.first()
            return
        }
        resultsFromHistory = false
        results = scans
    }

    fun showFromHistory(item: HistoryItem) {
        resultsFromHistory = true
        results = listOf(item.toScan())
    }

    fun dismissResults() {
        results = null
    }

    /** A picture picked from inside the app (photo picker or file picker). */
    fun scanPickedFile(uri: Uri) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val result = imageScanner.scan(uri)) {
                is ImageScanResult.Found -> {
                    result.preview?.recycle()
                    onScanned(result.scans)
                }
                is ImageScanResult.NothingFound -> {
                    result.preview?.recycle()
                    _messages.send(R.string.message_nothing_found)
                }
                is ImageScanResult.Failed -> _messages.send(result.reason.message)
            }
            busy = false
        }
    }

    /** A picture or PDF shared to us from another app. */
    fun scanSharedFile(uri: Uri, mimeType: String?) {
        mode = LaunchMode.SHARED_IMAGE
        sharedImage = SharedImageState.Working
        viewModelScope.launch {
            sharedImage = when (val result = imageScanner.scan(uri, mimeType)) {
                is ImageScanResult.Found -> {
                    onScanned(result.scans)
                    SharedImageState.Done(result.preview?.asImageBitmap(), found = true)
                }
                is ImageScanResult.NothingFound -> SharedImageState.Done(result.preview?.asImageBitmap(), found = false)
                is ImageScanResult.Failed -> SharedImageState.Failed(result.reason)
            }
        }
    }

    fun deleteFromHistory(id: Long) {
        viewModelScope.launch { history.delete(id) }
    }

    fun restoreToHistory(item: HistoryItem) {
        viewModelScope.launch { history.restore(item) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun exportHistory(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { history.exportCsv(it) }
            }.getOrNull() != null
            _messages.send(if (ok) R.string.message_history_exported else R.string.message_export_failed)
        }
    }

    fun showMessage(@StringRes message: Int) {
        _messages.trySend(message)
    }
}

@get:StringRes
val ImageScanResult.Reason.message: Int
    get() = when (this) {
        ImageScanResult.Reason.UNREADABLE -> R.string.message_unreadable
        ImageScanResult.Reason.PDF_LOCKED -> R.string.message_pdf_locked
        ImageScanResult.Reason.TOO_LARGE -> R.string.message_too_large
        ImageScanResult.Reason.DECODER_UNAVAILABLE -> R.string.message_decoder_unavailable
    }
