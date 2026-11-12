package com.sharek.app

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream

/** حفظ الملفات المستلمة في Download/Sharek عبر MediaStore (دون صلاحيات تخزين). */
object FileStore {
    private const val DIR = "Download/Sharek"
    private val collection: Uri get() = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    class Pending(val uri: Uri, val out: OutputStream, val displayName: String)

    fun create(ctx: Context, name: String, mime: String): Pending {
        val resolver = ctx.contentResolver
        val unique = uniqueName(resolver, name) // لا نكتب فوق ملف موجود أبدًا
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, unique)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("تعذّر إنشاء الملف في مجلد Sharek")
        val out = try {
            resolver.openOutputStream(uri) ?: throw IOException("تعذّر فتح الملف للكتابة")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (e is IOException) e else IOException("تعذّر فتح الملف للكتابة")
        }
        return Pending(uri, out, unique)
    }

    fun commit(ctx: Context, p: Pending) {
        p.out.flush()
        p.out.close()
        val v = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        ctx.contentResolver.update(p.uri, v, null, null)
    }

    fun discard(ctx: Context, p: Pending) {
        runCatching { p.out.close() }
        runCatching { ctx.contentResolver.delete(p.uri, null, null) }
    }

    private fun exists(resolver: ContentResolver, name: String): Boolean {
        val sel = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.RELATIVE_PATH} IN (?, ?)"
        resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID), sel,
            arrayOf(name, "$DIR/", DIR), null
        )?.use { return it.count > 0 }
        return false
    }

    private fun uniqueName(resolver: ContentResolver, name: String): String {
        if (!exists(resolver, name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val cand = "$base ($n)$ext"
            if (!exists(resolver, cand)) return cand
            n++
        }
    }

    /** قراءة اسم وحجم ملف اختاره المستخدم من منتقي الملفات. */
    fun describe(ctx: Context, uri: Uri): OutFile? {
        if (uri.scheme == "file") { // ملفات APK للتطبيقات
            val f = java.io.File(uri.path ?: return null)
            return if (f.isFile) OutFile(uri, Proto.cleanName(f.name), f.length()) else null
        }
        var name = "file"
        var size = -1L
        ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size < 0) {
            size = runCatching {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }
        if (size < 0) return null
        return OutFile(uri, Proto.cleanName(name), size)
    }

    /** هل ما زال الملف موجودًا ويمكن قراءته؟ */
    fun readable(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    fun open(ctx: Context, f: SavedFile) {
        val uri = f.uri ?: return
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, f.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "لا يوجد تطبيق يفتح هذا الملف", Toast.LENGTH_LONG).show()
        }
    }
}
