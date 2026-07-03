@file:OptIn(ExperimentalMaterial3Api::class)

package com.token2.burner3.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.token2.burner3.R
import com.token2.burner3.nfc.Token2Protocol
import com.token2.burner3.otp.OtpAlgorithm
import com.token2.burner3.otp.SeedInput
import com.token2.burner3.ui.wizard.ExpertAction
import com.token2.burner3.ui.wizard.FriendlyError
import com.token2.burner3.ui.wizard.Step
import com.token2.burner3.ui.wizard.WizardState
import com.token2.burner3.ui.wizard.WizardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    vm: WizardViewModel,
    reduceMotion: Boolean,
    onScanRequested: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenContact: () -> Unit = {},
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.expertMode -> "Expert console"
                            state.timeSyncMode -> "Time sync"
                            state.identifyMode -> "Identify a device"
                            else -> "Program a TOTP Token"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    when {
                        state.expertMode -> BackNavButton("Wizard", vm::exitExpertMode)
                        state.timeSyncMode -> BackNavButton("Wizard", vm::exitTimeSync)
                        state.identifyMode -> BackNavButton("Wizard", vm::exitIdentify)
                        state.step in listOf(Step.ScanSecret, Step.ConfirmSecret, Step.PowerOn, Step.TapToWrite) ->
                            BackNavButton("Back", vm::back)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            BrandBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
            if (state.expertMode) {
                ExpertConsole(state, vm, onScanRequested)
            } else if (state.timeSyncMode) {
                TimeSyncScreen(state, vm)
            } else if (state.identifyMode) {
                IdentifyScreen(state, vm)
            } else {
                StepDots(state.step)
                Spacer(Modifier.height(20.dp))

                when (state.step) {
                    Step.Welcome -> WelcomeStep(
                        onStart = vm::start,
                        onExpert = vm::enterExpertMode,
                        onTimeSync = vm::enterTimeSync,
                        onIdentify = vm::enterIdentify,
                        onOpenContact = onOpenContact,
                    )
                    Step.ScanSecret -> ScanStep(state, vm, onScanRequested)
                    Step.ConfirmSecret -> ConfirmStep(state, vm)
                    Step.PowerOn -> PowerOnStep(reduceMotion, onReady = { vm.goTo(Step.TapToWrite) })
                    Step.TapToWrite -> TapStep(state, reduceMotion)
                    Step.Writing -> WritingStep(state)
                    Step.Done -> DoneStep(state, vm)
                }
            }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Errors surface as a modal sheet so guidance is unmissable.
    state.error?.let { err ->
        ErrorSheet(
            error = err,
            onPrimary = {
                vm.dismissError()
                when (err.kind) {
                    FriendlyError.Kind.WRONG_QR_SERIAL,
                    FriendlyError.Kind.WRONG_QR_OTHER,
                    FriendlyError.Kind.WRONG_QR_MS_PUSH,
                    FriendlyError.Kind.WRONG_QR_FIDO,
                    FriendlyError.Kind.WRONG_QR_MIGRATION -> onScanRequested()
                    FriendlyError.Kind.NFC_OFF -> onOpenNfcSettings()
                    else -> {}
                }
            },
            onSecondary = { vm.dismissError() },
        )
    }

    // Success confirmation after a completed write (wizard / expert / time sync).
    state.successDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissSuccessDialog() },
            icon = {
                DottedBrandIcon(
                    icon = BrandIcon.SHIELD_CHECK,
                    size = 40.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 2.4f,
                )
            },
            title = { Text("Success") },
            text = {
                Column {
                    Text(message)
                    state.successInfo?.takeIf { it.isNotEmpty() }?.let { info ->
                        Spacer(Modifier.height(16.dp))
                        info.forEachIndexed { i, (label, value) ->
                            if (i > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(96.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissSuccessDialog() }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun BackNavButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun StepDots(step: Step) {
    val order = listOf(Step.Welcome, Step.ScanSecret, Step.ConfirmSecret, Step.PowerOn, Step.TapToWrite)
    val idx = order.indexOf(step).let { if (it < 0) order.size - 1 else it }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        order.forEachIndexed { i, _ ->
            val active = i <= idx
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            ) {}
        }
    }
}

// -- Steps --------------------------------------------------------------------

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    onExpert: () -> Unit,
    onTimeSync: () -> Unit,
    onIdentify: () -> Unit,
    onOpenContact: () -> Unit,
) {
    Column {
        Text("Set up your token", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "This app writes a secret key and the current time onto a Token2 " +
                "programmable token by holding it against the back of your phone. " +
                "It takes about a minute.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        HintCard(
            title = "You'll need two things",
            body = "1. The programmable token itself (miniOTP-2/3, OTPC-P1/P2, or C301/C302).\n" +
                "2. The QR code from the service you're securing — shown on its " +
                "\"Add authenticator\" or \"Two-factor\" screen.",
        )
        Spacer(Modifier.height(12.dp))
        HintCard(
            title = "Common mix-up",
            body = "Don't scan the barcode on the token — that's just its serial number. " +
                "The secret QR comes from the website or app you're protecting.",
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Start", leadingIcon = Icons.Filled.PlayArrow, onClick = onStart)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Identify", onClick = onIdentify, modifier = Modifier.weight(1f), fillWidth = false, leadingIcon = Icons.Filled.Info)
            GhostButton("Time sync", onClick = onTimeSync, modifier = Modifier.weight(1f), fillWidth = false, leadingIcon = Icons.Filled.Schedule)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Expert mode", onClick = onExpert, leadingIcon = Icons.Filled.Tune)

        Spacer(Modifier.height(28.dp))
        AppFooter(onOpenContact)
    }
}

/** Company / copyright line with a contact link, shown on the welcome screen. */
@Composable
private fun AppFooter(onOpenContact: () -> Unit) {
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(14.dp))
        Text(
            "TOKEN2 NFC Burner",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "© $year Token2 Sàrl · Version 3.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenContact) {
            Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Contact us")
        }
    }
}

