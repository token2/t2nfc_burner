package com.token2.burner3.util

/** Minimal hex helpers used across the protocol layer. */
object Hex {
    fun decode(s: String): ByteArray {
        val clean = s.replace(" ", "")
        require(clean.length % 2 == 0) { "hex string must have even length" }
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    fun encode(b: ByteArray): String =
        b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}
