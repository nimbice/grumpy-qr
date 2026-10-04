package io.github.nimbice.grumpyqr

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Long-press launcher shortcuts. They're published at runtime rather than in
 * XML so they keep working for debug builds, which have a different package name.
 */
object Shortcuts {
    const val ACTION_SCAN_PICTURE = "io.github.nimbice.grumpyqr.action.SCAN_PICTURE"
    const val ACTION_HISTORY = "io.github.nimbice.grumpyqr.action.HISTORY"
    private const val ID_SCAN_PICTURE = "scan_picture"
    private const val ID_HISTORY = "history"

    fun publish(context: Context) {
        if (ShortcutManagerCompat.getDynamicShortcuts(context).size == 2) return
        val shortcuts = listOf(
            shortcut(context, ID_SCAN_PICTURE, R.string.shortcut_scan_picture, R.drawable.ic_shortcut_picture, ACTION_SCAN_PICTURE),
            shortcut(context, ID_HISTORY, R.string.shortcut_history, R.drawable.ic_shortcut_history, ACTION_HISTORY),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    /** Lets the launcher rank shortcuts by how often they're actually used. */
    fun reportUsed(context: Context, action: String?) {
        val id = when (action) {
            ACTION_SCAN_PICTURE -> ID_SCAN_PICTURE
            ACTION_HISTORY -> ID_HISTORY
            else -> return
        }
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }

    private fun shortcut(context: Context, id: String, label: Int, icon: Int, action: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action))
            .build()
}
