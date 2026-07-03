package com.token2.burner3.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.token2.burner3.nfc.IsoDepTransport
import com.token2.burner3.nfc.Token2Protocol
import com.token2.burner3.otp.OtpAlgorithm
import com.token2.burner3.otp.SeedInput
import com.token2.burner3.otp.Totp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds and advances the wizard. All NFC work is funnelled through [onTagTapped],
 * which the Activity calls when the OS delivers a discovered tag. The ViewModel
 * decides — based on the current [Step] — whether that tap should read info,
 * write, or be politely ignored.
 */
class WizardViewModel : ViewModel() {

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state.asStateFlow()

    // -- Navigation ------------------------------------------------------------

    fun start() = _state.update {
        // Fresh wizard run — drop any previously identified token.
        it.copy(step = Step.ScanSecret, error = null, tokenInfo = null)
    }

    fun enterExpertMode() = _state.update {
        it.copy(expertMode = true, error = null, tokenInfo = null, seedBytes = null)
    }
    fun exitExpertMode() = _state.update {
        it.copy(expertMode = false, error = null, tokenInfo = null)
    }

    fun enterIdentify() = _state.update {
        it.copy(
            identifyMode = true, error = null,
            tokenInfo = null, identifyReport = null, identifyHeadline = null,
        )
    }
    fun exitIdentify() = _state.update {
        it.copy(identifyMode = false, error = null, tokenInfo = null, identifyReport = null, identifyHeadline = null)
    }

    fun enterTimeSync() = _state.update {
        it.copy(
            timeSyncMode = true, error = null, timeSyncLog = emptyList(),
            tokenInfo = null, timeSyncArmed = false, timeSyncCustomEpoch = null,
        )
    }
    fun exitTimeSync() = _state.update {
        it.copy(timeSyncMode = false, error = null, tokenInfo = null)
    }

    /** Set a custom time to program, or null to use the phone's current time. */
    fun setTimeSyncCustom(epochSeconds: Long?) = _state.update { it.copy(timeSyncCustomEpoch = epochSeconds) }

    /** Arm/disarm time sync based on the acknowledgement checkbox. */
    fun setTimeSyncArmed(armed: Boolean) = _state.update { it.copy(timeSyncArmed = armed) }

    fun clearTimeSyncLog() = _state.update { it.copy(timeSyncLog = emptyList()) }

    fun goTo(step: Step) = _state.update { it.copy(step = step, error = null) }

    fun back() {
        val s = _state.value
        val prev = when (s.step) {
            Step.ScanSecret -> Step.Welcome
            Step.ConfirmSecret -> Step.ScanSecret
            Step.PowerOn -> Step.ConfirmSecret
            Step.TapToWrite -> Step.PowerOn
            else -> s.step
        }
        goTo(prev)
    }

    /**
     * Handle the system Back gesture / button. Returns true if it was consumed
     * by navigating within the app, or false if we're already at the top (home
     * screen) and the system should be allowed to leave the app.
     *
     * Order: close a modal (error/success dialog) first, then leave any mode
     * (expert / identify / time sync), then step back through the wizard, and
     * only at the Welcome screen do we let the system exit.
     */
    fun onSystemBack(): Boolean {
        val s = _state.value
        return when {
            s.error != null -> { dismissError(); true }
            s.successDialog != null -> { dismissSuccessDialog(); true }
            s.expertMode -> { exitExpertMode(); true }
            s.identifyMode -> { exitIdentify(); true }
            s.timeSyncMode -> { exitTimeSync(); true }
            s.step == Step.Done -> { restartToWelcome(); true }
            s.step != Step.Welcome && s.step != Step.Writing -> { back(); true }
            else -> false  // at home (or mid-write): let the system handle it
        }
    }

