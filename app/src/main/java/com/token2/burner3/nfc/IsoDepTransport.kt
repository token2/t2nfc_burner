package com.token2.burner3.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.io.IOException

/**
 * A [Token2Protocol.Transport] backed by Android's [IsoDep] (ISO 14443-4).
 *
 * The token is a contactless smart card, so IsoDep is the correct tech. We keep
 * the connection open for the whole read/write session and let the ViewModel
 * decide when to close it, so a single tap can carry the full flow.
 */
class IsoDepTransport private constructor(
    private val isoDep: IsoDep,
    private val tag: Tag,
) : Token2Protocol.Transport, AutoCloseable {

    /** Low-level NFC metadata, for the Identify screen's "everything we can read". */
    val hardware: NfcHardware by lazy {
        NfcHardware(
            uidHex = tag.id?.joinToString("") { "%02X".format(it) } ?: "",
            techList = tag.techList?.map { it.substringAfterLast('.') } ?: emptyList(),
            historicalBytesHex = isoDep.historicalBytes?.joinToString("") { "%02X".format(it) } ?: "",
            hiLayerResponseHex = isoDep.hiLayerResponse?.joinToString("") { "%02X".format(it) } ?: "",
            maxTransceiveLength = isoDep.maxTransceiveLength,
        )
    }

    override fun transceive(apdu: ByteArray): ByteArray {
        try {
            if (!isoDep.isConnected) isoDep.connect()
            return isoDep.transceive(apdu)
        } catch (e: IOException) {
            throw Token2Protocol.TransportException(
                "Lost contact with the token. Keep it flat against the phone.", e
            )
        } catch (e: Exception) {
            throw Token2Protocol.TransportException(
                "Couldn't talk to the token (${e.message ?: "unknown error"}).", e
            )
        }
    }

    override fun close() {
        try {
            isoDep.close()
        } catch (_: Exception) {
        }
    }

    /** Low-level contactless details independent of the Token2 protocol. */
    data class NfcHardware(
        val uidHex: String,
        val techList: List<String>,
        val historicalBytesHex: String,
        val hiLayerResponseHex: String,
        val maxTransceiveLength: Int,
    )

    companion object {
        /**
         * Wrap a discovered [Tag]. Returns null if the tag doesn't speak IsoDep,
         * which lets the UI say "that's an NFC tag, but not a Token2 token."
         */
        fun from(tag: Tag): IsoDepTransport? {
            val isoDep = IsoDep.get(tag) ?: return null
            isoDep.timeout = 3000
            return try {
                isoDep.connect()
                IsoDepTransport(isoDep, tag)
            } catch (_: Exception) {
                null
            }
        }
    }
}
