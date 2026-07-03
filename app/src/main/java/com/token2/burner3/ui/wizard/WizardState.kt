package com.token2.burner3.ui.wizard

import com.token2.burner3.nfc.Token2Protocol
import com.token2.burner3.otp.OtpAlgorithm
import com.token2.burner3.otp.SeedInput

/**
 * The wizard is a small, explicit state machine. Every screen the user can see
 * is one [Step]; every failure is a [FriendlyError] that carries its own plain
 * explanation and a concrete next action, so the UI never has to invent copy or
 * surface a raw status word.
 */
enum class Step {
    Welcome,          // what this does, what you need
    ScanSecret,       // capture the otpauth QR / secret, with mistake detection
    ConfirmSecret,    // show what we understood; confirm before writing (params edited here via dialog)
    PowerOn,          // wake the token into programming mode (button animation)
    TapToWrite,       // "hold the token to the phone"
    Writing,          // in-progress, do not move the token
    Done,             // success + verification code preview
}

/**
 * What the expert console will do on the next tap. IDENTIFY only reads the
 * device (serial, model, on-device UTC) and never writes; the write actions each
 * authenticate first. WRITE_BOTH is the default for programming a token.
 */
enum class ExpertAction { WRITE_BOTH, WRITE_SEED, WRITE_CONFIG, IDENTIFY }

/** Everything the wizard needs to remember between steps. */
data class WizardState(
    val step: Step = Step.Welcome,

    /** When true, the app shows the manual expert console instead of the wizard. */
    val expertMode: Boolean = false,

    /** When true, the app shows the dedicated Identify screen. */
    val identifyMode: Boolean = false,
    /** Full human-readable identify report (label → value pairs), newest scan. */
    val identifyReport: List<Pair<String, String>>? = null,
    /** Headline kind for the identify screen (e.g. "Programmable token"). */
    val identifyHeadline: String? = null,

    /** When true, the app shows the dedicated time-sync screen. */
    val timeSyncMode: Boolean = false,
    /** Optional custom time to program (epoch seconds); null = use phone's now. */
    val timeSyncCustomEpoch: Long? = null,
    /** True once the user has ticked the acknowledgement box on the sync screen. */
    val timeSyncArmed: Boolean = false,
    val timeSyncLog: List<String> = emptyList(),

    // Secret capture
    val rawInput: String = "",
    val parsed: SeedInput.Result? = null,
    val seedBytes: ByteArray? = null,

    // Settings (defaults match the most common enrolment)
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val periodSeconds: Int = 30,          // 30 or 60
    val displayTimeoutIndex: Int = 1,     // 0=15s,1=30s,2=60s,3=120s

    // Live token info (populated on tap)
    val tokenInfo: Token2Protocol.TokenInfo? = null,

    // Transient UI
    val busyMessage: String? = null,
    val error: FriendlyError? = null,

    // Result
    val previewCode: String? = null,
    val previewPrevCode: String? = null,
    val previewNextCode: String? = null,
    val previewSecondsLeft: Int? = null,

    /** When non-null, a success dialog is shown (expert / time-sync completions). */
    val successDialog: String? = null,
    /** Full device info shown in the success dialog (label → value). */
    val successInfo: List<Pair<String, String>>? = null,

    // Expert console
    val expertAction: ExpertAction = ExpertAction.WRITE_BOTH,
    val expertLog: List<String> = emptyList(),
) {
    val timeStepWire: Int get() = if (periodSeconds == 60) 2 else 1

    // Data class with a ByteArray needs manual equals/hashCode for correctness.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WizardState) return false
        return step == other.step &&
            expertMode == other.expertMode &&
            identifyMode == other.identifyMode &&
            identifyReport == other.identifyReport &&
            identifyHeadline == other.identifyHeadline &&
            timeSyncMode == other.timeSyncMode &&
            timeSyncCustomEpoch == other.timeSyncCustomEpoch &&
            timeSyncArmed == other.timeSyncArmed &&
            timeSyncLog == other.timeSyncLog &&
            rawInput == other.rawInput &&
            parsed == other.parsed &&
            (seedBytes?.contentEquals(other.seedBytes) ?: (other.seedBytes == null)) &&
            algorithm == other.algorithm &&
            periodSeconds == other.periodSeconds &&
            displayTimeoutIndex == other.displayTimeoutIndex &&
            tokenInfo == other.tokenInfo &&
            busyMessage == other.busyMessage &&
            error == other.error &&
            previewCode == other.previewCode &&
            previewPrevCode == other.previewPrevCode &&
            previewNextCode == other.previewNextCode &&
            previewSecondsLeft == other.previewSecondsLeft &&
            expertAction == other.expertAction &&
            successDialog == other.successDialog &&
            successInfo == other.successInfo &&
            expertLog == other.expertLog
    }

    override fun hashCode(): Int {
        var r = step.hashCode()
        r = 31 * r + expertMode.hashCode()
        r = 31 * r + identifyMode.hashCode()
        r = 31 * r + (identifyReport?.hashCode() ?: 0)
        r = 31 * r + (identifyHeadline?.hashCode() ?: 0)
        r = 31 * r + timeSyncMode.hashCode()
        r = 31 * r + (timeSyncCustomEpoch?.hashCode() ?: 0)
        r = 31 * r + timeSyncArmed.hashCode()
        r = 31 * r + timeSyncLog.hashCode()
        r = 31 * r + rawInput.hashCode()
        r = 31 * r + (parsed?.hashCode() ?: 0)
        r = 31 * r + (seedBytes?.contentHashCode() ?: 0)
        r = 31 * r + algorithm.hashCode()
        r = 31 * r + periodSeconds
        r = 31 * r + displayTimeoutIndex
        r = 31 * r + (tokenInfo?.hashCode() ?: 0)
        r = 31 * r + (busyMessage?.hashCode() ?: 0)
        r = 31 * r + (error?.hashCode() ?: 0)
        r = 31 * r + (previewCode?.hashCode() ?: 0)
        r = 31 * r + (previewPrevCode?.hashCode() ?: 0)
        r = 31 * r + (previewNextCode?.hashCode() ?: 0)
        r = 31 * r + (previewSecondsLeft ?: 0)
        r = 31 * r + expertAction.hashCode()
        r = 31 * r + (successDialog?.hashCode() ?: 0)
        r = 31 * r + (successInfo?.hashCode() ?: 0)
        r = 31 * r + expertLog.hashCode()
        return r
    }
}

