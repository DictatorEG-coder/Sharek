package com.sharek.app

import android.content.Context
import android.os.Build

/** إعدادات خفيفة محلية فقط. */
object Prefs {
    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("sharek", Context.MODE_PRIVATE)

    fun deviceName(ctx: Context): String =
        sp(ctx).getString("device_name", null)?.takeIf { it.isNotBlank() } ?: (Build.MODEL ?: "Android")

    fun setDeviceName(ctx: Context, value: String) {
        sp(ctx).edit().putString("device_name", value.trim().take(40)).apply()
    }
}
