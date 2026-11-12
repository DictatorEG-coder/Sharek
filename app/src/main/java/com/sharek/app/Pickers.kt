package com.sharek.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ───────────────────────────── التطبيقات ─────────────────────────────

private class AppItem(
    val label: String, val pkg: String, val apk: String,
    val size: Long, val version: String, val split: Boolean
)

@Suppress("DEPRECATION")
private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
        val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
        val apk = File(ai.sourceDir)
        if (!apk.isFile) return@mapNotNull null
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isSystem) return@mapNotNull null
        val ver = runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }.getOrNull() ?: ""
        AppItem(
            ai.loadLabel(pm).toString(), ai.packageName, ai.sourceDir, apk.length(), ver,
            ai.splitSourceDirs?.isNotEmpty() == true
        )
    }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

@Composable
private fun AppIcon(pkg: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
internal fun AppsScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    val selected = remember { mutableStateMapOf<String, AppItem>() }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Header("التطبيقات", onBack) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = apps
            if (list == null) {
                CircularProgressIndicator(color = Crimson, modifier = Modifier.align(Alignment.Center))
            } else if (list.isEmpty()) {
                Text("لم يتم العثور على تطبيقات.", color = Muted, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(92.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.pkg }) { a ->
                        val on = selected.containsKey(a.pkg)
                        Column(
                            Modifier
                                .clickable { if (on) selected.remove(a.pkg) else selected[a.pkg] = a }
                                .background(if (on) Crimson.copy(alpha = 0.25f) else SurfaceDark, RoundedCornerShape(14.dp))
                                .border(if (on) 2.dp else 0.dp, if (on) Crimson else Color.Transparent, RoundedCornerShape(14.dp))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIcon(a.pkg, Modifier.size(52.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(a.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(fmtSize(a.size) + if (a.split) " ⚠" else "", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (selected.values.any { it.split }) {
            Text(
                "⚠ بعض التطبيقات المحددة مقسّمة (Split APK): يُرسل الملف الأساسي فقط وقد لا يُثبَّت وحده.",
                color = Alert, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        PickerBar(selected.size, selected.values.sumOf { it.size }, selected.isNotEmpty()) {
            val out = selected.values.mapNotNull { a ->
                FileStore.describe(ctx, Uri.fromFile(File(a.apk)))
                    ?.copy(name = Proto.cleanName("${a.label}_${a.version}.apk"))
            }
            Selection.add(out)
            onAdded()
        }
    }
}

// ───────────────────────────── الصور والفيديوهات ─────────────────────────────

private class MediaItem(val uri: Uri, val size: Long, val duration: Long, val video: Boolean)

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(ctx: Context): Boolean = mediaPermissions().any {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private fun queryMedia(ctx: Context, video: Boolean): List<MediaItem> {
    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = if (video) {
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.Video.Media.DURATION)
    } else {
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
    }
    val out = ArrayList<MediaItem>()
    runCatching {
        ctx.contentResolver.query(collection, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDur = if (video) c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1
            while (c.moveToNext()) {
                val size = c.getLong(iSize)
                if (size <= 0) continue
                out += MediaItem(
                    ContentUris.withAppendedId(collection, c.getLong(iId)), size,
                    if (iDur >= 0) c.getLong(iDur) else 0L, video
                )
            }
        }
    }
    return out
}

@Composable
internal fun MediaScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf<List<MediaItem>?>(null) }
    var videos by remember { mutableStateOf<List<MediaItem>?>(null) }
    val selected = remember { mutableStateMapOf<Uri, MediaItem>() }
    var note by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
    }
    val fallback = rememberLauncherForActivityResult(PickDocs()) { uris ->
        if (uris.isNotEmpty()) {
            val failed = addPicked(ctx, uris)
            if (failed > 0) note = "تعذّرت قراءة $failed ملف." else onAdded()
        }
    }

    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(mediaPermissions()) }
    OnResume { granted = hasMediaAccess(ctx) }
    LaunchedEffect(granted) {
        if (granted) {
            images = withContext(Dispatchers.IO) { queryMedia(ctx, false) }
            videos = withContext(Dispatchers.IO) { queryMedia(ctx, true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Header("الصور والفيديوهات", onBack) }

        if (!granted) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "نحتاج إذن الوصول إلى الصور والفيديوهات لعرضها هنا. لا تُرفع أي ملفات إلى الإنترنت.",
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permLauncher.launch(mediaPermissions()) }) { Text("السماح بالوصول") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { fallback.launch(arrayOf("image/*", "video/*")) }) { Text("اختيار عبر منتقي النظام") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openAppSettings(ctx) }) { Text("فتح إعدادات الأذونات") }
                note?.let { Text(it, color = Alert, fontSize = 12.sp) }
            }
        } else {
            TabRow(selectedTabIndex = tab, containerColor = SurfaceDark) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("الصور") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الفيديوهات") })
            }
            val list = if (tab == 0) images else videos
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list == null) {
                    CircularProgressIndicator(color = Crimson, modifier = Modifier.align(Alignment.Center))
                } else if (list.isEmpty()) {
                    Text("لا توجد عناصر.", color = Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(list, key = { it.uri.toString() }) { m ->
                            val on = selected.containsKey(m.uri)
                            Box(
                                Modifier.aspectRatio(1f).clickable {
                                    if (on) selected.remove(m.uri) else selected[m.uri] = m
                                }
                            ) {
                                Thumb(m.uri, Modifier.fillMaxSize())
                                if (m.video) {
                                    Text(
                                        fmtDuration(m.duration), color = Color.White, fontSize = 10.sp,
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                                Text(
                                    fmtSize(m.size), color = Color.White, fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 4.dp)
                                )
                                if (on) {
                                    Box(
                                        Modifier.fillMaxSize().background(Crimson.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                                }
                            }
                        }
                    }
                }
            }
            PickerBar(selected.size, selected.values.sumOf { it.size }, selected.isNotEmpty()) {
                val out = selected.values.mapNotNull { FileStore.describe(ctx, it.uri) }
                Selection.add(out)
                onAdded()
            }
        }
    }
}

// ───────────────────────────── الملفات ─────────────────────────────

private class FileCat(val title: String, val hint: String, val types: Array<String>)

private val fileCats = listOf(
    FileCat(
        "المستندات", "Word وExcel وPowerPoint ونصوص",
        arrayOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf", "text/plain"
        )
    ),
    FileCat("ملفات PDF", "كتب وأوراق PDF", arrayOf("application/pdf")),
    FileCat(
        "الملفات المضغوطة", "ZIP وRAR و7z",
        arrayOf(
            "application/zip", "application/x-rar-compressed", "application/vnd.rar",
            "application/x-7z-compressed", "application/gzip", "application/x-tar"
        )
    ),
    FileCat("الصوتيات", "موسيقى وتسجيلات", arrayOf("audio/*")),
    FileCat("كل الملفات", "تصفح أي نوع من الملفات", arrayOf("*/*"))
)

