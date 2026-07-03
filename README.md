# Token2 NFC Burner — rebuilt

A modern, wizard-driven rebuild of the Token2 NFC Burner Android app for
2nd-generation single-profile programmable TOTP tokens
(miniOTP-2/3, OTPC-P1/P2, C301/C302).

The goal of this rebuild is **not** new capability — it writes exactly the same
bytes to the token as the official tool — but a far more forgiving experience,
built around the reality that most support tickets come from people scanning the
*wrong* QR code.

## What's different

- **Token2 house style.** Brand red (#F80041) on a dark ink base with a white
  contactless-token motif, matching the sibling apps (Libre Key Companion, the
  Companion apps) rather than inventing a separate identity.
- **Guided wizard** with a visible progress rail: Welcome → Add secret →
  Confirm (with TOTP parameters editable in a dialog) → Power on → Tap → Done.
- **Expert mode.** A single-screen manual console for people who know the
  protocol: load a secret, set parameters, choose an action (read / seed only /
  config only / seed+config), tap, and watch a running log report exactly what
  happened — including precise failure reasons.
- **View / edit the secret.** On the confirm screen the Base32 secret is hidden
  behind dots with an eye toggle to reveal it, and an edit button to hand-correct
  it (validated inline).
- **Manual entry** goes straight to the confirm/verify screen, same as a scan.
- **Power-on step** with a looping animation: the power button (on the left of
  the token) presses in, then the screen lights up. The power glyph is drawn on
  a Canvas, so it never renders as a missing-glyph box.
- **Verify correctly.** The success screen tells the user to switch the token
  off and on *before* comparing codes, so a stale pre-programming code doesn't
  cause a false mismatch.
- **Wrong-QR detection in plain language.** The app recognises and explains,
  specifically:
  - the token's own **serial-number** barcode (the #1 mistake),
  - **`phonefactor://`** Microsoft push/number-match codes — tells the user to
    pick *"use a different authenticator app"* in Entra/Microsoft setup,
  - **`FIDO:/…`** passkey / security-key codes — explains a TOTP token can't
    hold a passkey,
  - **`otpauth-migration://`** authenticator export codes,
  - generic web links, plain numbers, and truncated secrets.
  Detection runs both live-as-you-type and on scan.
- **Wrong-device detection over NFC.** Users often tap a FIDO security key
  (e.g. Token2 T2F2-NFC-Card) expecting to save a TOTP profile to it. That *is*
  possible, but it's handled by a different app. On tap, the app probes the
  standard FIDO (`A0000006472F0001`) and YKOATH (`A0000005272101`) applet AIDs
  by read-only SELECT; if either responds, it stops and tells the user their
  key's codes are managed in **Libre Key Companion or Token2 Companion**, not
  here. Unknown smart cards (bank, transit, ID) get their own gentle message.
- **Power-on step** with a looping power-button animation, because the token
  must be woken into programming mode before NFC can reach it.
- **Human-readable errors** as bottom sheets, each with a title, a plain
  explanation, and a concrete next action. No status words, no APDUs, no "SM4".
- **Verification on success**: shows a live TOTP so the user can confirm the
  token displays the same code.
- **No more pop-on-any-tag.** The original app auto-launched on any NFC tag
  (a top complaint in the reviews). This build only listens via foreground
  dispatch while open.
- **Refuses unknown devices.** If the serial prefix isn't a known programmable
  model, the app reads info but won't write — mirroring the reference tool.

## Protocol fidelity

The wire protocol is a byte-exact port of the official `token2_config.py`
reference (ISO 7816 APDUs, SM4 crypto, ISO/IEC 9797-1 MAC). The SM4
implementation in `crypto/Sm4.kt` was validated against the reference `sm4`
Python package used by the official tool: ECB output, CBC-MAC output, and a full
seed-write APDU (including `Lc` and the 4-byte MAC) all match to the byte.

Serial-prefix → model mapping (from `PROTOCOL.md`), used for both model naming
and the "you scanned the serial" check:

| Serial prefix | Model          |
| ------------- | -------------- |
| 8659612       | OTPC-P1-i      |
| 8659622       | OTPC-P2-i      |
| 8659621       | OTPC-P2-i-NB   |
| 8659600       | miniOTP-2-i    |
| 8659601       | miniOTP-3-i    |
| 8659609       | miniOTP-3-i-NB |
| 8659610       | C301-i         |
| 8659632       | C302-i         |

## Structure

```
crypto/Sm4.kt            SM4 block cipher (ECB + CBC/IV0), verified byte-exact
nfc/Token2Protocol.kt    APDU construction, auth handshake, seed/config framing
nfc/IsoDepTransport.kt   Android IsoDep-backed transport
otp/SeedInput.kt         The input classifier — the heart of the UX
otp/Totp.kt              RFC 6238, for the success-screen verification code
otp/…                    OtpAlgorithm
ui/wizard/…              Wizard state machine + FriendlyError catalogue
ui/…                     Compose screens, components, theme
MainActivity.kt          NFC foreground dispatch + QR launcher + Compose host
QrScanActivity.kt        CameraX + Play Services ML Kit QR reader (returns raw string only)
```

## Build

Open in Android Studio (Ladybug or newer), or from the command line:

```
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (configure signing first)
./gradlew :app:bundleRelease        # Play Store App Bundle (.aab)
```

Requires an NFC-capable device running Android 7.0 (API 24) or newer. The QR
scanner uses the unbundled Google Play Services ML Kit barcode model, so the
scanning feature needs Google Play Services present (standard on consumer
devices); the model downloads automatically on first install.

- **Application ID:** `com.token2.nfcburner2`
- **Version:** 3.0.0
- **min / target SDK:** 24 / 35

## Design language

Token2 house style: brand red (`#F80041`) as the single signal colour reserved
for the current action, restrained teal (`#3FBFA0`) for success, and a muted
clay for gentle errors (most are honest mistakes, not disasters). A dark ink
base (`#15171C`) with light, cool greys, and full light/dark theme support.
Precision values — serials, codes, the secret preview — are set in a monospaced
face.

## License

Released under the Apache License 2.0. See [LICENSE](LICENSE).

The fixed 16-byte device key and protocol details are properties of the token
firmware and are reproduced here only to interoperate with Token2's own
hardware; they are not secrets.

## Contact

Token2 — https://token2.swiss/contact

