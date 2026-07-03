package com.token2.burner3.otp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP, used only to show a live verification code on the success
 * screen so the user can confirm the token now matches. This is *display-side*
 * verification; the token itself computes its own codes independently.
 */
object Totp {

    fun generate(
        secret: ByteArray,
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30,
        forEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): String {
        val counter = forEpochSeconds / periodSeconds
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val algoName = when (algorithm) {
            OtpAlgorithm.SHA1 -> "HmacSHA1"
            OtpAlgorithm.SHA256 -> "HmacSHA256"
        }
        val mac = Mac.getInstance(algoName)
        mac.init(SecretKeySpec(secret, "RAW"))
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % pow10(digits)
        return otp.toString().padStart(digits, '0')
    }

    fun secondsLeft(periodSeconds: Int = 30, forEpochSeconds: Long = System.currentTimeMillis() / 1000): Int =
        (periodSeconds - (forEpochSeconds % periodSeconds)).toInt()

    private fun pow10(n: Int): Int {
        var r = 1
        repeat(n) { r *= 10 }
        return r
    }
}
