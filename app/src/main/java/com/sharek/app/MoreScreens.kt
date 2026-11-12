package com.sharek.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────────── سجل النقل ─────────────────────────────

@Composable
internal fun HistoryScreen(onBack: () -> Unit, onResend: () -> Unit) {
    val entries by History.items.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Header("سجل النقل", onBack) }
            if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("مسح السجل") }
        }
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text("لا توجد عمليات نقل بعد.", color = Muted, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.id }) { e -> HistoryCard(e, onResend) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل") },
            text = { Text("سيُحذف سجل العمليات فقط، أما الملفات المستلمة فتبقى في مجلدها.") },
            confirmButton = { TextButton(onClick = { History.clear(); confirmClear = false }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun HistoryCard(e: HistoryEntry, onResend: () -> Unit) {
    val ctx = LocalContext.current
    val (statusText, statusColor) = when (e.status) {
        History.OK -> "مكتمل ✓" to Electric
        History.FAILED -> "فشل" to Alert
        History.CANCELLED -> "أُلغي" to Muted
        else -> "مرفوض" to Muted
    }
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()) }
    val total = e.files.sumOf { it.size }

    Column(
        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (e.sending) Icons.Default.CallMade else Icons.Default.CallReceived, null,
                tint = if (e.sending) Color(0xFFFF8FA0) else Electric, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (e.sending) "أُرسل إلى " else "استُلم من ") + e.peer,
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1
                )
                Text(fmt.format(Date(e.time)), color = Muted, fontSize = 11.sp)
            }
            Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text("${e.files.size} ملف • ${fmtSize(total)}", color = Muted, fontSize = 12.sp)

        e.files.take(4).forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                    Text(fmtSize(f.size), color = Muted, fontSize = 11.sp)
                }
                if (!e.sending && e.status == History.OK && f.uri != null) {
                    val exists by produceState<Boolean?>(null, f.uri) {
                        value = withContext(Dispatchers.IO) { FileStore.readable(ctx, f.uri) }
                    }
                    if (exists == true) TextButton(onClick = { FileStore.open(ctx, f) }) { Text("فتح") }
                    else if (exists == false) Text("محذوف", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (e.files.size > 4) Text("… و${e.files.size - 4} ملفات أخرى", color = Muted, fontSize = 11.sp)

        if (e.files.any { it.uri != null }) { // المرسَل والمستلَم معًا، ما دام الملف موجودًا
            OutlinedButton(onClick = {
                val ok = e.files.mapNotNull { f ->
                    val u = f.uri ?: return@mapNotNull null
                    if (FileStore.readable(ctx, u)) FileStore.describe(ctx, u)?.copy(name = f.name) else null
                }
                if (ok.isEmpty()) {
                    Toast.makeText(ctx, "الملفات لم تعد موجودة أو لا يمكن قراءتها.", Toast.LENGTH_LONG).show()
                } else {
                    Selection.add(ok)
                    if (ok.size < e.files.size) {
                        Toast.makeText(ctx, "أُضيف ${ok.size} من ${e.files.size} ملف (الباقي غير موجود).", Toast.LENGTH_LONG).show()
                    }
                    onResend()
                }
            }) { Text("إعادة إرسال") }
        }
    }
}

// ───────────────────────────── الإعدادات ─────────────────────────────

@Composable
internal fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(Prefs.deviceName(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header("الإعدادات", onBack)

        Text("اسم هذا الهاتف (يظهر للطرف الآخر)", color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (name.isBlank()) {
                Toast.makeText(ctx, "الاسم لا يمكن أن يكون فارغًا.", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setDeviceName(ctx, name)
                Toast.makeText(ctx, "تم الحفظ", Toast.LENGTH_SHORT).show()
            }
        }) { Text("حفظ الاسم") }

        HorizontalDivider(color = SurfaceDark)
        Text("مجلد الملفات المستلمة: Download/Sharek", color = Muted, fontSize = 13.sp)
        OutlinedButton(onClick = { confirmClear = true }) { Text("مسح سجل النقل") }

        HorizontalDivider(color = SurfaceDark)
        OutlinedButton(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("قناة تيليجرام")
        }
        OutlinedButton(onClick = { shareApp(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("مشاركة التطبيق")
        }

        Spacer(Modifier.height(8.dp))
        Text("صلِّ على النبي ﷺ", color = Electric, fontWeight = FontWeight.Bold)
        Text("شارك • الإصدار $version", color = Muted, fontSize = 11.sp)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح سجل النقل") },
            text = { Text("سيُحذف السجل فقط، أما الملفات المستلمة فتبقى في مجلدها.") },
            confirmButton = { TextButton(onClick = { History.clear(); confirmClear = false }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}
