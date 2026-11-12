package com.sharek.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val id: Long,
    val sending: Boolean,
    val peer: String,
    val time: Long,
    val status: String,
    val files: List<SavedFile>
)

/** سجل عمليات النقل، محفوظ محليًا في ملف JSON صغير (آخر 200 عملية). */
object History {
    const val OK = "success"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
    const val REJECTED = "rejected"

    private val _items = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val items: StateFlow<List<HistoryEntry>> = _items.asStateFlow()
    private var file: File? = null

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "history.json")
        file = f
        _items.value = load(f)
    }

    @Synchronized
    fun add(entry: HistoryEntry) {
        var id = entry.id
        while (_items.value.any { it.id == id }) id++ // معرّف فريد دائمًا (مفتاح القائمة)
        val e = entry.copy(id = id)
        _items.value = (listOf(e) + _items.value).take(200)
        save()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun load(f: File): List<HistoryEntry> {
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val fa = o.getJSONArray("files")
                HistoryEntry(
                    o.getLong("id"), o.getBoolean("sending"), o.getString("peer"),
                    o.getLong("time"), o.getString("status"),
                    List(fa.length()) { j ->
                        val fo = fa.getJSONObject(j)
                        val u = fo.optString("uri", "")
                        val m = fo.optString("mime", "")
                        SavedFile(
                            fo.getString("name"), fo.getLong("size"),
                            if (u.isEmpty()) null else Uri.parse(u),
                            if (m.isEmpty()) null else m
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val arr = JSONArray()
            for (e in _items.value) {
                val fa = JSONArray()
                for (s in e.files) {
                    fa.put(JSONObject().apply {
                        put("name", s.name)
                        put("size", s.size)
                        put("uri", s.uri?.toString() ?: "")
                        put("mime", s.mime ?: "")
                    })
                }
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("sending", e.sending)
                    put("peer", e.peer)
                    put("time", e.time)
                    put("status", e.status)
                    put("files", fa)
                })
            }
            f.writeText(arr.toString())
        }
    }
}
