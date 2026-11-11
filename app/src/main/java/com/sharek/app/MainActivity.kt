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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
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

private val Background = Color(0xFF101014)
private val SurfaceDark = Color(0xFF1B1B22)
private val Crimson = Color(0xFFE53950)
private val Electric = Color(0xFF8ADFFF)
private val Muted = Color(0xFF9A9AA5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SharekApp() }
    }
}

@Composable
private fun SharekApp() {
    var screen by remember { mutableStateOf("home") }
    val state by Controller.state.collectAsState()
    val busy = state is UiState.Progress || state is UiState.Connecting
    val view = LocalView.current
    SideEffect { view.keepScreenOn = busy }

    fun goHome() {
        Controller.reset()
        screen = "home"
    }

    BackHandler(enabled = screen != "home") { goHome() }

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
                when (screen) {
                    "send" -> SendScreen(onBack = { goHome() })
                    "receive" -> ReceiveScreen(onBack = { goHome() })
                    else -> HomeScreen(
                        onSend = { Controller.reset(); screen = "send" },
                        onReceive = { Controller.reset(); screen = "receive" }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(42.dp))
        Box(
            modifier = Modifier.size(86.dp).background(SurfaceDark, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("ش", color = Crimson, fontSize = 52.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(18.dp))
        Text("شارك", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("أرسل. استقبل. ببساطة.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("صلِّ على النبي ﷺ", color = Electric, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))
        ActionCard("إرسال", "أرسل الصور والفيديوهات والملفات", Crimson, onSend) {
            Icon(Icons.Default.CallMade, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        ActionCard("استلام", "استقبل الملفات من هاتف قريب", Electric, onReceive) {
            Icon(Icons.Default.CallReceived, null, tint = Background, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("لا حاجة إلى اتصال بالإنترنت — يكفي أن يكون الهاتفان على شبكة Wi-Fi واحدة أو نقطة اتصال", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text("شارك • الإصدار 0.2", color = Color(0xFF666671), fontSize = 11.sp)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SurfaceDark,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(54.dp).background(accent, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────────── الإرسال ─────────────────────────────

@Composable
private fun SendScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by Controller.state.collectAsState()
    val files = remember { mutableStateListOf<OutFile>() }
    val peers = remember { mutableStateListOf<Peer>() }
    var manual by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        for (u in uris) {
            if (files.any { it.uri == u }) continue
            val f = FileStore.describe(ctx, u)
            if (f != null) files.add(f) else error = "تعذّر قراءة أحد الملفات المختارة."
        }
    }

    DisposableEffect(Unit) {
        val finder = Finder(ctx) { list -> peers.clear(); peers.addAll(list) }
        finder.start()
        onDispose { finder.stop() }
    }

    fun sendTo(host: String, port: Int) {
        if (files.isEmpty()) { error = "اختر ملفًا واحدًا على الأقل أولًا."; return }
        error = null
        Controller.send(ctx, host, port, files.toList())
    }

    fun scanQr() {
        if (files.isEmpty()) { error = "اختر ملفًا واحدًا على الأقل أولًا."; return }
        error = null
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val target = Qr.parse(b.rawValue)
                if (target == null) error = "هذا ليس رمز شارك صالحًا."
                else sendTo(target.first, target.second)
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
                Button(onClick = { error = null; picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("اختيار الملفات")
                }
                if (files.isNotEmpty()) {
                    Text("الملفات المختارة (${files.size}) — ${fmtSize(files.sumOf { it.size })}", color = Color.White, fontWeight = FontWeight.Bold)
                    files.toList().forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(fmtSize(f.size), color = Muted, fontSize = 12.sp)
                            }
                            IconButton(onClick = { files.remove(f) }) {
                                Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Muted)
                            }
                        }
                    }
                }

                Text("الهواتف القريبة", color = Color.White, fontWeight = FontWeight.Bold)
                if (peers.isEmpty()) {
                    Text("جارٍ البحث … (افتح «استلام» على الهاتف الآخر). إن لم يظهر، استخدم العنوان يدويًا أدناه.", color = Muted, fontSize = 12.sp)
                }
                peers.toList().forEach { p ->
                    Surface(
                        onClick = { sendTo(p.host, p.port) },
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
                        else sendTo(host, port)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اتصال وإرسال") }

                error?.let { Text(it, color = Crimson, fontSize = 13.sp) }
            }
            else -> TransferPanel(
                state = s,
                onDone = { if (s is UiState.Done) files.clear(); Controller.reset() },
                onCancel = { Controller.reset() }
            )
        }
    }
}

// ───────────────────────────── الاستلام ─────────────────────────────

@Composable
private fun ReceiveScreen(onBack: () -> Unit) {
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
                        Text("لا يوجد اتصال بشبكة Wi-Fi أو نقطة اتصال. اتصل بالشبكة نفسها التي يتصل بها الهاتف المرسل.", color = Crimson, fontSize = 13.sp)
                    } else {
                        Text("العنوان للإدخال اليدوي على الهاتف المرسل:", color = Muted, fontSize = 12.sp)
                        s.addresses.forEach { Text("$it:${Proto.PORT}", color = Color.White, fontWeight = FontWeight.Bold) }
                        val qr = remember(s.addresses.first()) {
                            Qr.bitmap(Qr.payload(s.addresses.first(), Proto.PORT)).asImageBitmap()
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
private fun TransferPanel(state: UiState, onDone: () -> Unit, onCancel: () -> Unit) {
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
                Text(if (state.sending) "جارٍ الإرسال" else "جارٍ الاستلام", color = Electric, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("الملف ${state.index} من ${state.count}: ${state.fileName}", color = Color.White, fontSize = 13.sp, maxLines = 2)
                LinearProgressIndicator(progress = { frac }, color = Crimson, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(frac * 100).toInt()}%  •  ${fmtSize(state.totalDone)} / ${fmtSize(state.totalAll)}  •  ${fmtSize(state.speed)}/ث",
                    color = Muted, fontSize = 12.sp
                )
                OutlinedButton(onClick = onCancel) { Text("إلغاء") }
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
                Text("لم يكتمل النقل", color = Crimson, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(state.message, color = Color.White, fontSize = 13.sp)
                Button(onClick = onDone) { Text("حسنًا") }
            }
            else -> {}
        }
    }
}
