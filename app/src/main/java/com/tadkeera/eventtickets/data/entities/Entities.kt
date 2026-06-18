package com.tadkeera.eventtickets.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val eventName: String,
    val eventDate: Long, // Timestamp
    val createdAt: Long = System.currentTimeMillis(),
    val eventCode: String = "" // Generated during ticket issuance
)

@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey val ticketId: String = UUID.randomUUID().toString(),
    val eventId: String,
    val eventCode: String,
    val ticketNumber: Int,
    val qrCodeData: String, // 24-char unique code
    val guestName: String = "",
    val issuedAt: Long = System.currentTimeMillis(),
    var isScanned: Boolean = false,
    var scannedAt: Long? = null,
    var scanCount: Int = 0
)

@Entity(tableName = "ticket_designs")
data class TicketDesign(
    @PrimaryKey(autoGenerate = true) val designId: Long = 0,
    val eventId: String,
    val designName: String,
    val pdfTemplatePath: String,
    val qrCodeX: Float,
    val qrCodeY: Float,
    val qrCodeWidth: Float,
    val qrCodeHeight: Float,
    val qrCodeRotation: Float = 0f,
    val eventCodeX: Float,
    val eventCodeY: Float,
    val eventCodeSize: Float,
    val guestNameX: Float,
    val guestNameY: Float,
    val guestNameSize: Float,
    val showGuestName: Boolean,
    val isDefault: Boolean = false,
    val eventCodeColor: String = "#C62828",
    val guestNameColor: String = "#2E7D32",
    val guestNameFont: String = "arial.ttf",
    // New fields for absolute positioning and font weights!
    val eventCodeWidth: Float = 0.3f,
    val eventCodeHeight: Float = 0.08f,
    val guestNameWidth: Float = 0.4f,
    val guestNameHeight: Float = 0.08f,
    val eventCodeWeight: String = "bold", // normal, bold, extrabold
    val guestNameWeight: String = "bold"  // normal, bold, extrabold
)

@Entity(tableName = "guest_names")
data class GuestName(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val name: String,
    val isUsed: Boolean = false
)
