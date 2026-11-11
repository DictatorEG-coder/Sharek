package com.sharek.app

import android.net.Uri
import android.webkit.MimeTypeMap

/** ثوابت البروتوكول والنماذج المشتركة. */
object Proto {
    const val PORT = 8988
    const val MAGIC = "SHAREK1"
    const val SERVICE_TYPE = "_sharek._tcp."
    const val BUF = 64 * 1024

    /** ينظّف اسم الملف القادم من الشبكة: لا مسارات ولا رموز خطرة. */
    fun cleanName(raw: String): String {
        var n = raw.substringAfterLast('/').substringAfterLast('\\')
        n = n.filter { it >= ' ' && it !in "<>:\"|?*" }.trim().trimStart('.')
        if (n.length > 150) n = n.take(150)
        return n.ifEmpty { "file" }
    }

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}

data class FileMeta(val name: String, val size: Long)

/** ملف مختار للإرسال. */
data class OutFile(val uri: Uri, val name: String, val size: Long)

/** ملف تم حفظه (استلام) أو إرساله (uri = null). */
data class SavedFile(val name: String, val size: Long, val uri: Uri?, val mime: String?)

data class Peer(val name: String, val host: String, val port: Int)

sealed interface UiState {
    data object Idle : UiState
    data class Connecting(val message: String) : UiState
    data class Listening(val deviceName: String, val addresses: List<String>) : UiState
    data class Incoming(val sender: String, val files: List<FileMeta>) : UiState
    data class Progress(
        val sending: Boolean,
        val fileName: String,
        val index: Int,
        val count: Int,
        val fileDone: Long,
        val fileTotal: Long,
        val totalDone: Long,
        val totalAll: Long,
        val speed: Long
    ) : UiState
    data class Done(val sending: Boolean, val files: List<SavedFile>) : UiState
    data class Failed(val message: String) : UiState
}

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}
