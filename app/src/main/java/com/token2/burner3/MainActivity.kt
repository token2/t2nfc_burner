package com.token2.burner3

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.token2.burner3.nfc.IsoDepTransport
import com.token2.burner3.ui.WizardScreen
import com.token2.burner3.ui.theme.Token2Theme
import com.token2.burner3.ui.wizard.WizardViewModel

/**
 * Single-activity host. Responsibilities are deliberately thin:
 *  - own the NFC foreground dispatch and hand discovered tags to the ViewModel,
 *  - launch the QR scanner and feed its result to the ViewModel,
 *  - route the system Back gesture through the in-app navigation,
 *  - render the Compose wizard.
 *
 * The QR scanner uses a minimal contract; in a shipping build this would be
 * ML Kit or ZXing. It's isolated behind [scanLauncher] so the rest of the app
 * is scanner-agnostic.
 */
class MainActivity : ComponentActivity() {

    private val vm: WizardViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringExtra(QrScanActivity.EXTRA_RESULT)
        if (!text.isNullOrBlank()) {
            vm.onScanResult(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show the branded splash while the activity spins up, then hand off to
        // the main theme (configured via Theme.Token2Burner.Splash).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // System Back navigates within the app; it only leaves the app when the
        // ViewModel says there's nowhere left to go back to (i.e. the home screen).
        onBackPressedDispatcher.addCallback(this) {
            if (!vm.onSystemBack()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setContent {
            Token2Theme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WizardScreen(
                        vm = vm,
                        reduceMotion = isReduceMotionEnabled(),
                        onScanRequested = { launchScanner() },
                        onOpenNfcSettings = { openNfcSettings() },
                        onOpenContact = { openContactPage() },
                    )
                }
            }
        }

        // Surface an NFC-availability problem up front.
        if (nfcAdapter == null) {
            vm.showNoNfc()
        }
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag ?: return
        val transport = IsoDepTransport.from(tag)
        if (transport == null) {
            // Tag detected but not IsoDep — almost certainly not a Token2.
            vm.dismissError()
            vm.onTagRejected()
            return
        }
        vm.onTagTapped(transport)
    }

    private fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            vm.showNfcOff()
            return
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        val filters = arrayOf(android.content.IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val techLists = arrayOf(arrayOf(IsoDep::class.java.name))
        adapter.enableForegroundDispatch(this, pending, filters, techLists)
    }

    private fun launchScanner() {
        scanLauncher.launch(Intent(this, QrScanActivity::class.java))
    }

    private fun openNfcSettings() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    }

    private fun openContactPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://token2.swiss/contact")))
        } catch (_: Exception) {
            // No browser available; nothing to do.
        }
    }

    private fun isReduceMotionEnabled(): Boolean = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }
}
