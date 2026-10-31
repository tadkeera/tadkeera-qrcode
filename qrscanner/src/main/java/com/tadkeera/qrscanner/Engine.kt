package com.tadkeera.qrscanner

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Encrypted on-device storage. Nothing is kept in plain text. */
class ScannerStore(context: Context) {
    private val dir = File(context.filesDir, "qrs").apply { if (!exists()) mkdirs() }
    private val bundleFile = File(dir, "bundle.bin")
    private val stateFile = File(dir, "state.bin")

    fun loadBundle(): EventBundle? {
        if (!bundleFile.exists()) return null
        val plain = Container.decrypt(Container.MAGIC_STORE, bundleFile.readBytes()) ?: return null
        return runCatching { EventBundle.parse(String(plain, Charsets.UTF_8)) }.getOrNull()
    }

    fun saveBundle(bundle: EventBundle) {
        bundleFile.writeBytes(Container.encrypt(Container.MAGIC_STORE, bundle.toJson().toByteArray(Charsets.UTF_8)))
    }

    fun loadState(): ScanState? {
        if (!stateFile.exists()) return null
        val plain = Container.decrypt(Container.MAGIC_STORE, stateFile.readBytes()) ?: return null
        return runCatching { ScanState.fromJson(String(plain, Charsets.UTF_8)) }.getOrNull()
    }

    fun saveState(state: ScanState) {
        stateFile.writeBytes(Container.encrypt(Container.MAGIC_STORE, state.toJson().toByteArray(Charsets.UTF_8)))
    }
}

/**
 * Clock protection.
 * - The first activation needs the internet: the server's time is compared with the device clock
 *   and the difference is stored (encrypted).
 * - Every later launch checks that the device clock never goes backwards compared to the last
 *   time the scanner saw. Setting the clock back is detected and the scanner locks itself.
 */
object TrustedClock {
    private const val TOLERANCE_MS = 5 * 60 * 1000L

    /** Returns server time in millis, or null when the internet is not reachable. */
    fun fetchServerTimeMs(): Long? = try {
        val conn = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.useCaches = false
        conn.connect()
        val dateHeader = conn.getHeaderField("Date")
        conn.disconnect()
        dateHeader?.let {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }
                .parse(it)?.time
        }
    } catch (e: Exception) {
        null
    }

    /** Current trusted time, or null if the device clock was moved back. */
    fun now(state: ScanState): Long? {
        val device = System.currentTimeMillis()
        if (device + TOLERANCE_MS < state.lastSeen) return null
        state.lastSeen = maxOf(state.lastSeen, device)
        return device + state.clockOffsetMs
    }
}

enum class Mode { ENTRY, EXIT }

sealed class Verdict {
    data class Entered(val ticket: BundleTicket) : Verdict()
    data class Duplicate(val ticket: BundleTicket, val lastEntry: Long) : Verdict()
    data class Exited(val ticket: BundleTicket) : Verdict()
    data class NotInside(val ticket: BundleTicket) : Verdict()
    data class Forged(val preview: String, val reason: String) : Verdict()
}

data class Stats(val total: Int, val everEntered: Int, val inside: Int, val forgeries: Int) {
    val remaining: Int get() = total - everEntered
}

object ScanEngine {
    fun isInside(state: ScanState, qr: String): Boolean {
        val last = state.scans.lastOrNull { it.qr == qr } ?: return false
        return last.type == "IN"
    }

    /** Applies the scan rules and records the result in [state] when it is accepted. */
    fun evaluate(bundle: EventBundle, state: ScanState, qr: String, mode: Mode, now: Long, operator: String): Verdict {
        val ticket = bundle.ticketsByQr[qr]
        if (ticket == null) {
            val preview = qr.take(8) + "…"
            state.forgeries.add(ForgeryEntry(preview, "كود غير مسجل في المناسبة", now))
            return Verdict.Forged(preview, "كود غير مسجل في المناسبة")
        }
        val inside = isInside(state, qr)
        return when (mode) {
            Mode.ENTRY -> {
                if (inside) {
                    val lastEntry = state.scans.last { it.qr == qr && it.type == "IN" }.ts
                    Verdict.Duplicate(ticket, lastEntry)
                } else {
                    state.scans.add(ScanEntry(qr, "IN", now, operator))
                    Verdict.Entered(ticket)
                }
            }
            Mode.EXIT -> {
                if (!inside) {
                    Verdict.NotInside(ticket)
                } else {
                    state.scans.add(ScanEntry(qr, "OUT", now, operator))
                    Verdict.Exited(ticket)
                }
            }
        }
    }

    fun stats(bundle: EventBundle, state: ScanState): Stats {
        val entered = HashSet<String>()
        val inside = HashSet<String>()
        for (s in state.scans) {
            if (!bundle.ticketsByQr.containsKey(s.qr)) continue
            if (s.type == "IN") entered.add(s.qr)
            if (s.type == "IN") inside.add(s.qr) else inside.remove(s.qr)
        }
        return Stats(
            total = bundle.tickets.size,
            everEntered = entered.size,
            inside = inside.size,
            forgeries = state.forgeries.size
        )
    }
}
