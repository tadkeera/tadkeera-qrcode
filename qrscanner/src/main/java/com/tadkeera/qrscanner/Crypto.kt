package com.tadkeera.qrscanner

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypt-then-MAC container.
 *
 * Layout: MAGIC (5 bytes) | IV (16) | AES-256-CBC ciphertext | HMAC-SHA256 (32)
 * The HMAC covers everything before it. Any changed byte makes decrypt() return null
 * before any data is parsed.
 *
 * The main app uses the same derivation for event bundles ("TKEV1") and reports ("TKRP1").
 */
object Container {
    const val MAGIC_BUNDLE = "TKEV1"
    const val MAGIC_REPORT = "TKRP1"
    const val MAGIC_STORE = "TKST1"

    private const val IV_SIZE = 16
    private const val MAC_SIZE = 32
    private const val SECRET = "TadkeeraEventBundle::2026::v1"

    private fun deriveKey(label: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("$SECRET|$label".toByteArray(Charsets.UTF_8))

    fun encrypt(magic: String, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey("enc"), "AES"), IvParameterSpec(iv))
        val body = cipher.doFinal(plain)
        val header = magic.toByteArray(Charsets.US_ASCII) + iv
        return header + body + macOf(header + body)
    }

    /** Returns the plain payload, or null if the magic is wrong or the file was modified. */
    fun decrypt(magic: String, data: ByteArray): ByteArray? {
        val headerSize = magic.length + IV_SIZE
        if (data.size <= headerSize + MAC_SIZE) return null
        if (String(data, 0, magic.length, Charsets.US_ASCII) != magic) return null
        val signed = data.copyOfRange(0, data.size - MAC_SIZE)
        val tag = data.copyOfRange(data.size - MAC_SIZE, data.size)
        if (!MessageDigest.isEqual(macOf(signed), tag)) return null
        val iv = data.copyOfRange(magic.length, headerSize)
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
