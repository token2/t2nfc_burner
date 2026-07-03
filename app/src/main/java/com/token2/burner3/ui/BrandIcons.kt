package com.token2.burner3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The dotted security-icon motifs from the Token2 hardware packaging, redrawn as
 * crisp vector geometry with a real dashed stroke. These give the app visual
 * continuity with the physical product. Each is drawn in a 48x48 space and
 * scaled to fit; the dotted look comes from a round-capped dashed [PathEffect],
 * matching the printed artwork rather than approximating it with a solid line.
 */
enum class BrandIcon { KEY, LOCK, LOCK_DOTS, USER, CHECK, RING, SHIELD_CHECK }

@Composable
fun DottedBrandIcon(
    icon: BrandIcon,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Float = 2f,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val k = s / 48f  // scale from the 48-unit design space
        val stroke = Stroke(
            width = strokeWidth * k,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(0.1f * k, 3.2f * k), 0f
            ),
        )
        when (icon) {
            BrandIcon.KEY -> drawKey(color, stroke, k)
            BrandIcon.LOCK -> drawLock(color, stroke, k)
            BrandIcon.LOCK_DOTS -> drawLockDots(color, stroke, k)
            BrandIcon.USER -> drawUser(color, stroke, k)
            BrandIcon.CHECK -> drawCheck(color, stroke, k)
            BrandIcon.RING -> drawRing(color, stroke, k)
            BrandIcon.SHIELD_CHECK -> drawShieldCheck(color, stroke, k)
        }
    }
}

private fun DrawScope.drawKey(color: Color, stroke: Stroke, k: Float) {
    // round bow near the top, shaft with two teeth going down
    drawCircle(color, radius = 8f * k, center = Offset(24f * k, 15f * k), style = stroke)
    val p = Path().apply {
        moveTo(24f * k, 23f * k); lineTo(24f * k, 40f * k)
        moveTo(24f * k, 31f * k); lineTo(30f * k, 31f * k)
        moveTo(24f * k, 36f * k); lineTo(28f * k, 36f * k)
    }
    drawPath(p, color, style = stroke)
}

private fun DrawScope.drawLock(color: Color, stroke: Stroke, k: Float) {
    // shackle
    val sh = Path().apply {
        moveTo(17f * k, 22f * k)
        lineTo(17f * k, 17f * k)
        cubicTo(17f * k, 10f * k, 31f * k, 10f * k, 31f * k, 17f * k)
        lineTo(31f * k, 22f * k)
    }
    drawPath(sh, color, style = stroke)
    // body
    drawRoundRectPath(color, stroke, 14f * k, 22f * k, 20f * k, 16f * k, 3f * k)
    // keyhole
    drawCircle(color, radius = 2.2f * k, center = Offset(24f * k, 30f * k), style = stroke)
}

private fun DrawScope.drawLockDots(color: Color, stroke: Stroke, k: Float) {
    // wider "message" lock with three dots — matches the packaging's dotted lock
    val sh = Path().apply {
        moveTo(16f * k, 24f * k)
        lineTo(16f * k, 20f * k)
        cubicTo(16f * k, 14f * k, 26f * k, 14f * k, 26f * k, 20f * k)
    }
    drawPath(sh, color, style = stroke)
    drawRoundRectPath(color, stroke, 12f * k, 24f * k, 24f * k, 13f * k, 3f * k)
    // three dots (solid, so they read as dots not dashes)
    for (i in 0..2) {
        drawCircle(color, radius = 1.3f * k, center = Offset((19f + i * 5f) * k, 30.5f * k))
    }
}

private fun DrawScope.drawUser(color: Color, stroke: Stroke, k: Float) {
    drawCircle(color, radius = 11f * k, center = Offset(24f * k, 24f * k), style = stroke)
    // head
    drawCircle(color, radius = 3.6f * k, center = Offset(24f * k, 20f * k), style = stroke)
    // shoulders arc
    val sh = Path().apply {
        moveTo(17f * k, 31f * k)
        cubicTo(18.5f * k, 26f * k, 29.5f * k, 26f * k, 31f * k, 31f * k)
    }
    drawPath(sh, color, style = stroke)
}

private fun DrawScope.drawCheck(color: Color, stroke: Stroke, k: Float) {
    drawCircle(color, radius = 11f * k, center = Offset(24f * k, 24f * k), style = stroke)
    val p = Path().apply {
        moveTo(18.5f * k, 24.5f * k)
        lineTo(22.5f * k, 28.5f * k)
        lineTo(30f * k, 20f * k)
    }
    drawPath(p, color, style = stroke)
}

private fun DrawScope.drawRing(color: Color, stroke: Stroke, k: Float) {
    drawCircle(color, radius = 11f * k, center = Offset(24f * k, 24f * k), style = stroke)
    drawCircle(color, radius = 4.5f * k, center = Offset(24f * k, 24f * k), style = stroke)
}

private fun DrawScope.drawShieldCheck(color: Color, stroke: Stroke, k: Float) {
    val shield = Path().apply {
        moveTo(24f * k, 8f * k)
        lineTo(38f * k, 13f * k)
        lineTo(38f * k, 24f * k)
        cubicTo(38f * k, 33f * k, 31f * k, 38f * k, 24f * k, 41f * k)
        cubicTo(17f * k, 38f * k, 10f * k, 33f * k, 10f * k, 24f * k)
        lineTo(10f * k, 13f * k)
        close()
    }
    drawPath(shield, color, style = stroke)
    val chk = Path().apply {
        moveTo(18.5f * k, 24f * k)
        lineTo(22.5f * k, 28f * k)
        lineTo(30f * k, 19.5f * k)
    }
    drawPath(chk, color, style = stroke)
}

private fun DrawScope.drawRoundRectPath(
    color: Color, stroke: Stroke,
    left: Float, top: Float, w: Float, h: Float, r: Float,
) {
    val p = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                Rect(Offset(left, top), Size(w, h)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        )
    }
    drawPath(p, color, style = stroke)
}