    /** Return to the welcome screen, clearing any in-progress token state. */
    private fun restartToWelcome() = _state.update {
        it.copy(step = Step.Welcome, error = null, tokenInfo = null)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // -- Secret capture --------------------------------------------------------

    /**
     * Handle a scanned QR string. In the wizard this validates and routes to the
     * confirm step (with the wrong-QR guidance). In expert mode it extracts only
     * the raw secret and loads it, ignoring any label/issuer/parameters the QR
     * carried — the expert sets those manually.
     */
    fun onScanResult(text: String) {
        if (_state.value.expertMode) {
            when (val r = SeedInput.parse(text, _state.value.tokenInfo?.serial)) {
                is SeedInput.Result.Valid -> {
                    // Show the bare Base32 secret in the field — that's what expert
                    // mode actually uses, not the label/issuer/params a URI carried.
                    val b32 = SeedInput.base32Encode(r.rawBytes)
                    _state.update { it.copy(rawInput = b32, seedBytes = r.rawBytes, parsed = null, error = null) }
                    appendLog("Scanned secret: ${r.rawBytes.size} bytes loaded (from ${r.source}).")
                }
                is SeedInput.Result.WrongKind -> {
                    _state.update { it.copy(rawInput = text, parsed = null, error = null) }
                    appendLog("Scan ignored — ${r.detail}")
                }
                is SeedInput.Result.Unusable -> {
                    _state.update { it.copy(rawInput = text, parsed = null, error = null) }
                    appendLog("Scan ignored — ${r.detail}")
                }
            }
        } else {
            _state.update { it.copy(rawInput = text, parsed = null, error = null) }
            submitSecret()
        }
    }

    /** Called on every keystroke or after a QR scan completes. */
    fun onInputChanged(raw: String) {
        // In expert mode, if a full otpauth:// URI is entered (typically pasted),
        // collapse the field to the bare Base32 secret so it shows what's actually
        // used. Plain secrets are left exactly as typed.
        if (_state.value.expertMode && raw.trim().startsWith("otpauth://", ignoreCase = true)) {
            val r = SeedInput.parse(raw, _state.value.tokenInfo?.serial)
            if (r is SeedInput.Result.Valid) {
                val b32 = SeedInput.base32Encode(r.rawBytes)
                _state.update { it.copy(rawInput = b32, seedBytes = r.rawBytes, parsed = null, error = null) }
                return
            }
        }
        _state.update { it.copy(rawInput = raw, parsed = null, error = null) }
        // In expert mode, keep the loaded seed in sync with the field so the
        // secret loads as you type — no separate button needed.
        if (_state.value.expertMode) {
            val r = SeedInput.parse(raw, _state.value.tokenInfo?.serial)
            if (r is SeedInput.Result.Valid) {
                _state.update { it.copy(seedBytes = r.rawBytes) }
            } else if (raw.isBlank()) {
                _state.update { it.copy(seedBytes = null) }
            }
        }
    }

    /** User pressed "Continue" on the scan screen — validate and route. */
    fun submitSecret() {
        val s = _state.value
        val result = SeedInput.parse(s.rawInput, serialHint = s.tokenInfo?.serial)
        when (result) {
            is SeedInput.Result.Valid -> {
                // Adopt any settings the otpauth URI told us about.
                val algo = result.algorithm ?: s.algorithm
                val period = result.periodSeconds ?: s.periodSeconds
                _state.update {
                    it.copy(
                        parsed = result,
                        seedBytes = result.rawBytes,
                        algorithm = algo,
                        periodSeconds = if (period == 60) 60 else 30,
                        step = Step.ConfirmSecret,
                        error = null,
                    )
                }
            }
            is SeedInput.Result.WrongKind ->
                _state.update { it.copy(parsed = result, error = FriendlyError.forWrongKind(result)) }
            is SeedInput.Result.Unusable ->
                _state.update { it.copy(parsed = result, error = FriendlyError.forUnusable(result.detail)) }
        }
    }

    // -- Settings --------------------------------------------------------------

    fun setAlgorithm(a: OtpAlgorithm) = _state.update { it.copy(algorithm = a) }
    fun setPeriod(seconds: Int) = _state.update { it.copy(periodSeconds = if (seconds == 60) 60 else 30) }
    fun setDisplayTimeout(index: Int) = _state.update { it.copy(displayTimeoutIndex = index.coerceIn(0, 3)) }

    /**
     * Re-parse a base32 secret the user edited on the confirm screen. Returns
     * true if it was accepted (and applied), false if invalid — the caller shows
     * inline feedback rather than bouncing the user out of the step.
     */
    fun applyEditedBase32(secret: String): Boolean {
        val bytes = SeedInput.base32Decode(secret.uppercase().trim().trimEnd('=').replace(" ", ""))
            ?: return false
        if (bytes.size < 10) return false
        _state.update { it.copy(seedBytes = bytes) }
        return true
    }

    // -- NFC entry point -------------------------------------------------------

    /**
     * The Activity calls this with a live [IsoDepTransport] whenever a tag is
     * discovered. Behaviour depends on the current step:
     *  - On [Step.TapToWrite]: read info, verify model, authenticate, write.
     *  - Elsewhere (e.g. user taps early on the confirm screen): read info so we
     *    can show model/serial and pre-detect a "wrong device" case.
     */
    fun onTagTapped(transport: IsoDepTransport) {
        val s = _state.value
        val hw = runCatching { transport.hardware }.getOrNull()
        viewModelScope.launch {
            transport.use { t ->
                val protocol = Token2Protocol(t)
                when {
                    s.identifyMode -> performIdentify(protocol, hw)
                    s.timeSyncMode -> performTimeSync(protocol, hw)
                    s.expertMode -> performExpert(protocol, hw)
                    s.step == Step.TapToWrite -> performWrite(protocol, hw)
                    else -> peekInfo(protocol)
                }
            }
        }
    }

    /** Build a full, human-readable device report from token info + NFC hardware. */
    private fun deviceInfoLines(
        info: Token2Protocol.TokenInfo?,
        hw: IsoDepTransport.NfcHardware?,
    ): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        if (info != null) {
            out += "Model" to (info.model ?: "Unknown model")
            out += "Serial" to info.serial
            out += "On-device time" to formatUtc(info.utcEpochSeconds)
            out += "Time sync" to
                if (Token2Protocol.timeSyncKeepsSeed(info.serial)) "Unrestricted (keeps secret)"
                else "Restricted (clears secret)"
        }
        if (hw != null) {
            if (hw.uidHex.isNotBlank()) out += "NFC UID" to hw.uidHex
            if (hw.techList.isNotEmpty()) out += "Tech" to hw.techList.joinToString(", ")
            if (hw.historicalBytesHex.isNotBlank()) out += "ATR / historical" to hw.historicalBytesHex
            if (hw.hiLayerResponseHex.isNotBlank()) out += "Hi-layer resp" to hw.hiLayerResponseHex
            if (hw.maxTransceiveLength > 0) out += "Max APDU" to "${hw.maxTransceiveLength} bytes"
        }
        return out
    }

