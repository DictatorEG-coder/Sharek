package com.sharek.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * البروتوكول (اتصال TCP واحد):
 *  المرسل  -> MAGIC, اسم الجهاز, عدد الملفات, ثم (اسم, حجم) لكل ملف
 *  المستقبل -> boolean (قبول/رفض)  [بعد موافقة المستخدم]
 *  لكل ملف: المرسل -> البايتات كاملة ثم SHA-256 (32 بايت)
 *           المستقبل -> boolean (تم التحقق/فشل)
 */
object Controller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var active: Socket? = null
    @Volatile private var decision: CompletableDeferred<Boolean>? = null
    @Volatile private var advertiser: Advertiser? = null

    // جلسة النقل الجارية، لتسجيلها في السجل (لا تؤثر على البروتوكول).
    private class Session(val sending: Boolean, val peer: String, val files: List<SavedFile>)
    @Volatile private var session: Session? = null

    private fun finishSession(status: String, files: List<SavedFile>? = null) {
        val s = session ?: return
        session = null
        val now = System.currentTimeMillis()
        History.add(HistoryEntry(now, s.sending, s.peer, now, status, files ?: s.files))
    }

    fun reset() {
        finishSession(History.CANCELLED)
        stopAll()
        _state.value = UiState.Idle
    }

    fun respond(accept: Boolean) {
        decision?.complete(accept)
    }

    fun open(ctx: Context, f: SavedFile) = FileStore.open(ctx, f)

    private fun stopAll() {
        decision?.complete(false)
        job?.cancel(); job = null
        runCatching { server?.close() }; server = null
        runCatching { active?.close() }; active = null
        advertiser?.stop(); advertiser = null
    }

    // ───────────────────────── الاستلام ─────────────────────────

    fun startReceiving(ctx: Context) {
        val app = ctx.applicationContext
        stopAll()
        val name = Prefs.deviceName(app)
        job = scope.launch {
            val srv = ServerSocket()
            try {
                srv.reuseAddress = true
                srv.bind(InetSocketAddress(Proto.PORT))
            } catch (e: IOException) {
                runCatching { srv.close() }
                _state.value = UiState.Failed("تعذّر فتح المنفذ ${Proto.PORT}. أغلق التطبيق وأعد المحاولة.")
                return@launch
            }
            server = srv
            val adv = Advertiser(app)
            advertiser = adv
            adv.start(name, srv.localPort)
            try {
                while (isActive) {
                    _state.value = UiState.Listening(name, NetUtil.localAddresses())
                    val sock = srv.accept()
                    active = sock
                    val ok = try {
                        sock.use { handleIncoming(app, it) }
                    } finally {
                        active = null
                    }
                    if (ok) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    finishSession(History.FAILED)
                    _state.value = UiState.Failed(describe(e))
                }
            } finally {
                runCatching { srv.close() }
                if (server === srv) server = null
                adv.stop()
                if (advertiser === adv) advertiser = null
            }
        }
    }

    private class Header(val sender: String, val files: List<FileMeta>)

    private fun readHeader(input: DataInputStream): Header? = try {
        if (input.readUTF() != Proto.MAGIC) {
            null
        } else {
            val sender = input.readUTF().take(60)
            val count = input.readInt()
            if (count !in 1..5000) {
                null
            } else {
                val files = List(count) { FileMeta(Proto.cleanName(input.readUTF()), input.readLong()) }
                if (files.any { it.size < 0 }) null else Header(sender, files)
            }
        }
    } catch (e: IOException) {
        null
    }

    /** يعيد true عند نجاح النقل كاملًا. أي اتصال غريب قبل الموافقة يُتجاهل بصمت. */
    private suspend fun handleIncoming(ctx: Context, sock: Socket): Boolean {
        sock.soTimeout = 30_000
        val input = DataInputStream(BufferedInputStream(sock.getInputStream(), Proto.BUF))
        val output = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
        val header = readHeader(input) ?: return false

        session = Session(false, header.sender, header.files.map { SavedFile(it.name, it.size, null, Proto.mimeFor(it.name)) })
        val d = CompletableDeferred<Boolean>()
        decision = d
        _state.value = UiState.Incoming(header.sender, header.files)
        val accepted = withTimeoutOrNull(120_000) { d.await() } ?: false
        decision = null
        if (!accepted) {
            finishSession(History.REJECTED)
            runCatching { output.writeBoolean(false); output.flush() }
            return false
        }
        output.writeBoolean(true)
        output.flush()

        val total = header.files.sumOf { it.size }
        val meter = Meter(total)
        val saved = ArrayList<SavedFile>()
        val buf = ByteArray(Proto.BUF)
        val count = header.files.size

        for ((i, meta) in header.files.withIndex()) {
            val mime = Proto.mimeFor(meta.name)
            val pending = FileStore.create(ctx, meta.name, mime)
            var committed = false
            try {
                val md = MessageDigest.getInstance("SHA-256")
                var remaining = meta.size
                var fileDone = 0L
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw EOFException()
                    md.update(buf, 0, n)
                    pending.out.write(buf, 0, n)
                    remaining -= n
                    fileDone += n
                    meter.done += n
                    emit(false, meta.name, i, count, fileDone, meta.size, meter, false)
                }
                val theirs = ByteArray(32)
                input.readFully(theirs)
                val ok = MessageDigest.isEqual(md.digest(), theirs)
                if (ok) {
                    FileStore.commit(ctx, pending)
                    committed = true
                }
                output.writeBoolean(ok)
                output.flush()
                if (!ok) throw IOException("فشل التحقق من سلامة الملف «${meta.name}». تم حذف النسخة التالفة.")
                emit(false, meta.name, i, count, meta.size, meta.size, meter, true)
                saved += SavedFile(pending.displayName, meta.size, pending.uri, mime)
            } finally {
                if (!committed) FileStore.discard(ctx, pending)
            }
        }
        finishSession(History.OK, saved)
        _state.value = UiState.Done(false, saved)
        return true
    }

    // ───────────────────────── الإرسال ─────────────────────────

    fun send(ctx: Context, host: String, port: Int, files: List<OutFile>, peerName: String = host) {
        val app = ctx.applicationContext
        stopAll()
        session = Session(true, peerName, files.map { SavedFile(it.name, it.size, it.uri, Proto.mimeFor(it.name)) })
        job = scope.launch {
            _state.value = UiState.Connecting("جارٍ الاتصال بـ $host …")
            val sock = Socket()
            active = sock
            try {
                sock.connect(InetSocketAddress(host, port), 8_000)
                sock.tcpNoDelay = true
                sock.soTimeout = 150_000 // وقت كافٍ لموافقة المستخدم على الهاتف الآخر
                val output = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), Proto.BUF))
                val input = DataInputStream(BufferedInputStream(sock.getInputStream()))

                output.writeUTF(Proto.MAGIC)
                output.writeUTF(Prefs.deviceName(app))
                output.writeInt(files.size)
                for (f in files) {
                    output.writeUTF(f.name)
                    output.writeLong(f.size)
                }
                output.flush()

                _state.value = UiState.Connecting("بانتظار موافقة الهاتف الآخر على الاستلام …")
                if (!input.readBoolean()) {
                    if (isActive) {
                        finishSession(History.REJECTED)
                        _state.value = UiState.Failed("رفض الهاتف الآخر استلام الملفات.")
                    }
                    return@launch
                }
                sock.soTimeout = 30_000

                val total = files.sumOf { it.size }
                val meter = Meter(total)
                val buf = ByteArray(Proto.BUF)
                for ((i, f) in files.withIndex()) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val stream = app.contentResolver.openInputStream(f.uri)
                        ?: throw IOException("تعذّر فتح الملف «${f.name}».")
                    stream.use { ins ->
                        var remaining = f.size
                        var fileDone = 0L
                        while (remaining > 0) {
                            currentCoroutineContext().ensureActive()
                            val n = ins.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n < 0) throw IOException("تغيّر الملف «${f.name}» أثناء الإرسال.")
                            md.update(buf, 0, n)
                            output.write(buf, 0, n)
                            remaining -= n
                            fileDone += n
                            meter.done += n
                            emit(true, f.name, i, files.size, fileDone, f.size, meter, false)
                        }
                    }
                    output.write(md.digest())
                    output.flush()
                    if (!input.readBoolean()) {
                        throw IOException("فشل التحقق من الملف «${f.name}» على الهاتف الآخر.")
                    }
                    emit(true, f.name, i, files.size, f.size, f.size, meter, true)
                }
                finishSession(History.OK)
                _state.value = UiState.Done(true, files.map { SavedFile(it.name, it.size, null, null) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    finishSession(History.FAILED)
                    _state.value = UiState.Failed(describe(e))
                }
            } finally {
                runCatching { sock.close() }
                if (active === sock) active = null
            }
        }
    }

    // ───────────────────────── أدوات ─────────────────────────

    private class Meter(val total: Long) {
        var done = 0L
        var speed = 0L
        private var lastT = 0L
        private var lastDone = 0L

        fun tick(force: Boolean): Boolean {
            val now = System.nanoTime()
            if (!force && now - lastT < 250_000_000L) return false
            if (lastT != 0L && now > lastT) {
                val inst = (done - lastDone) * 1_000_000_000L / (now - lastT)
                speed = if (speed == 0L) inst else (speed * 6 + inst * 4) / 10
            }
            lastT = now
            lastDone = done
            return true
        }
    }

    private fun emit(
        sending: Boolean, name: String, idx: Int, count: Int,
        fileDone: Long, fileTotal: Long, m: Meter, force: Boolean
    ) {
        if (m.tick(force)) {
            _state.value = UiState.Progress(
                sending, name, idx + 1, count, fileDone, fileTotal, m.done, m.total, m.speed
            )
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "انتهت مهلة الاتصال. تأكد أن الهاتفين على الشبكة نفسها."
        is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            "تعذّر الاتصال بالهاتف الآخر. تحقق من العنوان ومن أنه في وضع «استلام»."
        is EOFException, is SocketException -> "انقطع الاتصال أثناء النقل."
        is IOException -> e.message ?: "حدث خطأ أثناء النقل."
        else -> e.message ?: "حدث خطأ غير متوقع."
    }
}
