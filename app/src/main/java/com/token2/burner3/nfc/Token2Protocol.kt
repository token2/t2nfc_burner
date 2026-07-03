package com.token2.burner3.nfc

import com.token2.burner3.crypto.Sm4
import com.token2.burner3.util.Hex

/**
 * The Token2 2nd-generation single-profile programmable TOTP token protocol.
 *
 * This is a direct, byte-exact port of the official `token2_config.py` reference
 * (ISO 7816 APDUs, SM4 crypto, ISO/IEC 9797-1 MAC). See PROTOCOL.md in the
 * reference repository for the wire format. Nothing here is guessed: the SM4
 * ECB/CBC outputs were validated against the reference implementation's own
 * known-answer values.
 *
 * A [Transport] abstracts the actual APDU exchange so this class stays testable
 * and independent of Android's NFC classes.
 */
class Token2Protocol(private val transport: Transport) {

    /** Abstraction over "send one APDU, get bytes + status word back". */
    interface Transport {
        /**
         * Transmit an APDU and return the full response *including* the trailing
         * 2-byte status word. Throws [TransportException] on I/O failure (tag
         * moved away, connection lost, timeout).
         */
        fun transceive(apdu: ByteArray): ByteArray
    }

    class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Info read from the token without authentication. */
    data class TokenInfo(
        val serial: String,
        val model: String?,
        val utcEpochSeconds: Long,
    ) {
        val isKnownModel: Boolean get() = model != null
    }

    /** The outcome of a write operation, mapped to a user-facing category. */
    sealed interface WriteOutcome {
        data object Success : WriteOutcome
        data object AuthLocked : WriteOutcome          // SW 6983
        data class DeviceRejected(val sw: Int) : WriteOutcome
        data class TransportLost(val message: String) : WriteOutcome
    }

    // -- Reads -----------------------------------------------------------------

    /** Read serial + on-device UTC time. No authentication needed. */
    fun readInfo(): TokenInfo {
        // 80 41 00 00 02  02 11
        val resp = send(0x80, 0x41, 0x00, 0x00, Hex.decode("0211"))
        requireOk(resp)
        val data = resp.copyOfRange(0, resp.size - 2)
        val serialLen = data[3].toInt() and 0xFF
        val serial = String(data.copyOfRange(4, 4 + serialLen), Charsets.US_ASCII)
        val timeOffset = 4 + serialLen + 2
        val secs = ((data[timeOffset].toLong() and 0xFF) shl 24) or
            ((data[timeOffset + 1].toLong() and 0xFF) shl 16) or
            ((data[timeOffset + 2].toLong() and 0xFF) shl 8) or
            (data[timeOffset + 3].toLong() and 0xFF)
        return TokenInfo(serial, modelForSerial(serial), secs)
    }

    /**
     * What kind of device is on the reader. Used to give the user a precise
     * message when they tap the wrong thing — most importantly, a FIDO/OATH
     * security key (e.g. Token2 T2F2-NFC-Card) which *can* hold TOTP profiles,
     * but only via a different app (Libre Key Companion). This app writes to the
     * standalone programmable tokens; it does not manage credentials on a key.
     */
    enum class DeviceKind {
        PROGRAMMABLE_TOKEN,  // one of ours: has the OTPC info applet + known serial
        SECURITY_KEY,        // FIDO2/U2F and/or YKOATH applet present — wrong app
        UNKNOWN_SMARTCARD,   // responds to APDUs but matches nothing we know
        NOT_RESPONSIVE,      // no usable response at all
    }

    data class Identity(
        val kind: DeviceKind,
        val info: TokenInfo? = null,     // populated for PROGRAMMABLE_TOKEN
        val hasFido: Boolean = false,
        val hasOath: Boolean = false,
    )

    /**
     * Non-destructively work out what's on the reader. Order matters: we first
     * try our own info command, and only if that fails do we probe the standard
     * FIDO and OATH applets by SELECT-by-AID. SELECT is read-only and safe.
     */
    fun identify(): Identity {
        // 1) Is it one of our programmable tokens? (info command, no SELECT.)
        val ours = runCatching { readInfo() }.getOrNull()
        if (ours != null && ours.serial.isNotBlank()) {
            // A real programmable token answers the OTPC info command *and* has a
            // serial. Known model → definitely ours; unknown prefix still routes
            // through the normal "unknown model, refuse to write" path.
            return Identity(DeviceKind.PROGRAMMABLE_TOKEN, info = ours)
        }

        // 2) Not ours — probe for FIDO and OATH applets to recognise a key.
        val hasFido = selectSucceeds(AID_FIDO)
        val hasOath = selectSucceeds(AID_YKOATH)
        return when {
            hasFido || hasOath -> Identity(DeviceKind.SECURITY_KEY, hasFido = hasFido, hasOath = hasOath)
            else -> Identity(DeviceKind.UNKNOWN_SMARTCARD)
        }
    }

