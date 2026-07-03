package com.token2.burner3.otp

import android.net.Uri
import kotlin.experimental.and

/**
 * Parses whatever the user scanned, pasted, or typed into a usable TOTP secret —
 * and, crucially, explains in plain language when the input is *not* a secret.
 *
 * The single most common support issue with programmable tokens is a user
 * scanning the wrong QR code: very often the token's own serial-number
 * barcode/QR (printed on the token or its packaging) instead of the secret
 * "otpauth" QR shown by the service they're enrolling in. This parser detects
 * that specific mistake and several others, so the UI can say exactly what went
 * wrong instead of failing with a cryptic error.
 */
object SeedInput {

    /** What the user's raw input actually turned out to be. */
    sealed interface Result {

        /** A usable secret was extracted. [rawBytes] is the decoded seed. */
        data class Valid(
            val rawBytes: ByteArray,
            val source: Source,
            /** Present only when the input was a full otpauth:// URI. */
            val label: String? = null,
            val issuer: String? = null,
            val algorithm: OtpAlgorithm? = null,
            val digits: Int? = null,
            val periodSeconds: Int? = null,
        ) : Result {
            override fun equals(other: Any?): Boolean =
                other is Valid && rawBytes.contentEquals(other.rawBytes)
            override fun hashCode(): Int = rawBytes.contentHashCode()
        }

        /** Recognisable but wrong: we know what it is and can tell the user. */
        data class WrongKind(val kind: WrongKind.Kind, val detail: String) : Result {
            enum class Kind {
                LOOKS_LIKE_SERIAL,   // token's own serial number
                OTHER_URL,           // some other web link
                EMPTY,
                PLAIN_NUMBER,
                TOO_SHORT,
                MS_PUSH,             // phonefactor:// — Microsoft push/native QR
                FIDO_PASSKEY,        // FIDO:/ — passkey/security-key registration QR
                OTPAUTH_MIGRATION,   // otpauth-migration:// — Google Authenticator export
            }
        }

        /** Unrecognisable: not empty, but nothing we can decode. */
        data class Unusable(val detail: String) : Result
    }

    enum class Source { OTPAUTH_URI, BASE32_SECRET, HEX_SECRET }

    /**
     * Classify and (if possible) decode [raw].
     *
     * @param serialHint the serial number already read from the token over NFC,
     *        if any. Used to recognise "the user scanned the serial" with high
     *        confidence when the two match.
     */
    fun parse(raw: String?, serialHint: String? = null): Result {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) {
            return Result.WrongKind(Result.WrongKind.Kind.EMPTY, "Nothing was scanned or entered yet.")
        }

        // 1) A full otpauth:// URI — the correct, richest case.
        if (input.startsWith("otpauth://", ignoreCase = true)) {
            return parseOtpauth(input)
        }

        // 1b) Microsoft push / native QR ("phonefactor://activate_account?...").
        // This enrols a phone-approval / number-match sign-in, which a hardware
        // TOTP token cannot do. The user must pick the "other authenticator app"
        // path in their Microsoft/Entra setup instead.
        if (input.startsWith("phonefactor://", ignoreCase = true) ||
            input.contains("phonefactor://", ignoreCase = true)
        ) {
            return Result.WrongKind(
                Result.WrongKind.Kind.MS_PUSH,
                "This is a Microsoft Authenticator push code, not a token secret."
            )
        }

        // 1c) Google Authenticator "export/transfer" QR.
        if (input.startsWith("otpauth-migration://", ignoreCase = true)) {
            return Result.WrongKind(
                Result.WrongKind.Kind.OTPAUTH_MIGRATION,
                "This is an authenticator export code, not a single token secret."
            )
        }

        // 1d) FIDO / passkey registration QR ("FIDO:/459420072993...").
        // These enrol a passkey or security key over CTAP; a TOTP token has no
        // passkey to store, so it simply doesn't apply here.
        if (input.startsWith("FIDO:/", ignoreCase = true)) {
            return Result.WrongKind(
                Result.WrongKind.Kind.FIDO_PASSKEY,
                "This is a passkey / security-key code, not a TOTP secret."
            )
        }

        // 2) Some other URL (a login page, a KeyURI wrapped oddly, etc.)
        if (input.contains("://") || input.startsWith("http", ignoreCase = true)) {
            return Result.WrongKind(
                Result.WrongKind.Kind.OTHER_URL,
                "This looks like a web link, not a token secret."
            )
        }

        val compact = input.replace(" ", "").replace("-", "")