@Composable
private fun ScanStep(state: WizardState, vm: WizardViewModel, onScanRequested: () -> Unit) {
    Column {
        Text("Add the secret", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the QR code your service shows you. If it also shows the key as text, you " +
                "can type that in instead.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        PrimaryButton("Scan QR code", leadingIcon = Icons.Filled.QrCodeScanner, onClick = onScanRequested)
        Spacer(Modifier.height(20.dp))

        Text(
            "or type the secret key",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "The short code shown next to the QR, usually 16–32 letters and numbers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.rawInput,
            onValueChange = vm::onInputChanged,
            label = { Text("Secret key") },
            placeholder = { Text("JBSWY3DPEHPK3PXP") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        // Live, gentle feedback as they type — before they even press continue.
        LiveInputHint(state)

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            "Continue",
            enabled = state.rawInput.isNotBlank(),
            onClick = vm::submitSecret,
        )
    }
}

@Composable
private fun LiveInputHint(state: WizardState) {
    val raw = state.rawInput.trim()
    if (raw.isBlank()) return
    // Cheap, non-committal classification for inline reassurance.
    val quick = remember(raw) { SeedInput.parse(raw, state.tokenInfo?.serial) }
    val (title, body, ok) = when (quick) {
        is SeedInput.Result.Valid -> Triple(
            "Looks like a valid secret",
            "We'll confirm the details on the next screen.",
            true,
        )
        is SeedInput.Result.WrongKind -> when (quick.kind) {
            SeedInput.Result.WrongKind.Kind.LOOKS_LIKE_SERIAL -> Triple(
                "That's the serial number",
                "The serial identifies the token — it's not the secret. Look for the " +
                    "QR on the service you're setting up.",
                false,
            )
            SeedInput.Result.WrongKind.Kind.MS_PUSH -> Triple(
                "Microsoft push code",
                "Choose \"use a different authenticator app\" in Microsoft's setup to get a " +
                    "scannable secret.",
                false,
            )
            SeedInput.Result.WrongKind.Kind.FIDO_PASSKEY -> Triple(
                "Passkey code", "This token stores time-based codes, not passkeys.", false,
            )
            SeedInput.Result.WrongKind.Kind.OTPAUTH_MIGRATION -> Triple(
                "Export code", "Scan a single account's QR, not the export code.", false,
            )
            SeedInput.Result.WrongKind.Kind.OTHER_URL -> Triple(
                "That's a web link", "Not a token secret. Scan the authenticator QR instead.", false,
            )
            SeedInput.Result.WrongKind.Kind.PLAIN_NUMBER -> Triple(
                "Numbers only", "A secret has letters too.", false,
            )
            SeedInput.Result.WrongKind.Kind.TOO_SHORT -> Triple(
                "Looks incomplete", "Check you copied the whole secret.", false,
            )
            SeedInput.Result.WrongKind.Kind.EMPTY -> return
        }
        is SeedInput.Result.Unusable -> Triple(
            "Not recognised yet", "Keep typing, or scan the QR code.", false,
        )
    }
    Spacer(Modifier.height(12.dp))
    HintCard(
        title = title,
        body = body,
        accent = if (ok) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
    )
}

@Composable
private fun ConfirmStep(state: WizardState, vm: WizardViewModel) {
    val parsed = state.parsed as? SeedInput.Result.Valid
    var showParamsDialog by remember { mutableStateOf(false) }

    Column {
        Text("Confirm the details", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Here's what we understood. Check it matches the service you're setting up.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                parsed?.issuer?.let { DataRow("Service", it) }
                parsed?.label?.let { DataRow("Account", it) }
                DataRow(
                    "Secret length",
                    "${state.seedBytes?.size ?: 0} bytes",
                )
                DataRow(
                    "Source",
                    when (parsed?.source) {
                        SeedInput.Source.OTPAUTH_URI -> "Scanned QR (otpauth)"
                        SeedInput.Source.BASE32_SECRET -> "Typed secret"
                        SeedInput.Source.HEX_SECRET -> "Typed secret (hex)"
                        null -> "—"
                    },
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))

                // TOTP parameters shown inline, editable via the change icon.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TOTP parameters",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showParamsDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Change parameters", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Change")
                    }
                }
                Spacer(Modifier.height(2.dp))
                DataRow("Algorithm", if (state.algorithm == OtpAlgorithm.SHA256) "SHA256" else "SHA1")
                DataRow("Code refresh", "${state.periodSeconds} sec")
                DataRow(
                    "Screen timeout",
                    listOf("15s", "30s", "60s", "120s").getOrElse(state.displayTimeoutIndex) { "30s" },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SecretRevealCard(state, vm)

        Spacer(Modifier.height(16.dp))
        HintCard(
            title = "Your secret stays private",
            body = "The secret is written directly to the token over NFC and never leaves " +
                "your phone. It isn't sent anywhere.",
        )

        Spacer(Modifier.height(24.dp))
        PrimaryButton("Looks right — continue", onClick = { vm.goTo(Step.PowerOn) })
        Spacer(Modifier.height(10.dp))
        GhostButton("No, scan again", onClick = { vm.goTo(Step.ScanSecret) })
    }

    if (showParamsDialog) {
        ParamsDialog(state, vm, onDismiss = { showParamsDialog = false })
    }
}

/** A focused dialog to change the TOTP parameters, opened from the confirm page. */
@Composable
private fun ParamsDialog(state: WizardState, vm: WizardViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("TOTP parameters", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "If you scanned a QR code these are already set. Only change them if your " +
                        "service specifically requires it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                SettingBlock(
                    label = "Algorithm",
                    help = "Almost all services use SHA1.",
                ) {
                    SegmentedChoice(
                        options = listOf("SHA1", "SHA256"),
                        selectedIndex = if (state.algorithm == OtpAlgorithm.SHA256) 1 else 0,
                        onSelect = { vm.setAlgorithm(if (it == 1) OtpAlgorithm.SHA256 else OtpAlgorithm.SHA1) },
                    )
                }
                Spacer(Modifier.height(18.dp))

                SettingBlock(
                    label = "Code refresh",
                    help = "How often the token shows a new code. 30 seconds is standard.",
                ) {
                    SegmentedChoice(
                        options = listOf("30 sec", "60 sec"),
                        selectedIndex = if (state.periodSeconds == 60) 1 else 0,
                        onSelect = { vm.setPeriod(if (it == 1) 60 else 30) },
                    )
                }
                Spacer(Modifier.height(18.dp))

                SettingBlock(
                    label = "Screen timeout",
                    help = "How long the token's display stays lit after a button press.",
                ) {
                    SegmentedChoice(
                        options = listOf("15s", "30s", "60s", "120s"),
                        selectedIndex = state.displayTimeoutIndex,
                        onSelect = vm::setDisplayTimeout,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

/**
 * Shows the secret in Base32, hidden behind dots by default with an eye toggle.
 * Reveals for people who want to double-check or hand-correct it; edits are
 * validated inline and only applied when they parse.
 */
@Composable
private fun SecretRevealCard(state: WizardState, vm: WizardViewModel) {
    var revealed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val base32 = remember(state.seedBytes) {
        state.seedBytes?.let { SeedInput.base32Encode(it) }.orEmpty()
    }
    var draft by remember(base32) { mutableStateOf(base32) }
    var editError by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SECRET KEY (BASE32)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide secret" else "Show secret",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = {
                        editing = !editing
                        if (editing) revealed = true else { draft = base32; editError = null }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit secret",
                            tint = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; editError = null },
                    singleLine = false,
                    minLines = 2,
                    isError = editError != null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                editError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (vm.applyEditedBase32(draft)) {
                                editing = false; editError = null
                            } else {
                                editError = "That isn't a valid Base32 secret. Use A–Z and 2–7 only."
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Save") }
                    OutlinedButton(
                        onClick = { editing = false; draft = base32; editError = null },
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Cancel") }
                }
            } else {
                Text(
                    if (revealed) base32.chunked(4).joinToString(" ") else "•".repeat(base32.length.coerceAtMost(32)),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingBlock(label: String, help: String, control: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            help,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        control()
    }
}

@Composable
private fun LabeledControl(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        control()
    }
}

@Composable
private fun SegmentedChoice(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    // One connected pill with a subtle track, thin gaps, and a compact height.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { i, opt ->
                val selected = i == selectedIndex
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp),
                    onClick = { onSelect(i) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            opt,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerOnStep(reduceMotion: Boolean, onReady: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Wake the token",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Before it can be programmed, the token has to be switched on. " +
                "Press and hold its button until the screen lights up.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        PowerButtonAnimation(reduceMotion)

        Spacer(Modifier.height(28.dp))
        HintCard(
            title = "How you know it worked",
            body = "The token's display shows digits or dashes. If it stays blank, press and " +
                "hold the button a little longer.",
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("It's on — continue", leadingIcon = Icons.Filled.Bolt, onClick = onReady)
    }
}

/**
 * The wake-up illustration: a real photo of the token with its screen off,
 * crossfading to the same token with its screen lit, on a gentle loop — so it
 * reads as "press the button and it wakes up" using actual hardware. Under
 * reduced motion it just shows the lit photo.
 *
 * The two images live at res/drawable-nodpi/token_off.png and token_on.png.
 * Swapping those files for new product photos needs no code change.
 */
@Composable
private fun PowerButtonAnimation(reduceMotion: Boolean) {
    // One looping clock drives both the screen crossfade and the button pulse.
    val t = rememberInfiniteTransition(label = "wake")
    val phase = if (reduceMotion) 1f else t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    ).value

    // Screen: off for the first third, then fades to lit and holds.
    val litAlpha = when {
        reduceMotion -> 1f
        phase < 0.30f -> 0f
        phase < 0.45f -> (phase - 0.30f) / 0.15f
        else -> 1f
    }
    // Button press "pulse": a quick glow that peaks just before the screen lights,
    // suggesting the press that wakes the token.
    val pressPulse = if (reduceMotion) 0f else {
        val p = phase
        when {
            p < 0.12f -> p / 0.12f          // ramp up
            p < 0.30f -> 1f - (p - 0.12f) / 0.18f  // fade out
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    // The button sits on the token's red body, so a red glow wouldn't show. Use a
    // high-contrast white pulse instead.
    val glowColor = androidx.compose.ui.graphics.Color.White

    // Power-button position and its outer dotted-ring radius (measured from the SVG).
    val btnCxF = 0.162f; val btnCyF = 0.496f
    val ringRF = 0.111f   // outer dotted ring radius, as a fraction of art width

    // The white backing matches the token's own rounded rectangle: same aspect,
    // same corner radius, so white shows only inside the token outline (not as a
    // larger box around it). The token image is inset by the outline thickness so
    // its dark border sits over the white edge.
    val tokenAspect = 926f / 441f
    BoxWithConstraints(
        Modifier.fillMaxWidth(0.86f).aspectRatio(tokenAspect),
        contentAlignment = Alignment.Center,
    ) {
        val cornerR = maxHeight * 0.13f  // matches the token's rounded corners
        // White fill, inset so it stays within the dark outline.
        Surface(
            color = androidx.compose.ui.graphics.Color.White,
            shape = RoundedCornerShape(cornerR),
            modifier = Modifier.fillMaxSize().padding(6.dp),
        ) {}
        Image(
            painter = painterResource(R.drawable.token_off),
            contentDescription = "Token with its screen off",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.token_on),
            contentDescription = "Token with its screen lit",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(litAlpha),
        )
        // Button press glow, drawn over the power button.
        Canvas(Modifier.fillMaxSize()) {
            if (pressPulse <= 0f) return@Canvas
            // The artwork fills this aspect-locked box, so its drawn rect == size.
            val drawnW = size.width; val drawnH = size.height
            val left = 0f; val top = 0f
            val cx = left + btnCxF * drawnW
            val cy = top + btnCyF * drawnH
            val ringR = ringRF * drawnW
            // A soft pulse that traces the button's outer dotted ring: a ring at the
            // ring radius plus a gentle fill, both fading with the press pulse.
            drawCircle(
                color = glowColor.copy(alpha = 0.85f * pressPulse),
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = ringR * 0.18f),
            )
            drawCircle(
                color = glowColor.copy(alpha = 0.28f * pressPulse),
                radius = ringR * 0.92f,
                center = Offset(cx, cy),
            )
        }
    }
}

@Composable
private fun TapStep(state: WizardState, reduceMotion: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Hold the token to the back of the phone",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No need to press anything — just rest the token flat against the back of the " +
                "phone and keep it there.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        HoldToPhoneAnimation(reduceMotion)
        Spacer(Modifier.height(28.dp))
        HintCard(
            title = "Where's the antenna?",
            body = "The NFC antenna is usually near the top of the phone, but on some models " +
                "it's in the middle. If nothing happens, slide the token slowly up and down " +
                "the centre-line of the back until it connects.",
        )
        Spacer(Modifier.height(12.dp))
        state.tokenInfo?.let { info ->
            HintCard(
                title = "Detected",
                body = "${info.model ?: "Unknown"} · serial ${info.serial}",
            )
            Spacer(Modifier.height(12.dp))
        }
        HintCard(
            title = "Nothing happening?",
            body = "Take off any case, and make sure no bank or transit card is stuck to the " +
                "phone — those can block the token.",
        )
        Spacer(Modifier.height(12.dp))
        HintCard(
            title = "Using a FIDO key?",
            body = "If your device is a tap-to-login security key (like a T2F2 card), this " +
                "isn't the right app — its codes are managed in Libre Key Companion or " +
                "Token2 Companion. This app is only for standalone tokens that show a code " +
                "on their own screen.",
        )
    }
}

/**
 * A clear "hold the token to the phone" visual: the phone (back view) illustration
 * with the token resting on its upper area — the usual NFC sweet spot — and
 * contactless arcs pulsing between them to show the wireless link. Uses the real
 * phone-back and token artwork so it's immediately recognisable.
 */
@Composable
private fun HoldToPhoneAnimation(reduceMotion: Boolean) {
    val pulse = if (reduceMotion) 0.5f else {
        val t = rememberInfiniteTransition(label = "nfc")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse",
        ).value
    }

    val phoneAspect = 700f / 1399f  // phone_back.png width/height

    Box(
        Modifier.fillMaxWidth().height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A container sized to the phone's actual footprint, so everything inside
        // scales with the phone rather than with the (possibly wide) outer Box.
        // This keeps proportions correct in both portrait and landscape.
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .aspectRatio(phoneAspect),
            contentAlignment = Alignment.Center,
        ) {
            val phoneW = maxWidth
            // Phone-back illustration fills this container.
            Image(
                painter = painterResource(R.drawable.phone_back),
                contentDescription = "Back of a phone",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // The token, on a white backing shaped exactly like the token's rounded
            // rectangle (so white shows only inside the outline), resting on the
            // phone's upper area. Sized relative to the phone width for correct
            // proportions in any orientation.
            val tokenW = phoneW * 0.72f
            val tokenAspect = 926f / 441f
            Box(
                Modifier
                    .width(tokenW)
                    .aspectRatio(tokenAspect)
                    .align(Alignment.Center)
                    .offset(y = -(phoneW * 0.34f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = RoundedCornerShape(tokenW * 0.13f / tokenAspect),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                ) {}
                Image(
                    painter = painterResource(R.drawable.token_on),
                    contentDescription = "Token held against the back of the phone",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Contactless waves radiating upward from the phone's center toward the
            // token above, in white for contrast against the dark phone back.
            Canvas(Modifier.fillMaxSize()) {
                // Origin at the phone's centre; waves arc upward (opening toward the
                // token). Successive arcs ripple outward on a loop.
                val originX = size.width * 0.5f
                val originY = size.height * 0.56f
                for (i in 0..3) {
                    val local = ((pulse * 4f) - i).coerceIn(0f, 1f)
                    val alpha = (1f - kotlin.math.abs(local - 0.5f) * 2f).coerceIn(0f, 1f)
                    val r = size.width * (0.12f + i * 0.11f)
                    // Upward-opening arc: centred on the origin, sweeping across the
                    // top (from 200° to 340°, i.e. the upper span).
                    drawArc(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(originX - r, originY - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(2f, 16f), 0f,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WritingStep(state: WizardState) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(
            state.busyMessage ?: "Working…",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Keep the token still against the phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        state.tokenInfo?.let { info ->
            Spacer(Modifier.height(16.dp))
            Text(
                "${info.model ?: "Unknown model"} · ${info.serial}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CodeChip(label: String, code: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            code.chunked(3).joinToString(" "),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoneStep(state: WizardState, vm: WizardViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Brand hero: the dotted shield-check motif from the Token2 packaging,
        // scaling in on arrival.
        val heroScale = remember { Animatable(0.7f) }
        LaunchedEffect(Unit) { heroScale.animateTo(1f, tween(500)) }
        Box(
            Modifier
                .padding(top = 8.dp)
                .scale(heroScale.value)
                .size(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(96.dp),
            ) {}
            DottedBrandIcon(
                icon = BrandIcon.SHIELD_CHECK,
                size = 56.dp,
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.4f,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Done", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "The token is programmed and its clock is set.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        HintCard(
            title = "First: turn the token off, then on",
            body = "Switch the token off and back on before checking it. Until you do, it may " +
                "still be showing an old code from before it was programmed. A fresh power-on " +
                "makes it pick up the new secret and clock.",
        )
        Spacer(Modifier.height(16.dp))

        state.previewCode?.let { current ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "THEN CHECK IT MATCHES YOUR TOKEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    // Current code — the emphasised one.
                    Text(
                        current.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Previous / next codes, quieter, shown as boundary fallbacks.
                    if (state.previewPrevCode != null || state.previewNextCode != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            state.previewPrevCode?.let {
                                CodeChip(label = "just before", code = it)
                            }
                            state.previewNextCode?.let {
                                CodeChip(label = "just after", code = it)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "After turning the token off and on, it should show the big code above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Codes change every ${state.periodSeconds} seconds. If you're checking " +
                            "right as it ticks over, the token may briefly show one of the two " +
                            "smaller codes instead — any of the three is a correct match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton("Finish", leadingIcon = Icons.Filled.Check, onClick = vm::startOver)
        Spacer(Modifier.height(10.dp))
        GhostButton(
            "Program another (clone)",
            leadingIcon = Icons.Filled.Contactless,
            onClick = vm::restartForAnotherToken,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "\"Program another\" writes the same secret and settings to a second token — " +
                "a clone of this one. Use it to make backups.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// -- Identify -----------------------------------------------------------------

/**
 * A read-only screen that reports everything the app can learn about whatever is
 * tapped — Token2 token, FIDO key, or any contactless card. No writes occur.
 */
@Composable
private fun IdentifyScreen(state: WizardState, vm: WizardViewModel) {
    Column {
        Text("Identify a device", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Power on the device (if it has a button) and hold it to the back of the " +
                "phone. Nothing is written — this only reads.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (state.identifyReport == null) {
            HintCard(
                title = "Waiting for a tap",
                body = "Hold any NFC device flat against the phone. Token2 tokens show " +
                    "their model, serial and clock; other devices show whatever they expose.",
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        state.identifyHeadline ?: "Result",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    state.identifyReport.forEachIndexed { i, (label, value) ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            GhostButton("Scan another", onClick = vm::enterIdentify, leadingIcon = Icons.Filled.Contactless)
        }
    }
}

// -- Time sync ----------------------------------------------------------------

/**
 * A focused screen for setting the token's clock. Time can only be written as
 * part of the device's configuration (there is no time-only wire command), and
 * on some models that clears the stored seed as a replay-attack safeguard — so
 * the screen leads with that warning, tailored to the detected model.
 */
@Composable
private fun TimeSyncScreen(state: WizardState, vm: WizardViewModel) {
    val serial = state.tokenInfo?.serial
    val model = state.tokenInfo?.model
    // Restricted-sync models clear the seed on a time write; the three unrestricted
    // models (miniOTP-2, OTPC-P1, C302) keep it. Only meaningful once identified.
    val clearsForDetected: Boolean? = serial?.let { s: String -> Token2Protocol.timeSyncClearsSeed(s) }
    var acknowledged by remember { mutableStateOf(false) }

    Column {
        Text("Sync the token's clock", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use this if the token's codes have drifted out of sync. It sets the token's " +
                "clock to the time you choose below.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // One-time warning: shown until the user clicks OK, then it collapses to
        // a small reminder line so it doesn't nag on every glance.
        if (!acknowledged) {
            WarningCard(
                title = "Time sync can erase the secret",
                body = "On most token models, syncing the time deliberately wipes the stored " +
                    "secret to protect against replay attacks, and you'll have to program it " +
                    "again afterwards.\n\n" +
                    "Only three models keep their secret through a time sync: miniOTP-2, " +
                    "OTPC-P1, and C302. Every other model loses it.",
            )

            // Model-specific note once a token has been detected.
            if (state.tokenInfo != null) {
                Spacer(Modifier.height(12.dp))
                when (clearsForDetected) {
                    true -> WarningCard(
                        title = "This token (${model ?: serial}) will lose its secret",
                        body = "This model has restricted time-sync, so syncing its time now will " +
                            "erase the current secret — be ready to re-program it right after.",
                        strong = true,
                    )
                    false -> HintCard(
                        title = "This token (${model ?: serial}) keeps its secret",
                        body = "The detected model isn't in the seed-clearing group, so its secret " +
                            "should survive the time sync. Still, only sync if you need to.",
                    )
                    null -> {}
                }
            }

            Spacer(Modifier.height(12.dp))
            PrimaryButton("OK, I understand", onClick = { acknowledged = true })
        } else {
            HintCard(
                title = "Heads up",
                body = "Most models clear the secret when you sync time — re-program it " +
                    "afterwards. miniOTP-2, OTPC-P1 and C302 are the exceptions that keep it.",
            )
        }

        Spacer(Modifier.height(20.dp))

        // Time source.
        Text("Time to program", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val useCustom = state.timeSyncCustomEpoch != null
        SegmentedChoice(
            options = listOf("Phone's current time", "Custom time"),
            selectedIndex = if (useCustom) 1 else 0,
            onSelect = { idx ->
                if (idx == 0) vm.setTimeSyncCustom(null)
                else vm.setTimeSyncCustom(System.currentTimeMillis() / 1000)
            },
        )

        if (useCustom) {
            Spacer(Modifier.height(10.dp))
            CustomTimeEditor(
                epoch = state.timeSyncCustomEpoch!!,
                onChange = { vm.setTimeSyncCustom(it) },
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "The token will be set to your phone's clock (UTC) at the moment you tap it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        if (acknowledged) {
            HintCard(
                title = "Ready — power on the token, then hold it to the phone",
                body = "Switch the token on and hold it flat against the back of the phone. The " +
                    "log below shows what happens.",
            )
        } else {
            HintCard(
                title = "Read the warning above first",
                body = "The time sync won't run until you've acknowledged the warning.",
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = vm::clearTimeSyncLog) { Text("Clear") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (state.timeSyncLog.isEmpty()) {
                    Text(
                        "No activity yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.timeSyncLog.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    // Keep the ViewModel's "armed" flag in sync with the acknowledgement so a tap
    // only runs once the warning has been accepted.
    LaunchedEffect(acknowledged) { vm.setTimeSyncArmed(acknowledged) }
}

@Composable
private fun CustomTimeEditor(epoch: Long, onChange: (Long) -> Unit) {
    // A readable summary plus tap-to-open date and time pickers (UTC).
    val utcFmt = remember {
        java.text.SimpleDateFormat("EEE, d MMM yyyy  HH:mm 'UTC'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                utcFmt.format(java.util.Date(epoch * 1000)),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            // Row 1: date and time side by side.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDate = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick date")
                }
                OutlinedButton(
                    onClick = { showTime = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick time")
                }
            }
            Spacer(Modifier.height(8.dp))
            // Row 2: reset to now, full width.
            OutlinedButton(
                onClick = { onChange(System.currentTimeMillis() / 1000) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset to now")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "All times are UTC — the token keeps its clock in UTC.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = epoch * 1000)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { newDateMillis ->
                        // Keep the existing time-of-day, replace the date portion.
                        onChange(combineDateTime(newDateMillis, epoch, replaceDate = true))
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val cal = remember {
            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = epoch * 1000
            }
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(java.util.Calendar.MINUTE),
            is24Hour = true,
        )
        DatePickerDialog(  // reuse the dialog scaffold for the time picker
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(setTimeOfDay(epoch, timeState.hour, timeState.minute))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        ) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
        }
    }
}

/** Combine a picked date (UTC millis) with the time-of-day from [baseEpochSec]. */
private fun combineDateTime(dateMillis: Long, baseEpochSec: Long, replaceDate: Boolean): Long {
    val utc = java.util.TimeZone.getTimeZone("UTC")
    val date = java.util.Calendar.getInstance(utc).apply { timeInMillis = dateMillis }
    val base = java.util.Calendar.getInstance(utc).apply { timeInMillis = baseEpochSec * 1000 }
    val out = java.util.Calendar.getInstance(utc)
    out.set(
        date.get(java.util.Calendar.YEAR),
        date.get(java.util.Calendar.MONTH),
        date.get(java.util.Calendar.DAY_OF_MONTH),
        base.get(java.util.Calendar.HOUR_OF_DAY),
        base.get(java.util.Calendar.MINUTE),
        base.get(java.util.Calendar.SECOND),
    )
    out.set(java.util.Calendar.MILLISECOND, 0)
    return out.timeInMillis / 1000
}

/** Replace the time-of-day of [baseEpochSec] (UTC) with [hour]:[minute]. */
private fun setTimeOfDay(baseEpochSec: Long, hour: Int, minute: Int): Long {
    val utc = java.util.TimeZone.getTimeZone("UTC")
    val cal = java.util.Calendar.getInstance(utc).apply { timeInMillis = baseEpochSec * 1000 }
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000
}

// -- Expert console -----------------------------------------------------------

/**
 * A single-screen manual console for people who know the protocol. Everything
 * the wizard does in steps is exposed here as direct controls: load a secret,
 * set parameters, choose an action, then tap. A running log reports exactly what
 * happened on each tap, including precise failure reasons.
 */
@Composable
private fun ExpertConsole(state: WizardState, vm: WizardViewModel, onScanRequested: () -> Unit) {
    Column {
        Text("Expert console", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Manual control. Pick an action, then hold the token to the phone. READ never " +
                "writes; write actions authenticate first and refuse unknown models.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // Secret input — auto-loads as you type or scan.
        Text("Secret", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Paste a Base32 secret or an otpauth:// link, or scan a QR. Only the secret is " +
                "used — set the parameters below yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.rawInput,
            onValueChange = vm::onInputChanged,
            placeholder = { Text("JBSWY3DPEHPK3PXP…") },
            singleLine = false,
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onScanRequested, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan QR")
            }
            state.seedBytes?.let {
                Text(
                    "✓ ${it.size} bytes loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } ?: Text(
                "no secret loaded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Parameters
        Text("Parameters", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        LabeledControl("Algorithm") {
            SegmentedChoice(
                options = listOf("SHA1", "SHA256"),
                selectedIndex = if (state.algorithm == OtpAlgorithm.SHA256) 1 else 0,
                onSelect = { vm.setAlgorithm(if (it == 1) OtpAlgorithm.SHA256 else OtpAlgorithm.SHA1) },
            )
        }
        Spacer(Modifier.height(12.dp))
        LabeledControl("Time step") {
            SegmentedChoice(
                options = listOf("30s", "60s"),
                selectedIndex = if (state.periodSeconds == 60) 1 else 0,
                onSelect = { vm.setPeriod(if (it == 1) 60 else 30) },
            )
        }
        Spacer(Modifier.height(12.dp))
        LabeledControl(
            label = "Display sleep timeout",
            hint = "How long the token's screen stays lit — a device setting, not part of TOTP.",
        ) {
            SegmentedChoice(
                options = listOf("15s", "30s", "60s", "120s"),
                selectedIndex = state.displayTimeoutIndex,
                onSelect = vm::setDisplayTimeout,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Action selector — compact labels so each fits on one line.
        Text("Action on next tap", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val actions = listOf(
            ExpertAction.WRITE_BOTH to "Both",
            ExpertAction.WRITE_SEED to "Seed",
            ExpertAction.WRITE_CONFIG to "Config",
            ExpertAction.IDENTIFY to "Identify",
        )
        SegmentedChoice(
            options = actions.map { it.second },
            selectedIndex = actions.indexOfFirst { it.first == state.expertAction }.coerceAtLeast(0),
            onSelect = { vm.setExpertAction(actions[it].first) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (state.expertAction) {
                ExpertAction.WRITE_BOTH -> "Writes the secret and the clock/parameters. The usual choice."
                ExpertAction.WRITE_SEED -> "Writes only the secret. Leaves the clock and parameters untouched."
                ExpertAction.WRITE_CONFIG -> "Writes only the clock and parameters. Doesn't change the secret."
                ExpertAction.IDENTIFY -> "Reads the device only — no changes. Shows model, serial and on-device UTC time."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        HintCard(
            title = if (state.expertAction == ExpertAction.IDENTIFY) "Power on the device, then hold it to the phone"
            else "Ready — power on the device, then hold it to the phone",
            body = "Switch the token on and hold it flat against the back of the phone. The log " +
                "below updates as each step runs.",
        )

        Spacer(Modifier.height(16.dp))

        // Log
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = vm::clearExpertLog) { Text("Clear") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (state.expertLog.isEmpty()) {
                    Text(
                        "No activity yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.expertLog.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// -- Error sheet --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorSheet(
    error: FriendlyError,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onSecondary) {
        Column(Modifier.padding(24.dp)) {
            Text(error.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                error.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(error.primaryAction, onClick = onPrimary)
            error.secondaryAction?.let {
                Spacer(Modifier.height(10.dp))
                GhostButton(it, onClick = onSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
