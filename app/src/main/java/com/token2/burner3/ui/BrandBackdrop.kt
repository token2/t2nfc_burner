package com.token2.burner3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/**
 * A quiet, ambient backdrop that scatters the Token2 dotted security-icon motifs
 * across the screen. It echoes the hardware packaging — a soft gray field of keys,
 * locks, checks, users and rings — sitting far enough back to read as texture, not
 * decoration. Purely visual; ignores touches.
 */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier) {
    // Gray motif, a touch stronger than before so it's actually visible, with an
    // occasional brand-red accent icon for life.
    val gray = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f)
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    Canvas(modifier.fillMaxSize()) {
        val stroke = Stroke(
            width = 1.7f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.0f), 0f),
        )
        val w = size.width
        val h = size.height

        data class Placed(
            val icon: BrandIcon, val fx: Float, val fy: Float,
            val scale: Float, val hot: Boolean = false,
        )
        // Denser, larger scatter across the whole height; a few accent (red) ones.
        val layout = listOf(
            Placed(BrandIcon.KEY,        0.10f, 0.06f, 2.0f),
            Placed(BrandIcon.CHECK,      0.83f, 0.05f, 1.8f, hot = true),
            Placed(BrandIcon.RING,       0.52f, 0.12f, 1.3f),
            Placed(BrandIcon.LOCK,       0.90f, 0.20f, 2.1f),
            Placed(BrandIcon.USER,       0.13f, 0.24f, 1.9f),
            Placed(BrandIcon.SHIELD_CHECK,0.70f, 0.30f, 1.6f),
            Placed(BrandIcon.RING,       0.30f, 0.34f, 1.1f),
            Placed(BrandIcon.LOCK_DOTS,  0.86f, 0.44f, 2.0f, hot = true),
            Placed(BrandIcon.CHECK,      0.09f, 0.46f, 1.5f),
            Placed(BrandIcon.KEY,        0.55f, 0.52f, 1.7f),
            Placed(BrandIcon.USER,       0.84f, 0.62f, 1.8f),
            Placed(BrandIcon.RING,       0.18f, 0.64f, 1.2f),
            Placed(BrandIcon.LOCK,       0.40f, 0.70f, 1.6f),
            Placed(BrandIcon.SHIELD_CHECK,0.80f, 0.78f, 1.9f),
            Placed(BrandIcon.CHECK,      0.12f, 0.80f, 1.4f, hot = true),
            Placed(BrandIcon.KEY,        0.63f, 0.86f, 1.8f),
            Placed(BrandIcon.RING,       0.90f, 0.90f, 1.1f),
            Placed(BrandIcon.LOCK_DOTS,  0.28f, 0.92f, 1.7f),
            Placed(BrandIcon.USER,       0.50f, 0.97f, 1.5f),
        )
        for (p in layout) {
            val c = if (p.hot) accent else gray
            val k = (w / 48f) * 0.16f * p.scale
            val cx = w * p.fx - 24f * k
            val cy = h * p.fy - 24f * k
            translate(cx, cy) {
                when (p.icon) {
                    BrandIcon.KEY -> bgKey(c, stroke, k)
                    BrandIcon.LOCK -> bgLock(c, stroke, k)
                    BrandIcon.LOCK_DOTS -> bgLock(c, stroke, k)
                    BrandIcon.USER -> bgUser(c, stroke, k)
                    BrandIcon.CHECK -> bgCheck(c, stroke, k)
                    BrandIcon.RING -> bgRing(c, stroke, k)
                    BrandIcon.SHIELD_CHECK -> bgShield(c, stroke, k)
                }
            }
        }
    }
}

private fun DrawScope.bgKey(c: Color, s: Stroke, k: Float) {
    drawCircle(c, 8f * k, Offset(24f * k, 15f * k), style = s)
    drawPath(Path().apply {
        moveTo(24f * k, 23f * k); lineTo(24f * k, 40f * k)
        moveTo(24f * k, 31f * k); lineTo(30f * k, 31f * k)
    }, c, style = s)
}

private fun DrawScope.bgLock(c: Color, s: Stroke, k: Float) {
    drawPath(Path().apply {
        moveTo(17f * k, 22f * k); lineTo(17f * k, 17f * k)
        cubicTo(17f * k, 10f * k, 31f * k, 10f * k, 31f * k, 17f * k)
        lineTo(31f * k, 22f * k)
    }, c, style = s)
    drawPath(Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                Rect(Offset(14f * k, 22f * k), Size(20f * k, 16f * k)),
                androidx.compose.ui.geometry.CornerRadius(3f * k, 3f * k),
            )
        )
    }, c, style = s)
}

private fun DrawScope.bgUser(c: Color, s: Stroke, k: Float) {
    drawCircle(c, 11f * k, Offset(24f * k, 24f * k), style = s)
    drawCircle(c, 3.6f * k, Offset(24f * k, 20f * k), style = s)
    drawPath(Path().apply {
        moveTo(17f * k, 31f * k)
        cubicTo(18.5f * k, 26f * k, 29.5f * k, 26f * k, 31f * k, 31f * k)
    }, c, style = s)
}

private fun DrawScope.bgCheck(c: Color, s: Stroke, k: Float) {
    drawCircle(c, 11f * k, Offset(24f * k, 24f * k), style = s)
    drawPath(Path().apply {
        moveTo(18.5f * k, 24.5f * k); lineTo(22.5f * k, 28.5f * k); lineTo(30f * k, 20f * k)
    }, c, style = s)
}

private fun DrawScope.bgRing(c: Color, s: Stroke, k: Float) {
    drawCircle(c, 11f * k, Offset(24f * k, 24f * k), style = s)
    drawCircle(c, 4.5f * k, Offset(24f * k, 24f * k), style = s)
}

private fun DrawScope.bgShield(c: Color, s: Stroke, k: Float) {
    drawPath(Path().apply {
        moveTo(24f * k, 8f * k)
        lineTo(38f * k, 13f * k)
        lineTo(38f * k, 24f * k)
        cubicTo(38f * k, 33f * k, 31f * k, 38f * k, 24f * k, 41f * k)
        cubicTo(17f * k, 38f * k, 10f * k, 33f * k, 10f * k, 24f * k)
        lineTo(10f * k, 13f * k)
        close()
    }, c, style = s)
    drawPath(Path().apply {
        moveTo(18.5f * k, 24f * k); lineTo(22.5f * k, 28f * k); lineTo(30f * k, 19.5f * k)
    }, c, style = s)
}
