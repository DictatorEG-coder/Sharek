package com.sharek.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ألوان الهوية: قرمزي داكن + أزرق كهربائي فاتح + خلفيات داكنة
internal val Background = Color(0xFF0E0E13)
internal val SurfaceDark = Color(0xFF1A1A22)
internal val Crimson = Color(0xFFB71C3A)
internal val Alert = Color(0xFFFF6B81)
internal val Electric = Color(0xFF8ADFFF)
internal val Muted = Color(0xFF9A9AA5)

internal const val TELEGRAM_URL = "https://t.me/Dictator_channel2"
internal const val APP_URL = "https://github.com/DictatorEG-coder/Sharek/releases/latest"

internal fun fmtTime(sec: Long): String = when {
    sec < 60 -> "$sec ث"
    sec < 3600 -> "${sec / 60} د ${sec % 60} ث"
    else -> "${sec / 3600} س ${(sec % 3600) / 60} د"
}

internal fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

internal fun openTelegram(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "لا يوجد تطبيق لفتح الرابط", Toast.LENGTH_LONG).show()
    }
}

internal fun shareApp(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "شارك")
        .putExtra(Intent.EXTRA_TEXT, "جرّب تطبيق «شارك» لنقل الملفات بين الهواتف دون إنترنت:\n$APP_URL")
    ctx.startActivity(Intent.createChooser(send, "مشاركة التطبيق").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** ينفّذ الكتلة كلما عاد المستخدم إلى التطبيق (مثلًا بعد منح إذن من الإعدادات). */
@Composable
internal fun OnResume(block: () -> Unit) {
    val ctx = LocalContext.current
    val current by rememberUpdatedState(block)
    DisposableEffect(ctx) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) current()
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
}

internal fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "تعذّر فتح إعدادات التطبيق", Toast.LENGTH_LONG).show()
    }
}

/** منتقي مستندات متعدد مع إذن دائم، ليبقى الملف قابلًا لإعادة الإرسال من السجل لاحقًا. */
internal class PickDocs : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val out = ArrayList<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { out += it }
        } else {
            intent.data?.let { out += it }
        }
        return out
    }
}

/** يضيف ملفات اختارها المستخدم عبر منتقي النظام إلى قائمة الإرسال. يعيد عدد ما تعذّرت قراءته. */
internal fun addPicked(ctx: Context, uris: List<Uri>): Int {
    var failed = 0
    val out = ArrayList<OutFile>()
    for (u in uris) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val f = FileStore.describe(ctx, u)
        if (f != null) out += f else failed++
    }
    Selection.add(out)
    return failed
}

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

/** شريط سفلي في شاشات الاختيار: عدد وحجم المحدد وزر المتابعة. */
@Composable
internal fun PickerBar(count: Int, size: Long, enabled: Boolean, label: String = "إضافة للإرسال", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("المحدد: $count", color = Color.White, fontWeight = FontWeight.Bold)
            Text(fmtSize(size), color = Muted, fontSize = 12.sp)
        }
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

private val thumbCache = android.util.LruCache<Uri, ImageBitmap>(120)

/** صورة مصغّرة لعنصر MediaStore، تُحمَّل في الخلفية مع ذاكرة مؤقتة صغيرة. */
@Composable
internal fun Thumb(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(thumbCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                        .asImageBitmap().also { thumbCache.put(uri, it) }
                }.getOrNull()
            }
        }
    }
    Box(modifier.background(SurfaceDark)) {
        bmp?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun Tile(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    filled: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (filled) accent else SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(46.dp).background(if (filled) Color.Black.copy(alpha = 0.18f) else accent, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(
                title,
                color = if (filled && accent == Electric) Background else Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(subtitle, color = if (filled && accent == Electric) Background.copy(alpha = 0.7f) else Muted, fontSize = 11.sp)
            }
        }
    }
}
