package com.tadkeera.eventtickets.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypt-then-MAC container for event bundles exported for the QR-SCANER app.
 *
 * Layout: MAGIC (5 bytes) | IV (16 bytes) | AES-256-CBC ciphertext | HMAC-SHA256 (32 bytes)
 * The HMAC covers MAGIC, IV and the ciphertext. Any byte changed in the file makes the
 * import fail before any data is read.
 *
 * The scanner app contains the same derivation, so it can open these files offline.
 */
object EventExportCrypto {
    private const val MAGIC = "TKEV1"
    private const val IV_SIZE = 16
    private const val MAC_SIZE = 32
    private const val SECRET = "TadkeeraEventBundle::2026::v1"

    private fun deriveKey(label: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$SECRET|$label".toByteArray(Charsets.UTF_8))

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey("enc"), "AES"), IvParameterSpec(iv))
        val body = cipher.doFinal(plain)
        val header = MAGIC.toByteArray(Charsets.US_ASCII) + iv
        val tag = macOf(header + body)
        return header + body + tag
    }

    /** Returns the plain payload, or null if the file is not a valid, untampered bundle. */
    fun decrypt(data: ByteArray): ByteArray? {
        val headerSize = MAGIC.length + IV_SIZE
        if (data.size <= headerSize + MAC_SIZE) return null
        if (String(data, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return null
        val signed = data.copyOfRange(0, data.size - MAC_SIZE)
        val tag = data.copyOfRange(data.size - MAC_SIZE, data.size)
        if (!MessageDigest.isEqual(macOf(signed), tag)) return null
        val iv = data.copyOfRange(MAGIC.length, headerSize)
        val body = data.copyOfRange(headerSize, data.size - MAC_SIZE)
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey("enc"), "AES"), IvParameterSpec(iv))
            cipher.doFinal(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun macOf(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deriveKey("mac"), "HmacSHA256"))
        return mac.doFinal(data)
    }
}
