package com.sharek.app

import android.graphics.Bitmap
import android.graphics.Color as AColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    private const val PREFIX = "sharek://"

    fun payload(host: String, port: Int) = "$PREFIX$host:$port"

    /** يحلّل محتوى الرمز؛ يعيد null إن لم يكن رمز شارك صالحًا. */
    fun parse(raw: String?): Pair<String, Int>? {
        val t = raw?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        val body = t.removePrefix(PREFIX)
        val host = body.substringBefore(':')
        val port = body.substringAfter(':', "").toIntOrNull() ?: Proto.PORT
        if (host.isEmpty() || port !in 1..65535) return null
        return host to port
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
