package io.github.nimbice.grumpyqr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import io.github.nimbice.grumpyqr.scan.Formats
import io.github.nimbice.grumpyqr.scan.Scan
import io.github.nimbice.grumpyqr.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        Shortcuts.publish(this)

        setContent {
            AppRoot(
                viewModel = viewModel,
                callerName = callerName(),
                onFinish = ::finish,
                onAnswerCaller = ::answerCaller,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                if (uri != null) {
                    viewModel.scanSharedFile(uri, intent.type)
                } else {
                    viewModel.showMessage(R.string.message_unreadable)
                }
            }
            ACTION_ZXING_SCAN -> viewModel.startScanForResult()
            Shortcuts.ACTION_SCAN_PICTURE -> viewModel.pickImageOnStart = true
            Shortcuts.ACTION_HISTORY -> viewModel.screen = Screen.HISTORY
        }
        Shortcuts.reportUsed(this, intent.action)
    }

    /** The app that asked us to scan for it, shown so you always know who gets the result. */
    private fun callerName(): String? {
        if (intent.action != ACTION_ZXING_SCAN) return null
        val pkg = callingActivity?.packageName ?: return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun answerCaller(scan: Scan) {
        val result = Intent(ACTION_ZXING_SCAN)
            .putExtra("SCAN_RESULT", scan.text)
            .putExtra("SCAN_RESULT_FORMAT", Formats.zxingIntentName(scan.format))
        scan.bytes?.let { result.putExtra("SCAN_RESULT_BYTES", it) }
        scan.ecLevel?.let { result.putExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL", it) }
        setResult(RESULT_OK, result)
        finish()
    }

    private companion object {
        const val ACTION_ZXING_SCAN = "com.google.zxing.client.android.SCAN"
    }
}