    /** Identify screen: read everything possible and present it, no writes. */
    private suspend fun performIdentify(protocol: Token2Protocol, hw: IsoDepTransport.NfcHardware?) {
        val identity = withContext(Dispatchers.IO) { runCatching { protocol.identify() }.getOrNull() }
        val (headline, info) = when (identity?.kind) {
            Token2Protocol.DeviceKind.PROGRAMMABLE_TOKEN -> {
                val i = identity.info
                _state.update { it.copy(tokenInfo = i) }
                (if (i?.isKnownModel == true) "Programmable token" to i
                else "Token2 token (unknown model)" to i)
            }
            Token2Protocol.DeviceKind.SECURITY_KEY ->
                "FIDO security key" to null
            Token2Protocol.DeviceKind.UNKNOWN_SMARTCARD ->
                "Unrecognised smart card" to null
            else -> "No response" to null
        }
        val lines = mutableListOf<Pair<String, String>>()
        lines += deviceInfoLines(info, hw)
        if (identity?.kind == Token2Protocol.DeviceKind.SECURITY_KEY) {
            lines += "FIDO applet" to if (identity.hasFido) "present" else "no"
            lines += "OATH applet" to if (identity.hasOath) "present" else "no"
            lines += "Note" to "Manage this in Libre Key Companion or Token2 Companion."
        }
        _state.update { it.copy(identifyHeadline = headline, identifyReport = lines) }
    }

    // -- Expert mode -----------------------------------------------------------

    fun setExpertAction(a: ExpertAction) = _state.update { it.copy(expertAction = a, error = null) }

    /** Parse the current input and load its seed for expert use (no navigation). */
    fun loadSecretForExpert() {
        val s = _state.value
        when (val r = SeedInput.parse(s.rawInput, s.tokenInfo?.serial)) {
            is SeedInput.Result.Valid -> {
                _state.update {
                    it.copy(
                        seedBytes = r.rawBytes,
                        algorithm = r.algorithm ?: it.algorithm,
                        periodSeconds = if ((r.periodSeconds ?: it.periodSeconds) == 60) 60 else 30,
                    )
                }
                appendLog("Loaded secret: ${r.rawBytes.size} bytes (${r.source}).")
            }
            is SeedInput.Result.WrongKind -> appendLog("Not a secret: ${r.detail}")
            is SeedInput.Result.Unusable -> appendLog("Unusable: ${r.detail}")
        }
    }