        // 3) Pure digits — almost always a serial number or an order/reference.
        if (compact.all { it.isDigit() }) {
            val looksLikeSerial =
                (serialHint != null && compact == serialHint.replace(" ", "")) ||
                (compact.length in 8..14 && KNOWN_SERIAL_PREFIXES.any { compact.startsWith(it) })
            return if (looksLikeSerial) {
                Result.WrongKind(
                    Result.WrongKind.Kind.LOOKS_LIKE_SERIAL,
                    "That's the token's serial number, not the secret key."
                )
            } else {
                Result.WrongKind(
                    Result.WrongKind.Kind.PLAIN_NUMBER,
                    "That's just a number. A TOTP secret contains letters too."
                )
            }
        }

        // 4) Hex secret (even length, all hex digits, and not obviously base32).
        val upper = compact.uppercase()
        if (upper.length % 2 == 0 && upper.all { it in "0123456789ABCDEF" } &&
            upper.any { it in "0123456789" } && upper.length >= 16
        ) {
            val bytes = hexToBytes(upper)
            if (bytes.size < 10) {
                return Result.WrongKind(
                    Result.WrongKind.Kind.TOO_SHORT,
                    "This secret is too short to be a valid key."
                )
            }
            return Result.Valid(bytes, Source.HEX_SECRET)
        }

        // 5) Base32 secret (the usual "JBSWY3DP..." form).
        val base32 = upper.trimEnd('=')
        if (base32.isNotEmpty() && base32.all { it in BASE32_ALPHABET }) {
            val bytes = base32Decode(base32)
                ?: return Result.Unusable("The secret contains characters that aren't valid Base32.")
            if (bytes.size < 10) {
                return Result.WrongKind(
                    Result.WrongKind.Kind.TOO_SHORT,
                    "This secret is too short to be a valid key."
                )
            }
            return Result.Valid(bytes, Source.BASE32_SECRET)
        }

        return Result.Unusable("This doesn't match any secret format we recognise.")
    }

    private fun parseOtpauth(input: String): Result {
        val uri = try {
            Uri.parse(input)
        } catch (_: Exception) {
            return Result.Unusable("The otpauth link is malformed and couldn't be read.")
        }
        if (!"totp".equals(uri.host, ignoreCase = true)) {
            return Result.Unusable(
                "This is a ${uri.host ?: "non-TOTP"} code. These tokens only support TOTP (time-based) codes."
            )
        }
        val secretParam = uri.getQueryParameter("secret")
            ?: return Result.Unusable("The otpauth link is missing its secret.")
        val bytes = base32Decode(secretParam.uppercase().trimEnd('='))
            ?: return Result.Unusable("The secret inside the link isn't valid Base32.")
        if (bytes.size < 10) {
            return Result.WrongKind(
                Result.WrongKind.Kind.TOO_SHORT,
                "The secret inside the link is too short to be valid."
            )
        }

        val label = uri.lastPathSegment?.removePrefix("/")
        val issuer = uri.getQueryParameter("issuer")
            ?: label?.substringBefore(":", "")?.ifBlank { null }
        val algo = uri.getQueryParameter("algorithm")?.let { OtpAlgorithm.fromLabel(it) }
        val digits = uri.getQueryParameter("digits")?.toIntOrNull()
        val period = uri.getQueryParameter("period")?.toIntOrNull()

        return Result.Valid(
            rawBytes = bytes,
            source = Source.OTPAUTH_URI,
            label = label,
            issuer = issuer,
            algorithm = algo,
            digits = digits,
            periodSeconds = period,
        )
    }

    // -- helpers ---------------------------------------------------------------

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Serial prefixes of known Token2 programmable models (from PROTOCOL.md). */
    private val KNOWN_SERIAL_PREFIXES = listOf(
        "8659612", "8659622", "8659621", "8659600",
        "8659601", "8659609", "8659610", "8659632",
    )

    fun base32Decode(s: String): ByteArray? {
        if (s.isEmpty()) return ByteArray(0)
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>(s.length * 5 / 8 + 1)
        for (c in s) {
            val v = BASE32_ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    fun hexToBytes(s: String): ByteArray {
        val clean = if (s.length % 2 != 0) "0$s" else s
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(b: ByteArray): String =
        b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** RFC 4648 Base32 encode (no padding) — for showing/editing the secret. */
    fun base32Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }
}

enum class OtpAlgorithm(val label: String, val wireValue: Int) {
    SHA1("SHA1", 1),
    SHA256("SHA256", 2);

    companion object {
        fun fromLabel(s: String): OtpAlgorithm? =
            entries.firstOrNull { it.label.equals(s, ignoreCase = true) }
    }
}
