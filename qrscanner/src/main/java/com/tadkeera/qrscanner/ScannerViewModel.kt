package com.tadkeera.qrscanner

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Base64
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ScannerStore(app)
    private val prefs = app.getSharedPreferences("qrs_prefs", Context.MODE_PRIVATE)
    private var state: ScanState = store.loadState() ?: ScanState()

    var bundle by mutableStateOf(store.loadBundle())
        private set
    var operatorName by mutableStateOf(prefs.getString("operator", "") ?: "")
        private set
    var p2pMode by mutableStateOf(prefs.getString("p2p", ROLE_STANDALONE) ?: ROLE_STANDALONE)
        private set
    var scanMode by mutableStateOf(Mode.ENTRY)
    var stats by mutableStateOf<Stats?>(null)
        private set
    /** The result popup. Null means the camera is ready for the next ticket. */
    var lastVerdict by mutableStateOf<Verdict?>(null)
        private set
    /** Set when scanning must stop (clock tampering or expired event). */
    var lockMessage by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var flashOn by mutableStateOf(false)
        private set
    /** ORGANIZER: true while connected to the host. */
    var linkConnected by mutableStateOf(false)
        private set
    /** HOST: organizers that connected (or were connected) to this device. */
    var devices by mutableStateOf<List<DeviceStatus>>(emptyList())
        private set

    private var cameraControl: CameraControl? = null
    private var meteringFactory: MeteringPointFactory? = null
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull()
    private var host: P2PHost? = null
    private var client: P2PClient? = null

    private var lastCode = ""
    private var lastCodeAt = 0L
    private var clearJob: Job? = null
    private var noticeJob: Job? = null

    /** True when the app starts with a valid saved login (the user must not log in again). */
    var sessionRestored = false
        private set

    init {
        sessionRestored = restoreSession()
        refreshStats()
    }

    /**
     * The login lasts until the user logs out, even if the app was closed.
     * It is dropped only if the event has expired or the device clock was tampered with.
     */
    private fun restoreSession(): Boolean {
        if (!prefs.getBoolean(KEY_SESSION, false)) return false
        val b = bundle ?: return false
        if (!state.hasClockOffset) return false
        val now = TrustedClock.now(state)
        if (now == null || now > b.endDate) {
            prefs.edit().putBoolean(KEY_SESSION, false).apply()
            return false
        }
        store.saveState(state)
        startNetwork()
        return true
    }

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { block() }
    }

    private fun refreshStats() {
        val b = bundle ?: return
        stats = ScanEngine.stats(b, state)
    }

    private fun persist() {
        store.saveState(state)
        refreshStats()
    }

    /** Returns an error message, or null when the import succeeded. */
    fun importBundle(bytes: ByteArray): String? {
        val plain = Container.decrypt(Container.MAGIC_BUNDLE, bytes)
            ?: return "البيانات غير صالحة: الملف تالف أو ليس ملف مناسبة صادراً من تطبيق تذكرة."
        val parsed = runCatching { EventBundle.parse(String(plain, Charsets.UTF_8)) }.getOrNull()
            ?: return "البيانات غير صالحة: بنية الملف غير مكتملة."

        // One event per device: importing another event clears the previous scan history.
        if (state.eventId != parsed.eventId) {
            state = ScanState(eventId = parsed.eventId)
        }
        store.saveBundle(parsed)
        store.saveState(state)
        bundle = parsed
        refreshStats()
        return null
    }

    /** Checks user code, activation code, expiry and clock. Returns an error message or null. */
    suspend fun activate(userCode: String, activationCode: String): String? {
        val b = bundle ?: return "الرجاء استيراد بيانات المناسبة أولاً."
        val user = b.users.firstOrNull { it.accessCode == userCode.trim().uppercase() }
            ?: return "كود المستخدم غير صحيح."
        if (activationCode.trim().uppercase() != b.activationCode || b.activationCode.isEmpty()) {
            return "كود التفعيل غير صحيح."
        }
        if (b.endDate <= 0L) return "لم يتم تحديد تاريخ انتهاء المناسبة في البيانات."

        if (!state.hasClockOffset) {
            // First activation on this device: the internet is required to verify the date.
            val serverTime = withContext(Dispatchers.IO) { TrustedClock.fetchServerTimeMs() }
                ?: return "يجب توفر الإنترنت في أول تفعيل للتحقق من تاريخ الجهاز."
            state.clockOffsetMs = serverTime - System.currentTimeMillis()
            state.hasClockOffset = true
        }
        val now = TrustedClock.now(state)
            ?: return "تم اكتشاف تلاعب في تاريخ الجهاز. لا يمكن فتح الماسح."
        if (now > b.endDate) return "بيانات تذاكر هذه المناسبة منتهية."

        state.activatedCode = b.activationCode
        store.saveState(state)
        operatorName = user.name
        prefs.edit().putString("operator", user.name).putBoolean(KEY_SESSION, true).apply()
        startNetwork()
        return null
    }

    /** Ends the login. This is the only way the saved session is cleared. */
    fun logout() {
        stopNetwork()
        prefs.edit().putBoolean(KEY_SESSION, false).apply()
    }

    // ---------------- Camera controls ----------------

    fun attachCamera(control: CameraControl, factory: MeteringPointFactory) {
        cameraControl = control
        meteringFactory = factory
        control.enableTorch(flashOn)
    }

    fun toggleFlash() {
        flashOn = !flashOn
        cameraControl?.enableTorch(flashOn)
    }

    /** Focuses on the centre of the camera view (the QR is usually centred). */
    fun focusCenter() {
        val factory = meteringFactory ?: return
        cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(factory.createPoint(0.5f, 0.5f)).build()
        )
    }

    // ---------------- Scanning ----------------

    fun onScanned(qr: String) = handleScan(qr, manual = false)

    /** Manual check when the camera could not read the code. */
    fun verifyManual(eventCode: String, ticketNo: String) {
        val number = ticketNo.toIntOrNull()
        val ticket = if (number == null) null else findTicket(eventCode, number)
        if (ticket == null) {
            showNotice("لم يتم العثور على تذكرة بهذا الكود والرقم")
            return
        }
        handleScan(ticket.qrCodeData, manual = true)
    }

    private fun handleScan(qr: String, manual: Boolean) {
        if (lastVerdict != null || lockMessage != null) return
        val b = bundle ?: return
        val nowWall = System.currentTimeMillis()
        if (!manual && qr == lastCode && nowWall - lastCodeAt < SAME_CODE_COOLDOWN_MS) return
        lastCode = qr
        lastCodeAt = nowWall

        val now = TrustedClock.now(state)
        if (now == null) {
            lockMessage = "تم اكتشاف تلاعب في تاريخ الجهاز. تم إيقاف الماسح."
            return
        }
        if (now > b.endDate) {
            lockMessage = "بيانات تذاكر هذه المناسبة منتهية."
            return
        }

        if (p2pMode == ROLE_ORGANIZER) {
            // The host decides; the result comes back as a RESULT message.
            if (!linkConnected) {
                showNotice("غير متصل بجهاز المستضيف")
                return
            }
            client?.send(JSONObject().put("t", "SCAN").put("qr", qr)
                .put("mode", if (scanMode == Mode.EXIT) "OUT" else "IN"))
            return
        }
        presentVerdict(processLocally(b, qr, scanMode, now, operatorName))
    }

    /** Applies the scan rules on this device; a HOST also broadcasts the change to organizers. */
    private fun processLocally(b: EventBundle, qr: String, mode: Mode, now: Long, operator: String): Verdict {
        val scansBefore = state.scans.size
        val forgeriesBefore = state.forgeries.size
        val verdict = ScanEngine.evaluate(b, state, qr, mode, now, operator)
        persist()
        if (p2pMode == ROLE_HOST) {
            if (state.scans.size > scansBefore) host?.broadcast(entryMessage(state.scans.last()))
            if (state.forgeries.size > forgeriesBefore) host?.broadcast(forgeryMessage(state.forgeries.last()))
        }
        return verdict
    }

    private fun presentVerdict(v: Verdict) {
        val ok = v is Verdict.Entered || v is Verdict.Exited
        playTone(ok)
        lastVerdict = v
        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            delay(RESULT_AUTO_CLOSE_MS)
            lastVerdict = null
        }
    }

    /** Success tone for valid tickets, warning tone for invalid or duplicate ones. */
    private fun playTone(ok: Boolean) {
        runCatching {
            if (ok) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            else tone?.startTone(ToneGenerator.TONE_SUP_ERROR, 600)
        }
    }

    private fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(2500)
            notice = null
        }
    }

    fun dismissResult() {
        clearJob?.cancel()
        lastVerdict = null
    }

    fun dismissLock() {
        lockMessage = null
    }

    fun setMode(newMode: Mode) {
        scanMode = newMode
    }

    fun setP2P(value: String) {
        p2pMode = value
        prefs.edit().putString("p2p", value).apply()
        if (operatorName.isNotEmpty()) startNetwork()
    }

    fun findTicket(eventCode: String, ticketNumber: Int): BundleTicket? {
        val b = bundle ?: return null
        return b.tickets.firstOrNull {
            it.eventCode.equals(eventCode.trim(), ignoreCase = true) && it.ticketNumber == ticketNumber
        }
    }

    /** Every IN/OUT movement of one ticket, oldest first. */
    fun timelineOf(ticket: BundleTicket): List<ScanEntry> =
        state.scans.filter { it.qr == ticket.qrCodeData }

    // ---------------- Local network (P2P) ----------------

    fun startNetwork() {
        stopNetwork()
        val b = bundle ?: return
        val app = getApplication<Application>()
        when (p2pMode) {
            ROLE_HOST -> {
                host = P2PHost(
                    app, viewModelScope, b.eventId,
                    onMessage = { id, obj -> onMain { onHostMessage(id, obj) } },
                    onDevices = { list -> onMain { devices = list } }
                ).also { it.start() }
            }
            ROLE_ORGANIZER -> {
                val name = (operatorName.ifEmpty { "مستخدم" }) + " - " + Build.MODEL
                client = P2PClient(
                    app, viewModelScope, b.eventId,
                    onMessage = { obj -> onMain { onClientMessage(obj) } },
                    onStatus = { up -> onMain { linkConnected = up } }
                ).also { it.start(name) }
            }
        }
    }

    fun stopNetwork() {
        host?.stop()
        host = null
        client?.stop()
        client = null
        linkConnected = false
        devices = emptyList()
    }

    /** HOST: an organizer sent a message. */
    private fun onHostMessage(peerId: String, obj: JSONObject) {
        val b = bundle ?: return
        val h = host ?: return
        when (obj.optString("t")) {
            "HELLO" -> {
                h.sendTo(peerId, JSONObject().put("t", "BUNDLE").put("data", encodeBundle(b)))
                h.sendTo(peerId, snapshotMessage())
            }
            "SCAN" -> {
                val now = TrustedClock.now(state) ?: return
                val qr = obj.optString("qr")
                val mode = if (obj.optString("mode") == "OUT") Mode.EXIT else Mode.ENTRY
                val verdict = processLocally(b, qr, mode, now, h.nameOf(peerId))
                h.sendTo(peerId, resultMessage(verdict, qr))
            }
        }
    }

    /** ORGANIZER: a message from the host. */
    private fun onClientMessage(obj: JSONObject) {
        when (obj.optString("t")) {
            "BUNDLE" -> {
                val bytes = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
                val plain = Container.decrypt(Container.MAGIC_BUNDLE, bytes) ?: return
                val parsed = runCatching { EventBundle.parse(String(plain, Charsets.UTF_8)) }.getOrNull() ?: return
                if (state.eventId != parsed.eventId) {
                    state = ScanState(
                        eventId = parsed.eventId,
                        activatedCode = state.activatedCode,
                        clockOffsetMs = state.clockOffsetMs,
                        hasClockOffset = state.hasClockOffset,
                        lastSeen = state.lastSeen
                    )
                }
                store.saveBundle(parsed)
                bundle = parsed
                persist()
            }
            "STATE" -> {
                state.scans.clear()
                state.scans.addAll(parseScans(obj.optJSONArray("scans")))
                state.forgeries.clear()
                state.forgeries.addAll(parseForgeries(obj.optJSONArray("forgeries")))
                persist()
            }
            "SCAN_ENTRY" -> {
                state.scans.add(scanFromJson(obj.getJSONObject("entry")))
                persist()
            }
            "FORGERY" -> {
                state.forgeries.add(forgeryFromJson(obj.getJSONObject("entry")))
                persist()
            }
            "RESULT" -> {
                val b = bundle ?: return
                presentVerdict(verdictFromJson(obj, b))
            }
        }
    }

    private fun encodeBundle(b: EventBundle): String =
        Base64.encodeToString(
            Container.encrypt(Container.MAGIC_BUNDLE, b.toJson().toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

    private fun snapshotMessage(): JSONObject = JSONObject()
        .put("t", "STATE")
        .put("scans", JSONArray().also { arr -> state.scans.forEach { arr.put(scanToJson(it)) } })
        .put("forgeries", JSONArray().also { arr -> state.forgeries.forEach { arr.put(forgeryToJson(it)) } })

    private fun entryMessage(s: ScanEntry): JSONObject =
        JSONObject().put("t", "SCAN_ENTRY").put("entry", scanToJson(s))

    private fun forgeryMessage(f: ForgeryEntry): JSONObject =
        JSONObject().put("t", "FORGERY").put("entry", forgeryToJson(f))

    // ---------------- Report ----------------

    /** Encrypted report file for the main app (attendance, forgeries). */
    fun buildReportFile(): File? {
        val b = bundle ?: return null
        val exportedAt = TrustedClock.now(state) ?: System.currentTimeMillis()
        val payload = JSONObject()
            .put("format", "tadkeera-report-v1")
            .put("eventId", b.eventId)
            .put("eventCode", b.eventCode)
            .put("eventName", b.eventName)
            .put("exportedAt", exportedAt)
            .put("operator", operatorName)
            .put("scans", JSONArray().also { arr -> state.scans.forEach { arr.put(scanToJson(it)) } })
            .put("forgeries", JSONArray().also { arr -> state.forgeries.forEach { arr.put(forgeryToJson(it)) } })
        val bytes = Container.encrypt(Container.MAGIC_REPORT, payload.toString().toByteArray(Charsets.UTF_8))
        val dir = File(getApplication<Application>().cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safe = b.eventCode.ifEmpty { "event" }.replace(Regex("[^A-Za-z0-9_-]"), "")
        val file = File(dir, "Tadkeera_Report_$safe.tkrpt")
        file.writeBytes(bytes)
        return file
    }

    override fun onCleared() {
        stopNetwork()
        runCatching { tone?.release() }
        super.onCleared()
    }

    companion object {
        const val ROLE_STANDALONE = "STANDALONE"
        const val ROLE_ORGANIZER = "ORGANIZER"
        const val ROLE_HOST = "HOST"
        private const val KEY_SESSION = "logged_in"
        private const val RESULT_AUTO_CLOSE_MS = 1500L
        private const val SAME_CODE_COOLDOWN_MS = 4000L
    }
}

// ---------------- JSON helpers for the P2P messages ----------------

private fun scanToJson(s: ScanEntry): JSONObject =
    JSONObject().put("qr", s.qr).put("type", s.type).put("ts", s.ts).put("operator", s.operator)

private fun scanFromJson(o: JSONObject): ScanEntry =
    ScanEntry(o.getString("qr"), o.getString("type"), o.getLong("ts"), o.optString("operator"))

private fun forgeryToJson(f: ForgeryEntry): JSONObject =
    JSONObject().put("preview", f.preview).put("reason", f.reason).put("ts", f.ts)

private fun forgeryFromJson(o: JSONObject): ForgeryEntry =
    ForgeryEntry(o.getString("preview"), o.getString("reason"), o.getLong("ts"))

private fun parseScans(arr: JSONArray?): List<ScanEntry> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { scanFromJson(arr.getJSONObject(it)) }
}

private fun parseForgeries(arr: JSONArray?): List<ForgeryEntry> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { forgeryFromJson(arr.getJSONObject(it)) }
}