    private suspend fun performExpert(protocol: Token2Protocol, hw: IsoDepTransport.NfcHardware?) {
        val s = _state.value
        appendLog("Tag detected. Identifying…")
        val identity = withContext(Dispatchers.IO) { runCatching { protocol.identify() }.getOrNull() }
        when (identity?.kind) {
            Token2Protocol.DeviceKind.PROGRAMMABLE_TOKEN -> {
                val info = identity.info!!
                _state.update { it.copy(tokenInfo = info) }
                appendLog("Model: ${info.model ?: "unknown"}")
                appendLog("Serial: ${info.serial}")
                appendLog("On-device UTC: ${formatUtc(info.utcEpochSeconds)}")
                if (s.expertAction == ExpertAction.IDENTIFY) return
                if (!info.isKnownModel) { appendLog("Unknown model prefix — refusing to write."); return }

                appendLog("Authenticating…")
                val ok = withContext(Dispatchers.IO) { runCatching { protocol.authenticate() }.getOrDefault(false) }
                if (!ok) { appendLog("Authentication failed (key locked?). Aborting."); return }
                appendLog("Authenticated.")

                val now = System.currentTimeMillis() / 1000
                if (s.expertAction == ExpertAction.WRITE_CONFIG || s.expertAction == ExpertAction.WRITE_BOTH) {
                    appendLog("Writing config (algo=${s.algorithm.label}, step=${s.periodSeconds}s, timeout idx=${s.displayTimeoutIndex})…")
                    val r = withContext(Dispatchers.IO) {
                        protocol.setConfig(now, s.displayTimeoutIndex, s.algorithm.wireValue, s.timeStepWire)
                    }
                    appendLog(if (r is Token2Protocol.WriteOutcome.Success) "Config OK." else "Config failed: $r")
                    if (r !is Token2Protocol.WriteOutcome.Success) return
                }
                if (s.expertAction == ExpertAction.WRITE_SEED || s.expertAction == ExpertAction.WRITE_BOTH) {
                    val seed = s.seedBytes
                    if (seed == null) { appendLog("No secret loaded — enter one first."); return }
                    appendLog("Writing seed (${seed.size} bytes)…")
                    val r = withContext(Dispatchers.IO) { protocol.setSeed(seed) }
                    appendLog(if (r is Token2Protocol.WriteOutcome.Success) "Seed OK." else "Seed failed: $r")
                    if (r !is Token2Protocol.WriteOutcome.Success) return
                }
                appendLog("Done. Power-cycle the token before checking codes.")
                val what = when (s.expertAction) {
                    ExpertAction.WRITE_BOTH -> "Secret and configuration written."
                    ExpertAction.WRITE_SEED -> "Secret written."
                    ExpertAction.WRITE_CONFIG -> "Configuration written."
                    ExpertAction.IDENTIFY -> null
                }
                if (what != null) {
                    _state.update {
                        it.copy(
                            successDialog = "$what\n\nPower-cycle the token before checking codes.",
                            successInfo = deviceInfoLines(info, hw),
                        )
                    }
                }
            }
            Token2Protocol.DeviceKind.SECURITY_KEY ->
                appendLog("This is a FIDO security key — use Libre Key Companion or Token2 Companion, not this app.")
            Token2Protocol.DeviceKind.UNKNOWN_SMARTCARD ->
                appendLog("Unrecognised smart card — not a Token2 token.")
            else -> appendLog("No response — not a Token2 token, or it moved away.")
        }
    }

    private fun appendLog(line: String) = _state.update {
        it.copy(expertLog = (it.expertLog + line).takeLast(40))
    }

