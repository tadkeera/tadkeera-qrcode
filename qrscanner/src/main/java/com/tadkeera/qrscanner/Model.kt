package com.tadkeera.qrscanner

import org.json.JSONArray
import org.json.JSONObject

data class BundleUser(val name: String, val accessCode: String)

data class BundleTicket(
    val ticketNumber: Int,
    val eventCode: String,
    val qrCodeData: String,
    val guestName: String
)

/** Event data exported from the main Tadkeera app. Read-only on the scanner. */
data class EventBundle(
    val eventId: String,
    val eventName: String,
    val eventCode: String,
    val endDate: Long,
    val activationCode: String,
    val exportedAt: Long,
    val users: List<BundleUser>,
    val tickets: List<BundleTicket>
) {
    val ticketsByQr: Map<String, BundleTicket> by lazy { tickets.associateBy { it.qrCodeData } }

    companion object {
        /** Throws IllegalArgumentException when the JSON is not a valid, consistent bundle. */
        fun parse(json: String): EventBundle {
            val root = JSONObject(json)
            require(root.optString("format") == "tadkeera-event-v1") { "format" }
            val ev = root.getJSONObject("event")
            val eventId = ev.getString("eventId")
            require(eventId.isNotBlank()) { "eventId" }

            val usersArr = root.getJSONArray("users")
            val users = (0 until usersArr.length()).map {
                val u = usersArr.getJSONObject(it)
                BundleUser(u.getString("name"), u.getString("accessCode"))
            }
            require(users.isNotEmpty()) { "users" }
            require(users.all { it.accessCode.length == 8 }) { "accessCode" }

            val ticketsArr = root.getJSONArray("tickets")
            val tickets = (0 until ticketsArr.length()).map {
                val t = ticketsArr.getJSONObject(it)
                BundleTicket(
                    ticketNumber = t.getInt("ticketNumber"),
                    eventCode = t.getString("eventCode"),
                    qrCodeData = t.getString("qrCodeData"),
                    guestName = t.optString("guestName", "")
                )
            }
            require(tickets.isNotEmpty()) { "tickets" }
            require(tickets.map { it.qrCodeData }.toSet().size == tickets.size) { "duplicate qr" }

            return EventBundle(
                eventId = eventId,
                eventName = ev.optString("eventName", ""),
                eventCode = ev.optString("eventCode", ""),
                endDate = ev.optLong("endDate", 0L),
                activationCode = ev.optString("activationCode", ""),
                exportedAt = root.optLong("exportedAt", 0L),
                users = users,
                tickets = tickets
            )
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("format", "tadkeera-event-v1")
        put("exportedAt", exportedAt)
        put("event", JSONObject()
            .put("eventId", eventId)
            .put("eventName", eventName)
            .put("eventCode", eventCode)
            .put("endDate", endDate)
            .put("activationCode", activationCode))
        put("users", JSONArray().also { arr ->
            users.forEach { arr.put(JSONObject().put("name", it.name).put("accessCode", it.accessCode)) }
        })
        put("tickets", JSONArray().also { arr ->
            tickets.forEach {
                arr.put(JSONObject()
                    .put("ticketNumber", it.ticketNumber)
                    .put("eventCode", it.eventCode)
                    .put("qrCodeData", it.qrCodeData)
                    .put("guestName", it.guestName))
            }
        })
    }.toString()
}

/** One entry (IN) or exit (OUT) recorded by this scanner. */
data class ScanEntry(val qr: String, val type: String, val ts: Long, val operator: String)

/** A scan that did not match any ticket of the event. */
data class ForgeryEntry(val preview: String, val reason: String, val ts: Long)

/** Everything the scanner remembers on the device (stored encrypted). */
data class ScanState(
    var eventId: String = "",
    var activatedCode: String = "",
    var clockOffsetMs: Long = 0L,
    var hasClockOffset: Boolean = false,
    var lastSeen: Long = 0L,
    val scans: MutableList<ScanEntry> = mutableListOf(),
    val forgeries: MutableList<ForgeryEntry> = mutableListOf()
) {
    fun toJson(): String = JSONObject()
        .put("eventId", eventId)
        .put("activatedCode", activatedCode)
        .put("clockOffsetMs", clockOffsetMs)
        .put("hasClockOffset", hasClockOffset)
        .put("lastSeen", lastSeen)
        .put("scans", JSONArray().also { arr ->
            scans.forEach { arr.put(JSONObject().put("qr", it.qr).put("type", it.type).put("ts", it.ts).put("operator", it.operator)) }
        })
        .put("forgeries", JSONArray().also { arr ->
            forgeries.forEach { arr.put(JSONObject().put("preview", it.preview).put("reason", it.reason).put("ts", it.ts)) }
        })
        .toString()

    companion object {
        fun fromJson(json: String): ScanState {
            val o = JSONObject(json)
            val st = ScanState(
                eventId = o.optString("eventId"),
                activatedCode = o.optString("activatedCode"),
                clockOffsetMs = o.optLong("clockOffsetMs"),
                hasClockOffset = o.optBoolean("hasClockOffset"),
                lastSeen = o.optLong("lastSeen")
            )
            val scans = o.optJSONArray("scans") ?: JSONArray()
            for (i in 0 until scans.length()) {
                val s = scans.getJSONObject(i)
                st.scans.add(ScanEntry(s.getString("qr"), s.getString("type"), s.getLong("ts"), s.optString("operator")))
            }
            val fz = o.optJSONArray("forgeries") ?: JSONArray()
            for (i in 0 until fz.length()) {
                val f = fz.getJSONObject(i)
                st.forgeries.add(ForgeryEntry(f.getString("preview"), f.getString("reason"), f.getLong("ts")))
            }
            return st
        }
    }
}
