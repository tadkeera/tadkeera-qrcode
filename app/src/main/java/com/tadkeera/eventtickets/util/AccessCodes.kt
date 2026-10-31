package com.tadkeera.eventtickets.util

import java.security.SecureRandom

/**
 * Random, human-typeable codes. Ambiguous characters (0/O, 1/I) are excluded.
 */
object AccessCodes {
    private val secureRandom = SecureRandom()
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** 8-character access code for a scanner user. */
    fun newUserCode(): String = random(8)

    /** Activation code issued whenever the event end date is set or changed. */
    fun newActivationCode(): String = random(10)

    private fun random(length: Int): String =
        (1..length).map { CHARSET[secureRandom.nextInt(CHARSET.length)] }.joinToString("")
}
