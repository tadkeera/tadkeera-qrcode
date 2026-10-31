package com.tadkeera.qrscanner

import android.app.Application
import android.content.Context
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
    var p2pMode by mutableStateOf(prefs.getString("p2p", "STANDALONE") ?: "STANDALONE")
    var scanMode by mutableStateOf(Mode.ENTRY)
    var stats by mutableStateOf<Stats?>(null)
        private set
    /** The result popup. Null means the camera is ready for the next ticket. */
    var lastVerdict by mutableStateOf<Verdict?>(null)
        private set
    /** Set when scanning must stop (clock tampering or expired event). */
    var lockMessage by mutableStateOf<String?>(null)
        private set

    private var lastCode = ""
    private var lastCodeAt = 0L
    private var clearJob: Job? = null

    init {
        refreshStats()
    }

    private fun refreshStats() {
        val b = bundle ?: return
        stats = ScanEngine.stats(b, state)
    }

    /** Returns an error message, or null when the import succeeded. */
    fun importBundle(bytes: ByteArray): String? {
        val plain = Container.decrypt(Container.MAGIC_BUNDLE, bytes)
            ?: return "البيانات غير صالحة: الملف تالف أو ليس ملف مناسبة صادراً من تطبيق تذكرة."
        val parsed = runCatching { EventBundle.parse(String(plain, Charsets.UTF_8)) }.getOrNull()
            ?: return "البيانات غير صالحة: بنية الملف غير مكتملة."

        if (state.eventId != parsed.eventId) {
            // A different event replaces the previous one (scan history is per event).
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
        prefs.edit().putString("operator", user.name).apply()
        return null
    }

    /** Called for every QR code the camera reads. */
    fun onScanned(qr: String) {
        if (lastVerdict != null || lockMessage != null) return
        val b = bundle ?: return
        val nowWall = System.currentTimeMillis()
        if (qr == lastCode && nowWall - lastCodeAt < SAME_CODE_COOLDOWN_MS) return
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

        val verdict = ScanEngine.evaluate(b, state, qr, scanMode, now, operatorName)
        store.saveState(state)
        refreshStats()
        lastVerdict = verdict

        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            delay(RESULT_AUTO_CLOSE_MS)
            lastVerdict = null
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

    /** Encrypted report file for the main app (attendance, forgeries). */
    fun buildReportFile(): File? {
        val b = bundle ?: return null
        val exportedAt = TrustedClock.now(state) ?: System.currentTimeMillis()
        val scans = JSONArray().also { arr ->
            state.scans.forEach {
                arr.put(JSONObject().put("qr", it.qr).put("type", it.type).put("ts", it.ts).put("operator", it.operator))
            }
        }
        val forgeries = JSONArray().also { arr ->
            state.forgeries.forEach {
                arr.put(JSONObject().put("preview", it.preview).put("reason", it.reason).put("ts", it.ts))
            }
        }
        val payload = JSONObject()
            .put("format", "tadkeera-report-v1")
            .put("eventId", b.eventId)
            .put("eventCode", b.eventCode)
            .put("eventName", b.eventName)
            .put("exportedAt", exportedAt)
            .put("operator", operatorName)
            .put("scans", scans)
            .put("forgeries", forgeries)
        val bytes = Container.encrypt(Container.MAGIC_REPORT, payload.toString().toByteArray(Charsets.UTF_8))
        val dir = File(getApplication<Application>().cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safe = b.eventCode.ifEmpty { "event" }.replace(Regex("[^A-Za-z0-9_-]"), "")
        val file = File(dir, "Tadkeera_Report_$safe.tkrpt")
        file.writeBytes(bytes)
        return file
    }

    companion object {
        private const val RESULT_AUTO_CLOSE_MS = 1500L
        private const val SAME_CODE_COOLDOWN_MS = 4000L
    }
}
