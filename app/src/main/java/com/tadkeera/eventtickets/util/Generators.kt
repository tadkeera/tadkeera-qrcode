package com.tadkeera.eventtickets.util

import com.tadkeera.eventtickets.data.dao.TicketDao
import java.security.SecureRandom

object QRCodeGenerator {
    private val secureRandom = SecureRandom()
    private const val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val QR_LENGTH = 24
    
    suspend fun generateUniqueQRCode(ticketDao: TicketDao): String {
        var qrCode: String
        var attempts = 0
        val maxAttempts = 100
        
        do {
            qrCode = generateRandomCode()
            val existing = ticketDao.getTicketByQR(qrCode)
            attempts++
            
            if (attempts >= maxAttempts) {
                throw Exception("Failed to generate unique QR code")
            }
        } while (existing != null)
        
        return qrCode
    }
    
    private fun generateRandomCode(): String {
        return (1..QR_LENGTH)
            .map { CHARSET[secureRandom.nextInt(CHARSET.length)] }
            .joinToString("")
    }
}

object EventCodeGenerator {
    private val secureRandom = SecureRandom()
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars
    private const val CODE_LENGTH = 5
    
    fun generateEventCode(): String {
        return (1..CODE_LENGTH)
            .map { CHARSET[secureRandom.nextInt(CHARSET.length)] }
            .joinToString("")
    }
}
