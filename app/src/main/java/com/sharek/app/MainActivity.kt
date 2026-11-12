package com.sharek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.sharek.app.History.init(applicationContext)
        setContent { SharekApp() }
    }
}

@Composable
private fun SharekApp() {
    var stack by remember { mutableStateOf(listOf("home")) }
    val screen = stack.last()
    val state by Controller.state.collectAsState()
    val busy = state is UiState.Progress || state is UiState.Connecting
    val view = LocalView.current
    SideEffect { view.keepScreenOn = busy }

    fun push(s: String) { stack = stack + s }

    fun pop() {
        // مغادرة شاشتي الإرسال/الاستلام تلغي أي عملية جارية (السلوك السابق نفسه)
        if (screen == "send" || screen == "receive") Controller.reset()
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    // بعد أن يضيف المستخدم عناصر من شاشة اختيار: ننتقل إلى شاشة الإرسال
    fun added() {
        val i = stack.indexOf("send")
        stack = if (i >= 0) stack.subList(0, i + 1) else stack.dropLast(1) + "send"
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Crimson,
                secondary = Electric,
                background = Background,
                surface = SurfaceDark
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Background) {
              // Android 15 يرسم المحتوى تحت شريطي النظام؛ نضيف هامشًا ونُبقي الخلفية داكنة خلفهما
              Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (screen) {
                    "send" -> SendScreen(onBack = { pop() }, onNavigate = { push(it) })
                    "receive" -> ReceiveScreen(onBack = { pop() })
                    "apps" -> AppsScreen(onBack = { pop() }, onAdded = { added() })
                    "media" -> MediaScreen(onBack = { pop() }, onAdded = { added() })
                    "files" -> FilesScreen(onBack = { pop() }, onAdded = { added() })
                    "contacts" -> ContactsScreen(onBack = { pop() }, onAdded = { added() })
                    "history" -> HistoryScreen(onBack = { pop() }, onResend = { added() })
                    "settings" -> SettingsScreen(onBack = { pop() })
                    else -> HomeScreen(onNavigate = { dest ->
                        if (dest == "send" || dest == "receive") Controller.reset()
                        push(dest)
                    })
                }
              }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(76.dp).background(SurfaceDark, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("ش", color = Alert, fontSize = 46.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text("شارك", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("أرسل. استقبل. ببساطة.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("صلِّ على النبي ﷺ", color = Electric, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("إرسال", Crimson, Modifier.weight(1f), "أرسل ملفاتك", filled = true, onClick = { onNavigate("send") }) {
                Icon(Icons.Default.CallMade, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Tile("استلام", Electric, Modifier.weight(1f), "من هاتف قريب", filled = true, onClick = { onNavigate("receive") }) {
                Icon(Icons.Default.CallReceived, null, tint = Background, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("التطبيقات", Crimson, Modifier.weight(1f), onClick = { onNavigate("apps") }) {
                Icon(Icons.Default.Apps, null, tint = Color.White)
            }
            Tile("الصور والفيديوهات", Crimson, Modifier.weight(1f), onClick = { onNavigate("media") }) {
                Icon(Icons.Default.PhotoLibrary, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("الملفات", Crimson, Modifier.weight(1f), onClick = { onNavigate("files") }) {
                Icon(Icons.Default.Folder, null, tint = Color.White)
            }
            Tile("جهات الاتصال", Crimson, Modifier.weight(1f), onClick = { onNavigate("contacts") }) {
                Icon(Icons.Default.Contacts, null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("سجل النقل", Electric, Modifier.weight(1f), onClick = { onNavigate("history") }) {
                Icon(Icons.Default.History, null, tint = Background)
            }
            Tile("الإعدادات", Electric, Modifier.weight(1f), onClick = { onNavigate("settings") }) {
                Icon(Icons.Default.Settings, null, tint = Background)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { openTelegram(ctx) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("قناة تيليجرام")
            }
            OutlinedButton(onClick = { shareApp(ctx) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مشاركة التطبيق")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("لا حاجة إلى إنترنت — يكفي أن يكون الهاتفان على شبكة Wi-Fi واحدة أو نقطة اتصال", color = Muted, fontSize = 11.sp)
    }
}

// ───────────────────────────── الإرسال ─────────────────────────────

@Composable
internal fun SendScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val state by Controller.state.collectAsState()
    val files = Selection.files
    val peers = remember { mutableStateListOf<Peer>() }
    var manual by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val finder = Finder(ctx) { list -> peers.clear(); peers.addAll(list) }
        finder.start()
        onDispose { finder.stop() }
    }

    fun sendTo(host: String, port: Int, name: String) {
        if (files.isEmpty()) { error = "اختر ملفًا واحدًا على الأقل أولًا."; return }
        error = null
        Controller.send(ctx, host, port, files.toList(), name)
    }

    fun scanQr() {
        if (files.isEmpty()) { error = "اختر ملفًا واحدًا على الأقل أولًا."; return }
        error = null
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val target = Qr.parse(b.rawValue)
                if (target == null) error = "هذا ليس رمز شارك صالحًا."
                else sendTo(target.host, target.port, target.name)
            }
            .addOnFailureListener {
                error = "تعذّر فتح الماسح (يحتاج خدمات Google Play). استخدم العنوان يدويًا."
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header("إرسال الملفات", onBack)

        when (val s = state) {
            is UiState.Idle -> {
                Text("ماذا تريد أن ترسل؟", color = Color.White, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("apps") }, modifier = Modifier.weight(1f)) { Text("التطبيقات", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = { onNavigate("media") }, modifier = Modifier.weight(1f)) { Text("الوسائط", fontSize = 12.sp, maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("files") }, modifier = Modifier.weight(1f)) { Text("الملفات", fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = { onNavigate("contacts") }, modifier = Modifier.weight(1f)) { Text("جهات الاتصال", fontSize = 12.sp, maxLines = 1) }
                }

                if (files.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "المحدد (${files.size}) — ${fmtSize(Selection.totalSize())}",
                            color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { Selection.clear() }) { Text("مسح الكل") }
                    }
                    files.toList().forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(fmtSize(f.size), color = Muted, fontSize = 12.sp)
                            }
                            IconButton(onClick = { Selection.remove(f) }) {
                                Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Muted)
                            }
                        }
                    }
                }

                Text("الهواتف القريبة", color = Color.White, fontWeight = FontWeight.Bold)
                if (peers.isEmpty()) {
                    Text("جارٍ البحث … (افتح «استلام» على الهاتف الآخر). إن لم يظهر، امسح رمز QR أو أدخل العنوان يدويًا.", color = Muted, fontSize = 12.sp)
                }
                peers.toList().forEach { p ->
                    Surface(
                        onClick = { sendTo(p.host, p.port, p.name) },
                        color = SurfaceDark,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${p.host}:${p.port} — اضغط للإرسال", color = Muted, fontSize = 12.sp)
                        }
                    }
                }

                OutlinedButton(onClick = { scanQr() }, modifier = Modifier.fillMaxWidth()) {
                    Text("مسح رمز QR من الهاتف المستقبل")
                }

                Text("أو أدخل عنوان الهاتف المستقبل", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("مثال: 192.168.1.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val t = manual.trim()
                        val host = t.substringBefore(':')
                        val port = t.substringAfter(':', "").toIntOrNull() ?: Proto.PORT
                        if (host.isEmpty()) error = "أدخل عنوان IP الظاهر على شاشة الاستلام."
                        else sendTo(host, port, host)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اتصال وإرسال") }

                error?.let { Text(it, color = Alert, fontSize = 13.sp) }
            }
            else -> TransferPanel(
                state = s,
                onDone = { if (s is UiState.Done) Selection.clear(); Controller.reset() },
                onCancel = { Controller.reset() }
            )
        }
    }
}

// ───────────────────────────── الاستلام ─────────────────────────────

@Composable
internal fun ReceiveScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by Controller.state.collectAsState()

    LaunchedEffect(Unit) { Controller.startReceiving(ctx) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header("استلام الملفات", onBack)

        when (val s = state) {
            is UiState.Listening -> {
                Column(
                    Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(22.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("جاهز للاستلام ✓", color = Electric, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("اسم الهاتف: ${s.deviceName}", color = Color.White)
                    if (s.addresses.isEmpty()) {
                        Text("لا يوجد اتصال بشبكة Wi-Fi أو نقطة اتصال. اتصل بالشبكة نفسها التي يتصل بها الهاتف المرسل.", color = Alert, fontSize = 13.sp)
                    } else {
                        Text("العنوان للإدخال اليدوي على الهاتف المرسل:", color = Muted, fontSize = 12.sp)
                        s.addresses.forEach { Text("$it:${Proto.PORT}", color = Color.White, fontWeight = FontWeight.Bold) }
                        val qr = remember(s.addresses.first(), s.deviceName) {
                            Qr.bitmap(Qr.payload(s.addresses.first(), Proto.PORT, s.deviceName)).asImageBitmap()
                        }
                        Text("أو امسح هذا الرمز من الهاتف المرسل:", color = Muted, fontSize = 12.sp)
                        Image(
                            bitmap = qr,
                            contentDescription = "رمز QR للاتصال",
                            modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                    Text("ينتظر طلبًا من الهاتف المرسل …", color = Muted, fontSize = 12.sp)
                }
            }
            is UiState.Incoming -> {
                Text("وصل طلب استلام …", color = Color.White)
                IncomingDialog(s)
            }
            is UiState.Idle -> CircularProgressIndicator(color = Crimson)
            else -> TransferPanel(state = s, onDone = onBack, onCancel = { Controller.reset(); onBack() })
        }
    }
}

@Composable
private fun IncomingDialog(s: UiState.Incoming) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("طلب استلام ملفات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("«${s.sender}» يريد إرسال ${s.files.size} ملف (${fmtSize(s.files.sumOf { it.size })}):")
                s.files.take(8).forEach { Text("• ${it.name} — ${fmtSize(it.size)}", fontSize = 13.sp) }
                if (s.files.size > 8) Text("… و${s.files.size - 8} ملفات أخرى", fontSize = 13.sp)
                Text("ستُحفظ في: Download/Sharek", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { Controller.respond(true) }) { Text("قبول") } },
        dismissButton = { TextButton(onClick = { Controller.respond(false) }) { Text("رفض") } }
    )
}

// ───────────────────────────── لوحة التقدم ─────────────────────────────

@Composable
internal fun TransferPanel(state: UiState, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(22.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            is UiState.Connecting -> {
                CircularProgressIndicator(color = Crimson)
                Text(state.message, color = Color.White)
                OutlinedButton(onClick = onCancel) { Text("إلغاء") }
            }
            is UiState.Progress -> {
                val frac = if (state.totalAll <= 0) 1f else (state.totalDone.toFloat() / state.totalAll).coerceIn(0f, 1f)
                val remaining = (state.totalAll - state.totalDone).coerceAtLeast(0)
                val eta = if (state.speed > 0) remaining / state.speed else -1L
                Text(if (state.sending) "جارٍ الإرسال" else "جارٍ الاستلام", color = Electric, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("الملف ${state.index} من ${state.count}: ${state.fileName}", color = Color.White, fontSize = 13.sp, maxLines = 2)
                LinearProgressIndicator(progress = { frac }, color = Crimson, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(frac * 100).toInt()}%  •  ${fmtSize(state.totalDone)} / ${fmtSize(state.totalAll)}",
                    color = Color.White, fontSize = 13.sp
                )
                Text(
                    "السرعة: ${fmtSize(state.speed)}/ث  •  المتبقي: ${fmtSize(remaining)}" +
                        if (eta >= 0) "  •  ≈ ${fmtTime(eta)}" else "",
                    color = Muted, fontSize = 12.sp
                )
                OutlinedButton(onClick = onCancel) { Text("إلغاء النقل") }
            }
            is UiState.Done -> {
                Text("اكتمل النقل وتم التحقق من سلامة الملفات ✓", color = Electric, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                state.files.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text(fmtSize(f.size), color = Muted, fontSize = 12.sp)
                        }
                        if (f.uri != null) TextButton(onClick = { Controller.open(ctx, f) }) { Text("فتح") }
                    }
                }
                if (!state.sending) Text("المجلد: Download/Sharek", color = Muted, fontSize = 12.sp)
                Button(onClick = onDone) { Text("تم") }
            }
            is UiState.Failed -> {
                Text("لم يكتمل النقل", color = Alert, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(state.message, color = Color.White, fontSize = 13.sp)
                Button(onClick = onDone) { Text("حسنًا") }
            }
            else -> {}
        }
    }
}