    private fun formatUtc(epochSeconds: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochSeconds * 1000))
    }

    fun clearExpertLog() = _state.update { it.copy(expertLog = emptyList()) }

    fun dismissSuccessDialog() = _state.update { it.copy(successDialog = null, successInfo = null) }

    // -- Time sync -------------------------------------------------------------

    private fun tsLog(line: String) = _state.update {
        it.copy(timeSyncLog = (it.timeSyncLog + line).takeLast(40))
    }

    private suspend fun performTimeSync(protocol: Token2Protocol, hw: IsoDepTransport.NfcHardware?) {
        val s = _state.value
        if (!s.timeSyncArmed) {
            tsLog("Please tick the acknowledgement box before syncing.")
            return
        }
        tsLog("Tag detected. Identifying…")
        val identity = withContext(Dispatchers.IO) { runCatching { protocol.identify() }.getOrNull() }
        when (identity?.kind) {
            Token2Protocol.DeviceKind.PROGRAMMABLE_TOKEN -> {
                val info = identity.info!!
                _state.update { it.copy(tokenInfo = info) }
                tsLog("Model: ${info.model ?: "unknown"} · serial ${info.serial}")
                tsLog("Current on-device UTC: ${formatUtc(info.utcEpochSeconds)}")
                if (!info.isKnownModel) { tsLog("Unknown model prefix — refusing."); return }

                val clears = Token2Protocol.timeSyncClearsSeed(info.serial)
                if (clears) tsLog("Note: this model has restricted sync — its seed will be cleared.")
                else tsLog("This model keeps its seed through the sync.")

                tsLog("Authenticating…")
                val ok = withContext(Dispatchers.IO) { runCatching { protocol.authenticate() }.getOrDefault(false) }
                if (!ok) { tsLog("Authentication failed (key locked?). Aborting."); return }

                val target = s.timeSyncCustomEpoch ?: (System.currentTimeMillis() / 1000)
                tsLog("Setting time to ${formatUtc(target)}…")
                // Time travels in the full config TLV; we reuse the token's current
                // parameters so nothing else meaningfully changes.
                val r = withContext(Dispatchers.IO) {
                    protocol.setConfig(target, s.displayTimeoutIndex, s.algorithm.wireValue, s.timeStepWire)
                }
                if (r is Token2Protocol.WriteOutcome.Success) {
                    tsLog("Time set successfully.")
                    if (clears) tsLog("The seed on this model was cleared — re-program it before use.")
                    tsLog("Power-cycle the token to apply.")
                    val msg = buildString {
                        append("Time set to ${formatUtc(target)}.")
                        if (clears) append("\n\nThis model cleared its secret — re-program it before use.")
                        append("\n\nPower-cycle the token to apply.")
                    }
                    _state.update { it.copy(successDialog = msg, successInfo = deviceInfoLines(info, hw)) }
                } else {
                    tsLog("Time sync failed: $r")
                }
            }
            Token2Protocol.DeviceKind.SECURITY_KEY ->
                tsLog("This is a FIDO security key — use Libre Key Companion or Token2 Companion.")
            Token2Protocol.DeviceKind.UNKNOWN_SMARTCARD ->
                tsLog("Unrecognised smart card — not a Token2 token.")
            else -> tsLog("No response — not a Token2 token, or it moved away.")
        }
    }

    private suspend fun peekInfo(protocol: Token2Protocol) {
        val identity = withContext(Dispatchers.IO) {
            runCatching { protocol.identify() }.getOrNull()
        }
        when (identity?.kind) {
            Token2Protocol.DeviceKind.PROGRAMMABLE_TOKEN -> {
                val info = identity.info!!
                _state.update { it.copy(tokenInfo = info) }
                if (!info.isKnownModel) {
                    _state.update { it.copy(error = FriendlyError.unknownModel(info.serial)) }
                }
            }
            Token2Protocol.DeviceKind.SECURITY_KEY ->
                _state.update { it.copy(error = FriendlyError.securityKey(identity.hasOath)) }
            Token2Protocol.DeviceKind.UNKNOWN_SMARTCARD ->
                _state.update { it.copy(error = FriendlyError.unknownSmartcard()) }
            else ->
                _state.update { it.copy(error = FriendlyError.notAToken2()) }
        }
    }

    private suspend fun performWrite(protocol: Token2Protocol, hw: IsoDepTransport.NfcHardware?) {
        val s = _state.value
        val seed = s.seedBytes ?: return

        _state.update { it.copy(step = Step.Writing, busyMessage = "Reading the token…", error = null) }

        val identity = withContext(Dispatchers.IO) { runCatching { protocol.identify() }.getOrNull() }
        when (identity?.kind) {
            Token2Protocol.DeviceKind.PROGRAMMABLE_TOKEN -> { /* proceed */ }
            Token2Protocol.DeviceKind.SECURITY_KEY -> {
                fail(FriendlyError.securityKey(identity.hasOath)); return
            }
            Token2Protocol.DeviceKind.UNKNOWN_SMARTCARD -> {
                fail(FriendlyError.unknownSmartcard()); return
            }
            else -> { fail(FriendlyError.notAToken2()); return }
        }
        val info = identity.info!!
        _state.update { it.copy(tokenInfo = info) }
        if (!info.isKnownModel) {
            fail(FriendlyError.unknownModel(info.serial)); return
        }

        _state.update { it.copy(busyMessage = "Unlocking the token…") }
        val authed = withContext(Dispatchers.IO) { runCatching { protocol.authenticate() }.getOrDefault(false) }
        if (!authed) {
            fail(FriendlyError.authLocked()); return
        }

        // Config first (clock + parameters), then seed — matching the reference order.
        _state.update { it.copy(busyMessage = "Setting the clock and options…") }
        val nowUtc = System.currentTimeMillis() / 1000
        val cfg = withContext(Dispatchers.IO) {
            protocol.setConfig(
                utcEpochSeconds = nowUtc,
                displayTimeout = s.displayTimeoutIndex,
                hmacAlgo = s.algorithm.wireValue,
                timeStep = s.timeStepWire,
            )
        }
        if (mapWriteFailure(cfg)) return

        _state.update { it.copy(busyMessage = "Writing the secret…") }
        val seedOut = withContext(Dispatchers.IO) { protocol.setSeed(seed) }
        if (mapWriteFailure(seedOut)) return

        // Success — compute the previous, current, and next codes so the user can
        // verify even right at a time-step boundary (where the token may still be
        // showing the previous code, or has just rolled to the next one).
        val period = s.periodSeconds
        val current = runCatching { Totp.generate(seed, s.algorithm, 6, period, nowUtc) }.getOrNull()
        val previous = runCatching { Totp.generate(seed, s.algorithm, 6, period, nowUtc - period) }.getOrNull()
        val next = runCatching { Totp.generate(seed, s.algorithm, 6, period, nowUtc + period) }.getOrNull()
        _state.update {
            it.copy(
                step = Step.Done,
                busyMessage = null,
                error = null,
                previewCode = current,
                previewPrevCode = previous,
                previewNextCode = next,
                previewSecondsLeft = Totp.secondsLeft(period, nowUtc),
                successDialog = "Secret and time written to the token.",
                successInfo = deviceInfoLines(info, hw),
            )
        }
    }

    /** Returns true and sets an error if [outcome] was a failure. */
    private fun mapWriteFailure(outcome: Token2Protocol.WriteOutcome): Boolean {
        return when (outcome) {
            is Token2Protocol.WriteOutcome.Success -> false
            is Token2Protocol.WriteOutcome.AuthLocked -> { fail(FriendlyError.authLocked()); true }
            is Token2Protocol.WriteOutcome.TransportLost -> { fail(FriendlyError.tokenMoved()); true }
            is Token2Protocol.WriteOutcome.DeviceRejected -> { fail(FriendlyError.deviceRejected(outcome.sw)); true }
        }
    }

    private fun fail(error: FriendlyError) {
        _state.update { it.copy(step = Step.TapToWrite, busyMessage = null, error = error) }
    }

    fun onTagRejected() {
        _state.update { it.copy(error = FriendlyError.notAToken2()) }
    }

    fun showNoNfc() = _state.update { it.copy(error = FriendlyError.noNfc()) }
    fun showNfcOff() = _state.update { it.copy(error = FriendlyError.nfcOff()) }

    fun restartForAnotherToken() {
        _state.update {
            WizardState(
                // keep the secret and settings so you can program a batch quickly
                step = Step.TapToWrite,
                rawInput = it.rawInput,
                parsed = it.parsed,
                seedBytes = it.seedBytes,
                algorithm = it.algorithm,
                periodSeconds = it.periodSeconds,
                displayTimeoutIndex = it.displayTimeoutIndex,
            )
        }
    }

    fun startOver() = _state.update { WizardState() }
}
