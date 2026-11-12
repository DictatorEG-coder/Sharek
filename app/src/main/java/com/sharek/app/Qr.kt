package com.sharek.app

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

data class QrTarget(val host: String, val port: Int, val name: String)

object Qr {
    private const val PREFIX = "sharek://"

    /** الرمز يحمل العنوان والمنفذ واسم الجهاز؛ الرموز القديمة (بلا اسم) ما زالت مقبولة. */
    fun payload(host: String, port: Int, name: String) =
        "$PREFIX$host:$port?n=${Uri.encode(name)}"

    fun parse(raw: String?): QrTarget? {
        val t = raw?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        val body = t.removePrefix(PREFIX)
        val hp = body.substringBefore('?')
        val query = body.substringAfter('?', "")
        val host = hp.substringBefore(':')
        val port = hp.substringAfter(':', "").toIntOrNull() ?: Proto.PORT
        if (host.isEmpty() || port !in 1..65535) return null
        val name = query.split('&').firstOrNull { it.startsWith("n=") }
            ?.removePrefix("n=")?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() } ?: host
        return QrTarget(host, port, name)
    }

    fun bitmap(text: String, size: Int = 512): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (m.get(x, y)) AColor.BLACK else AColor.WHITE)
        }
        return bmp
    }
}
