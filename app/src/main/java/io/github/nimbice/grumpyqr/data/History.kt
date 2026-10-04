package io.github.nimbice.grumpyqr.data

import android.content.Context
import android.util.AtomicFile
import io.github.nimbice.grumpyqr.content.isSensitive
import io.github.nimbice.grumpyqr.scan.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant

data class HistoryItem(val id: Long, val text: String, val format: String, val time: Long) {
    fun toScan() = Scan(text = text, format = format, time = time)
}

/**
 * Scan history, kept in a single JSON file in the app's private storage.
 * It never leaves the phone: backups are disabled for the whole app.
 */
class HistoryStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "history.json"))
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _items.value = read() }
    }

    /** Saves a scan, unless it's sensitive. A repeat scan just moves to the top. */
    suspend fun add(scan: Scan) {
        if (scan.content.isSensitive) return
        update { items ->
            val id = maxOf(scan.time, (items.maxOfOrNull { it.id } ?: 0L) + 1)
            listOf(HistoryItem(id, scan.text, scan.format, scan.time)) +
                items.filterNot { it.text == scan.text && it.format == scan.format }.take(MAX_ITEMS - 1)
        }
    }

    suspend fun delete(id: Long) = update { items -> items.filterNot { it.id == id } }

    /** Puts a deleted item back where it was (undo). */
    suspend fun restore(item: HistoryItem) = update { items ->
        (items.filterNot { it.id == item.id } + item).sortedByDescending { it.id }
    }

    suspend fun clear() = update { emptyList() }

    /** Writes the history as CSV (time, format, text) for the user to keep. */
    suspend fun exportCsv(output: OutputStream) = withContext(Dispatchers.IO) {
        val csv = buildString {
            append("time,format,text\r\n")
            for (item in _items.value) {
                append(Instant.ofEpochMilli(item.time)).append(',')
                append(item.format).append(',')
                append('"').append(item.text.replace("\"", "\"\"")).append('"').append("\r\n")
            }
        }
        output.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
    }

    private suspend fun update(transform: (List<HistoryItem>) -> List<HistoryItem>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val before = _items.value
                val after = transform(before)
                _items.value = after
                if (after !== before) write(after)
            }
        }

    private fun read(): List<HistoryItem> = try {
        val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            HistoryItem(o.getLong("id"), o.getString("text"), o.getString("format"), o.getLong("time"))
        }
    } catch (_: Exception) {
        emptyList() // Missing or unreadable file: start fresh.
    }

    private fun write(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("format", it.format)
                    .put("time", it.time),
            )
        }
        val stream = file.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private companion object {
        const val MAX_ITEMS = 1000
    }
}