    /** Issue a SELECT (by name) for [aid] and report whether it returned 9000. */
    private fun selectSucceeds(aid: ByteArray): Boolean {
        // 00 A4 04 00 <Lc> <AID> 00
        val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
        return try {
            val resp = transport.transceive(apdu)
            resp.size >= 2 && statusWord(resp) == 0x9000
        } catch (_: TransportException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    // -- Auth ------------------------------------------------------------------

    /** Challenge-response handshake with the fixed device key. */
    fun authenticate(): Boolean {
        val chalResp = send(0x80, 0x4B, 0x08, 0x00, byteArrayOf(0x00))
        if (!isOk(chalResp)) return false
        val challenge = chalResp.copyOfRange(0, chalResp.size - 2)
        if (challenge.size != 8) return false

        // Inflate to 16 bytes (append eight zeros), SM4-encrypt with device key.
        val block = challenge + ByteArray(8)
        val response = Sm4(DEVICE_KEY).encryptEcb(block)

        val authResp = send(0x80, 0xCE, 0x00, 0x00, response)
        return isOk(authResp)   // 9000 = ok; 6983 = key locked; anything else = fail
    }

    // -- Writes ----------------------------------------------------------------

    /**
     * Set the OTP seed. [seed] is the raw secret (1..63 bytes). Framing follows
     * the reference: general ISO 9797-1 minimal padding, with the special
     * 32-byte "extra full pad block" case.
     */
    fun setSeed(seed: ByteArray): WriteOutcome {
        require(seed.isNotEmpty() && seed.size <= 63) { "seed must be 1..63 bytes" }

        val enc: ByteArray
        val macHeader: ByteArray
        if (seed.size == 32) {
            val padded = seed + byteArrayOf(0x80.toByte()) + ByteArray(15)
            enc = Sm4(DEVICE_KEY).encryptEcb(padded)
            macHeader = Hex.decode("80C5010030")
        } else {
            var body = seed
            if (body.size % 16 != 0) {
                val padLen = 15 - (body.size % 16)
                body = body + byteArrayOf(0x80.toByte()) + ByteArray(padLen)
            }
            enc = Sm4(DEVICE_KEY).encryptEcb(body)
            macHeader = Hex.decode("80C50100") + byteArrayOf(enc.size.toByte())
        }

        val mac = calcMac(macHeader + enc)
        return runWrite(0x84, 0xC5, 0x01, 0x00, enc + mac)
    }

    /**
     * Write the device configuration and sync its clock.
     *
     * @param utcEpochSeconds current UTC time to program (u32)
     * @param displayTimeout 0=15s, 1=30s, 2=60s, 3=120s
     * @param hmacAlgo 1=SHA1, 2=SHA256
     * @param timeStep 1=30s, 2=60s
     */
    fun setConfig(
        utcEpochSeconds: Long,
        displayTimeout: Int,
        hmacAlgo: Int,
        timeStep: Int,
    ): WriteOutcome {
        require(utcEpochSeconds in 0..0xFFFFFFFFL) { "time must fit in 4 bytes" }
        require(displayTimeout in 0..3)
        require(hmacAlgo == 1 || hmacAlgo == 2)
        require(timeStep == 1 || timeStep == 2)

        val timeHex = "%08X".format(utcEpochSeconds)
        val stepHex = if (timeStep == 1) "1E" else "3C"
        val tlv = "8111" +
            "1F01" + "%02X".format(displayTimeout) +
            "0F04" + timeHex +
            "8606" +
            "0A01" + "%02X".format(hmacAlgo) +
            "0D01" + stepHex
        val data = Hex.decode(tlv)
        val macHeader = Hex.decode("80D4000013") + data
        val mac = calcMac(macHeader)
        return runWrite(0x84, 0xD4, 0x00, 0x00, data + mac)
    }

    // -- Internals -------------------------------------------------------------

    private fun runWrite(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray): WriteOutcome {
        val resp = try {
            send(cla, ins, p1, p2, data)
        } catch (e: TransportException) {
            return WriteOutcome.TransportLost(e.message ?: "connection lost")
        }
        val sw = statusWord(resp)
        return when (sw) {
            0x9000 -> WriteOutcome.Success
            0x6983 -> WriteOutcome.AuthLocked
            else -> WriteOutcome.DeviceRejected(sw)
        }
    }

    /** ISO/IEC 9797-1 algorithm 1 MAC (SM4-CBC, IV=0, padding method 2). */
    private fun calcMac(message: ByteArray): ByteArray {
        var m = message
        if (m.size % 16 != 0) {
            val padLen = 15 - (m.size % 16)
            m = m + byteArrayOf(0x80.toByte()) + ByteArray(padLen)
        }
        val cbc = Sm4(DEVICE_KEY).encryptCbcIvZero(m)
        // first 4 bytes of the last block
        return cbc.copyOfRange(cbc.size - 16, cbc.size - 12)
    }

    private fun send(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray): ByteArray {
        val apdu = ByteArray(5 + data.size)
        apdu[0] = cla.toByte()
        apdu[1] = ins.toByte()
        apdu[2] = p1.toByte()
        apdu[3] = p2.toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        val resp = transport.transceive(apdu)
        if (resp.size < 2) throw TransportException("Truncated response from token")
        return resp
    }

    private fun statusWord(resp: ByteArray): Int =
        ((resp[resp.size - 2].toInt() and 0xFF) shl 8) or (resp[resp.size - 1].toInt() and 0xFF)

    private fun isOk(resp: ByteArray) = statusWord(resp) == 0x9000
    private fun requireOk(resp: ByteArray) {
        if (!isOk(resp)) throw TransportException("Token returned status %04X".format(statusWord(resp)))
    }

    companion object {
        /** The single fixed 16-byte SM4 device key for this token family. */
        val DEVICE_KEY = Hex.decode("8AD206883CA369482AB27182B6E83224")

        /**
         * Standard applet AIDs used to recognise a *security key* (the wrong
         * device for this app). These are defined by the FIDO and YubiKey OATH
         * specs, not by Token2, so they identify keys from any vendor:
         *  - FIDO U2F / FIDO2 authenticator applet
         *  - YKOATH applet (where TOTP/HOTP credentials live on a key)
         */
        val AID_FIDO = Hex.decode("A0000006472F0001")
        val AID_YKOATH = Hex.decode("A0000005272101")

        private val MODELS = listOf(
            "8659612" to "OTPC-P1-i",
            "8659622" to "OTPC-P2-i",
            "8659621" to "OTPC-P2-i-NB",
            "8659600" to "miniOTP-2-i",
            "8659601" to "miniOTP-3-i",
            "8659609" to "miniOTP-3-i-NB",
            "8659610" to "C301-i",
            "8659632" to "C302-i",
        )

        /** Model name for a serial, longest-prefix-first; null if unknown. */
        fun modelForSerial(serial: String?): String? {
            val s = serial?.trim().orEmpty()
            return MODELS
                .filter { s.startsWith(it.first) }
                .maxByOrNull { it.first.length }
                ?.second
        }

        /**
         * Models with *unrestricted* time-sync keep their stored seed when a new
         * configuration is written (time is set via the full config TLV — there is
         * no time-only wire command). Every other model has *restricted* sync and
         * clears the seed on a time write, as a replay-attack safeguard.
         *
         * Confirmed unrestricted set: miniOTP-2 (8659600), OTPC-P1 (8659612),
         * C302 (8659632). All other known models are restricted.
         */
        private val UNRESTRICTED_SYNC_PREFIXES = setOf("8659600", "8659612", "8659632")

        /** True if this serial's model keeps its seed across a time sync. */
        fun timeSyncKeepsSeed(serial: String?): Boolean {
            val s = serial?.trim().orEmpty()
            return UNRESTRICTED_SYNC_PREFIXES.any { s.startsWith(it) }
        }

        /**
         * True if syncing time on this serial's model will clear its seed. Only
         * meaningful for a recognised programmable model; unknown serials return
         * false (we don't claim a destructive effect we can't vouch for).
         */
        fun timeSyncClearsSeed(serial: String?): Boolean {
            val s = serial?.trim().orEmpty()
            if (s.isBlank()) return false
            // Restricted = a known model that is NOT in the unrestricted set.
            val known = modelForSerial(s) != null
            return known && !timeSyncKeepsSeed(s)
        }
    }
}
