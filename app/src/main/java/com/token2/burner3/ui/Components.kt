package com.token2.burner3.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The one loud button per screen: the current action. */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Quiet secondary action. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .heightIn(min = 52.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * An inline helper card. Used generously throughout the wizard to keep guidance
 * next to the thing it explains, rather than hidden behind a help button.
 */
/**
 * A prominent, attention-grabbing card for consequential warnings (e.g. an
 * action that erases data). Uses the error color family and a heavier title so
 * it can't be skimmed past. [strong] fills the card for maximum emphasis.
 */
@Composable
fun WarningCard(
    title: String,
    body: String,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg = if (strong) MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun HintCard(
    title: String,
    body: String,
    accent: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = accent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A labelled value row, monospaced for anything the user must read precisely. */
@Composable
fun DataRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * The animated "tap target" ring shown while waiting for the token. A slow
 * breathing pulse signals "waiting" without being frantic — matching the calm
 * instrument tone. Respects reduced-motion by falling back to a static ring.
 */
@Composable
fun TapPulse(
    reduceMotion: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val scale = if (reduceMotion) 1f else {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scale",
        ).value
    }
    Box(
        modifier = modifier
            .size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(180.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(90.dp))
        )
        Box(
            Modifier
                .size(120.dp)
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(60.dp))
        )
        Text("Tap", style = MaterialTheme.typography.headlineSmall, color = color)
    }
}