@Composable
internal fun FilesScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickDocs()) { uris ->
        val failed = addPicked(ctx, uris)
        note = if (failed > 0) "تعذّرت قراءة $failed ملف." else null
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Header("الملفات", onBack) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "اختر نوع الملفات ثم حدّد ما تريد بالضغط المتعدد. سيُفتح منتقي ملفات النظام الآمن (دون صلاحيات تخزين واسعة).",
                    color = Muted, fontSize = 12.sp
                )
            }
            items(fileCats) { c ->
                Surface(
                    onClick = { picker.launch(c.types) },
                    color = SurfaceDark,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(c.hint, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            item { note?.let { Text(it, color = Alert, fontSize = 12.sp) } }
        }
        PickerBar(
            Selection.files.size, Selection.totalSize(), Selection.files.isNotEmpty(), "متابعة للإرسال"
        ) { onAdded() }
    }
}

// ───────────────────────────── جهات الاتصال ─────────────────────────────

private class ContactItem(val id: Long, val name: String, val numbers: List<String>, val vcard: String) {
    val bytes: Long = vcard.toByteArray().size.toLong()
}

private fun vEsc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

private fun buildVCard(name: String, numbers: List<String>): String = buildString {
    append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
    append("FN:").append(vEsc(name)).append("\r\n")
    append("N:").append(vEsc(name)).append(";;;;\r\n")
    numbers.forEach { append("TEL;TYPE=CELL:").append(vEsc(it)).append("\r\n") }
    append("END:VCARD\r\n")
}

private fun loadContacts(ctx: Context): List<ContactItem> {
    val names = LinkedHashMap<Long, String>()
    val nums = HashMap<Long, MutableList<String>>()
    runCatching {
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                val num = c.getString(2)?.trim().orEmpty()
                if (name.isEmpty()) continue
                names.putIfAbsent(id, name)
                if (num.isNotEmpty()) {
                    val l = nums.getOrPut(id) { mutableListOf() }
                    if (num !in l) l += num
                }
            }
        }
    }
    return names.map { (id, name) ->
        val n = nums[id].orEmpty()
        ContactItem(id, name, n, buildVCard(name, n))
    }
}

@Composable
internal fun ContactsScreen(onBack: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var contacts by remember { mutableStateOf<List<ContactItem>?>(null) }
    val selected = remember { mutableStateMapOf<Long, ContactItem>() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.READ_CONTACTS) }
    // العودة من إعدادات النظام بعد منح الإذن يدويًا
    OnResume {
        granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(granted) { if (granted) contacts = withContext(Dispatchers.IO) { loadContacts(ctx) } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Header("جهات الاتصال", onBack) }
        if (!granted) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "نحتاج إذن قراءة جهات الاتصال لعرضها وإرسال ما تختاره منها فقط إلى الهاتف الآخر. لا تُرفع إلى الإنترنت.",
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("السماح بالوصول") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openAppSettings(ctx) }) { Text("فتح إعدادات الأذونات") }
            }
        } else {
            val list = contacts
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list == null) {
                    CircularProgressIndicator(color = Crimson, modifier = Modifier.align(Alignment.Center))
                } else if (list.isEmpty()) {
                    Text("لا توجد جهات اتصال بأرقام.", color = Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                        item {
                            TextButton(onClick = {
                                if (selected.size == list.size) selected.clear() else list.forEach { selected[it.id] = it }
                            }) { Text(if (selected.size == list.size) "إلغاء تحديد الكل" else "تحديد الكل") }
                        }
                        items(list, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { if (selected.containsKey(c.id)) selected.remove(c.id) else selected[c.id] = c }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.containsKey(c.id),
                                    onCheckedChange = { if (it) selected[c.id] = c else selected.remove(c.id) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, color = Color.White, fontSize = 15.sp, maxLines = 1)
                                    Text(c.numbers.joinToString("  "), color = Muted, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            PickerBar(selected.size, selected.values.sumOf { it.bytes }, selected.isNotEmpty()) {
                // نجمع الجهات المحددة في ملف vCard واحد يمكن استيراده مباشرة على الهاتف المستقبل
                val text = selected.values.joinToString("") { it.vcard }
                val file = File(ctx.cacheDir, "contacts-${System.currentTimeMillis()}.vcf")
                file.writeText(text, Charsets.UTF_8)
                Selection.files.removeAll { it.name == "contacts.vcf" }
                Selection.add(listOf(OutFile(Uri.fromFile(file), "contacts.vcf", file.length())))
                onAdded()
            }
        }
    }
}