private fun resultMessage(v: Verdict, qr: String): JSONObject {
    val o = JSONObject().put("t", "RESULT").put("qr", qr)
    when (v) {
        is Verdict.Entered -> o.put("kind", "ENTERED")
        is Verdict.Duplicate -> o.put("kind", "DUP").put("lastEntry", v.lastEntry)
        is Verdict.Exited -> o.put("kind", "EXITED")
        is Verdict.NotInside -> o.put("kind", "NOT_INSIDE")
        is Verdict.Forged -> o.put("kind", "FORGED").put("preview", v.preview).put("reason", v.reason)
    }
    return o
}

private fun verdictFromJson(o: JSONObject, b: EventBundle): Verdict {
    val qr = o.optString("qr")
    val ticket = b.ticketsByQr[qr]
    if (ticket == null) {
        return Verdict.Forged(o.optString("preview", qr.take(8) + "…"), o.optString("reason", "كود غير مسجل في المناسبة"))
    }
    return when (o.optString("kind")) {
        "ENTERED" -> Verdict.Entered(ticket)
        "DUP" -> Verdict.Duplicate(ticket, o.optLong("lastEntry"))
        "EXITED" -> Verdict.Exited(ticket)
        "NOT_INSIDE" -> Verdict.NotInside(ticket)
        else -> Verdict.Forged(o.optString("preview", qr.take(8) + "…"), o.optString("reason", "تذكرة غير صالحة"))
    }
}