/**
 * A human-readable error. [title] is a short heading, [body] explains what
 * happened in the user's own terms, and [primaryAction]/[secondaryAction] label
 * the buttons that resolve it. Nothing here mentions APDUs, SW codes, or SM4.
 */
data class FriendlyError(
    val title: String,
    val body: String,
    val primaryAction: String,
    val secondaryAction: String? = null,
    val kind: Kind,
) {
    enum class Kind {
        WRONG_QR_SERIAL,     // they scanned the serial number
        WRONG_QR_OTHER,      // some other QR / link
        WRONG_QR_MS_PUSH,    // phonefactor:// Microsoft push
        WRONG_QR_FIDO,       // FIDO:/ passkey
        WRONG_QR_MIGRATION,  // otpauth-migration:// export
        NOT_A_SECRET,        // unrecognisable input
        SECRET_TOO_SHORT,
        NOT_A_TOKEN2,        // NFC tag that isn't one of ours
        UNKNOWN_MODEL,       // Token2-ish but serial prefix unknown -> refuse
        AUTH_LOCKED,         // device key locked (6983)
        TOKEN_MOVED,         // transport lost mid-write
        DEVICE_REJECTED,     // other SW
        NFC_OFF,             // NFC disabled in system settings
        NO_NFC,              // device has no NFC at all
        TOKEN_OFF,           // token not in programming mode
        SECURITY_KEY,        // tapped a FIDO/OATH key — wrong app
        UNKNOWN_SMARTCARD,   // responds but isn't anything we know
    }

    companion object {
        fun forWrongKind(w: SeedInput.Result.WrongKind): FriendlyError = when (w.kind) {
            SeedInput.Result.WrongKind.Kind.LOOKS_LIKE_SERIAL -> FriendlyError(
                title = "That's the serial number, not the secret",
                body = "You scanned the barcode printed on the token (its serial number). " +
                    "The secret is the QR code your service shows on screen when you add a " +
                    "new authenticator — it starts with \"otpauth://\" and contains a long " +
                    "string of letters and numbers.\n\nLook for a QR code on the website or " +
                    "app you're setting up, not on the token itself.",
                primaryAction = "Scan again",
                secondaryAction = "Enter secret by hand",
                kind = Kind.WRONG_QR_SERIAL,
            )
            SeedInput.Result.WrongKind.Kind.MS_PUSH -> FriendlyError(
                title = "That's a Microsoft push code",
                body = "You scanned a Microsoft \"phone approval\" code. That method sends a " +
                    "prompt to a phone app and can't be stored on a hardware token.\n\n" +
                    "Go back in your Microsoft or Entra security setup and choose " +
                    "\"I want to use a different authenticator app\" (not \"Microsoft " +
                    "Authenticator\"). You'll then be shown a QR code with a secret key — " +
                    "scan that one instead.",
                primaryAction = "Scan the other code",
                secondaryAction = "Enter secret by hand",
                kind = Kind.WRONG_QR_MS_PUSH,
            )
            SeedInput.Result.WrongKind.Kind.FIDO_PASSKEY -> FriendlyError(
                title = "That's a passkey code",
                body = "You scanned a passkey / security-key registration code. This kind of " +
                    "token stores time-based codes (TOTP), not passkeys, so this code doesn't " +
                    "apply.\n\nIf your service also offers \"authenticator app\" or " +
                    "\"time-based one-time code\" as an option, choose that — it will show a " +
                    "QR code with a secret key you can scan here.",
                primaryAction = "Scan the other code",
                secondaryAction = "Enter secret by hand",
                kind = Kind.WRONG_QR_FIDO,
            )
            SeedInput.Result.WrongKind.Kind.OTPAUTH_MIGRATION -> FriendlyError(
                title = "That's an export code",
                body = "You scanned an authenticator \"export\" or \"transfer\" code, which can " +
                    "hold several accounts at once. A token holds a single secret, so it can't " +
                    "read this.\n\nInstead, open the individual account's \"Add authenticator\" " +
                    "screen on the original service and scan the single QR code it shows.",
                primaryAction = "Scan again",
                secondaryAction = "Enter secret by hand",
                kind = Kind.WRONG_QR_MIGRATION,
            )
            SeedInput.Result.WrongKind.Kind.OTHER_URL -> FriendlyError(
                title = "That looks like a web link",
                body = "The code you scanned is a website address, not a token secret. " +
                    "The right QR code comes from the \"Add authenticator\" or " +
                    "\"Set up two-factor\" screen of the service you're securing.",
                primaryAction = "Scan again",
                secondaryAction = "Enter secret by hand",
                kind = Kind.WRONG_QR_OTHER,
            )
            SeedInput.Result.WrongKind.Kind.PLAIN_NUMBER -> FriendlyError(
                title = "That's just a number",
                body = "A token secret is a mix of letters and numbers (for example, " +
                    "\"JBSWY3DPEHPK3PXP\"). What you entered is only digits, so it can't be " +
                    "a secret. If you were copying the serial number, that's not what goes here.",
                primaryAction = "Try again",
                secondaryAction = null,
                kind = Kind.NOT_A_SECRET,
            )
            SeedInput.Result.WrongKind.Kind.TOO_SHORT -> FriendlyError(
                title = "That secret is too short",
                body = "A valid secret is at least 16 characters. Double-check you copied the " +
                    "whole thing — it's easy to miss the end.",
                primaryAction = "Try again",
                secondaryAction = null,
                kind = Kind.SECRET_TOO_SHORT,
            )
            SeedInput.Result.WrongKind.Kind.EMPTY -> FriendlyError(
                title = "Nothing to read yet",
                body = "Scan the QR code from your service, or type the secret in by hand.",
                primaryAction = "Scan QR code",
                secondaryAction = "Enter by hand",
                kind = Kind.NOT_A_SECRET,
            )
        }

        fun forUnusable(detail: String): FriendlyError = FriendlyError(
            title = "That doesn't look like a secret",
            body = "$detail\n\nThe right code comes from the \"Add authenticator\" screen of " +
                "the service you're securing and starts with \"otpauth://\".",
            primaryAction = "Scan again",
            secondaryAction = "Enter by hand",
            kind = Kind.NOT_A_SECRET,
        )

        fun unknownModel(serial: String): FriendlyError = FriendlyError(
            title = "This isn't a programmable Token2",
            body = "The token you tapped has serial $serial, which isn't one of the " +
                "programmable models this app can write to. To protect your other cards, " +
                "the app won't write to a device it doesn't recognise.\n\nMake sure you're " +
                "using a 2nd-generation programmable token (miniOTP-2/3, OTPC-P1/P2, or C301/C302).",
            primaryAction = "OK",
            kind = Kind.UNKNOWN_MODEL,
        )

        fun tokenOff(): FriendlyError = FriendlyError(
            title = "Turn the token on first",
            body = "The token has to be awake and in programming mode before the phone can " +
                "reach it. Press and hold its button until the display lights up, then tap it " +
                "to the phone straight away.",
            primaryAction = "Got it",
            kind = Kind.TOKEN_OFF,
        )

        fun securityKey(hasOath: Boolean): FriendlyError = FriendlyError(
            title = "That's a security key, not a token",
            body = buildString {
                append("You tapped a FIDO security key (the kind you tap to log in), not a ")
                append("standalone programmable token.\n\n")
                if (hasOath) {
                    append("This key can store time-based codes, but they're managed in a ")
                    append("different app — Libre Key Companion or Token2 Companion, not this ")
                    append("app. Open one of those, tap the key, and add the code there.\n\n")
                } else {
                    append("Codes for this key are handled by a different app — Libre Key ")
                    append("Companion or Token2 Companion, not this app.\n\n")
                }
                append("This app is only for the standalone programmable tokens ")
                append("(miniOTP-2/3, OTPC-P1/P2, or C301/C302) that show a code on their own screen.")
            },
            primaryAction = "Got it",
            kind = Kind.SECURITY_KEY,
        )

        fun unknownSmartcard(): FriendlyError = FriendlyError(
            title = "That card isn't a Token2 token",
            body = "The phone reached a smart card, but it doesn't respond like a Token2 " +
                "programmable token. If you tapped a bank card, transit card, ID card, or a " +
                "security key by mistake, move it away and try again with the token.",
            primaryAction = "Try again",
            kind = Kind.UNKNOWN_SMARTCARD,
        )

        fun notAToken2(): FriendlyError = FriendlyError(
            title = "That's an NFC tag, but not a token",
            body = "The phone detected something, but it doesn't respond like a Token2 " +
                "programmable token. If you tapped a bank card, transit card, or another " +
                "phone by mistake, move it away and try again with the token.",
            primaryAction = "Try again",
            kind = Kind.NOT_A_TOKEN2,
        )

        fun authLocked(): FriendlyError = FriendlyError(
            title = "This token is locked",
            body = "The token refused programming because its security key is locked. " +
                "This usually means it isn't a factory-programmable token, or it has been " +
                "sealed by an administrator. Contact Token2 support with your serial number " +
                "if you believe this is a mistake.",
            primaryAction = "OK",
            kind = Kind.AUTH_LOCKED,
        )

        fun tokenMoved(): FriendlyError = FriendlyError(
            title = "The token moved too soon",
            body = "Contact was lost while writing. Nothing was damaged — the token simply " +
                "didn't receive the full update. Lay the token flat against the back of the " +
                "phone and hold it still until you see the success screen.",
            primaryAction = "Try again",
            kind = Kind.TOKEN_MOVED,
        )

        fun deviceRejected(sw: Int): FriendlyError = FriendlyError(
            title = "The token didn't accept that",
            body = "The token rejected the update (code %04X). Try tapping again. If it keeps " +
                "happening, restart the app and make sure no other NFC card is near the phone."
                .format(sw),
            primaryAction = "Try again",
            kind = Kind.DEVICE_REJECTED,
        )

        fun nfcOff(): FriendlyError = FriendlyError(
            title = "NFC is switched off",
            body = "This app talks to the token wirelessly over NFC, which is currently " +
                "disabled. Turn it on in your phone's settings, then come back.",
            primaryAction = "Open NFC settings",
            kind = Kind.NFC_OFF,
        )

        fun noNfc(): FriendlyError = FriendlyError(
            title = "This phone has no NFC",
            body = "Programming a token needs NFC, and this device doesn't have it. You can " +
                "use a different phone with NFC, or the desktop configurator with a USB NFC " +
                "reader.",
            primaryAction = "OK",
            kind = Kind.NO_NFC,
        )
    }
}
