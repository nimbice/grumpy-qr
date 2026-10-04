package io.github.nimbice.grumpyqr.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** The handful of settings Grumpy QR has, backed by SharedPreferences. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var _keepHistory by mutableStateOf(prefs.getBoolean(KEEP_HISTORY, true))
    var keepHistory: Boolean
        get() = _keepHistory
        set(value) {
            _keepHistory = value
            prefs.edit { putBoolean(KEEP_HISTORY, value) }
        }

    private var _vibrate by mutableStateOf(prefs.getBoolean(VIBRATE, true))
    var vibrate: Boolean
        get() = _vibrate
        set(value) {
            _vibrate = value
            prefs.edit { putBoolean(VIBRATE, value) }
        }

    private var _linkNotes by mutableStateOf(prefs.getBoolean(LINK_NOTES, true))
    var linkNotes: Boolean
        get() = _linkNotes
        set(value) {
            _linkNotes = value
            prefs.edit { putBoolean(LINK_NOTES, value) }
        }

    /** Set when the user turns down the camera permission, so we never ask on our own again. */
    var cameraDeclined: Boolean
        get() = prefs.getBoolean(CAMERA_DECLINED, false)
        set(value) = prefs.edit { putBoolean(CAMERA_DECLINED, value) }

    private companion object {
        const val KEEP_HISTORY = "keep_history"
        const val VIBRATE = "vibrate"
        const val LINK_NOTES = "link_notes"
        const val CAMERA_DECLINED = "camera_declined"
    }
}
