package com.token2.burner3.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Small persisted app preferences. Kept deliberately tiny — SharedPreferences
 * is enough for a couple of booleans and avoids pulling in DataStore.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * NFC device pre-verification. When on (default), a tapped device must
     * answer the token info command with a known serial prefix before anything
     * is written, and FIDO/OATH keys or other smart cards are recognised and
     * explained. When off, the app skips those checks and goes straight to
     * authentication + write; the token's own auth handshake is the only gate.
     */
    var nfcPreVerification: Boolean
        get() = prefs.getBoolean(KEY_NFC_PRE_VERIFICATION, true)
        set(value) { prefs.edit().putBoolean(KEY_NFC_PRE_VERIFICATION, value).apply() }

    private companion object {
        const val FILE = "burner_settings"
        const val KEY_NFC_PRE_VERIFICATION = "nfc_pre_verification"
    }
}
