package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueBright
import com.example.ui.theme.SkyBlueSecondary

/**
 * Original RM Messenger brand emblem: a rounded geometric chat bubble housing a crisp
 * interlocking "RM" lettermark in Navy Blue, Sky Blue, and White.
 */
@Composable
fun RmMessengerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    animatedGlow: Boolean = false,
    contentDescriptionText: String = "RM Messenger logo",
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            if (animatedGlow) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SkyBlueBright.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = (w * 0.56f) * pulseScale,
                    ),
                    radius = (w * 0.56f) * pulseScale,
                )
            }

            // Outer Navy badge circle
            val badgePadding = w * 0.06f
            val bubbleLeft = badgePadding
            val bubbleTop = h * 0.08f
            val bubbleWidth = w - badgePadding * 2f
            val bubbleHeight = h * 0.78f
            val corner = bubbleWidth * 0.34f

            // Chat bubble body with Navy -> Sky gradient
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(NavyPrimary, NavyDeep),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(corner, corner),
            )

            // Sky Blue inner accent border
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(SkyBlueBright, SkyBlueSecondary),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = w * 0.045f),
            )

            // Chat bubble pointer tail (bottom-right)
            val tailPath = Path().apply {
                moveTo(w * 0.66f, bubbleTop + bubbleHeight - w * 0.02f)
                lineTo(w * 0.87f, h * 0.94f)
                lineTo(w * 0.83f, bubbleTop + bubbleHeight * 0.74f)
                close()
            }
            drawPath(path = tailPath, color = SkyBlueSecondary)

            // Crisp Stylized "R" + "M" Monogram
            val strokeWidth = w * 0.085f
            val topY = h * 0.32f
            val bottomY = h * 0.62f
            val midY = h * 0.47f

            // Letter R (left side)
            val rX = w * 0.27f
            val rRightX = w * 0.44f
            val rPath = Path().apply {
                moveTo(rX, bottomY)
                lineTo(rX, topY)
                lineTo(rRightX - w * 0.03f, topY)
                cubicTo(
                    rRightX + w * 0.03f, topY,
                    rRightX + w * 0.03f, midY,
                    rRightX - w * 0.03f, midY,
                )
                lineTo(rX, midY)
                moveTo(rX + w * 0.08f, midY)
                lineTo(rRightX + w * 0.02f, bottomY)
            }
            drawPath(
                path = rPath,
                color = PureWhite,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Letter M (right side, Sky Blue highlight)
            val mLeftX = w * 0.52f
            val mMidX = w * 0.63f
            val mRightX = w * 0.74f
            val mPath = Path().apply {
                moveTo(mLeftX, bottomY)
                lineTo(mLeftX, topY)
                lineTo(mMidX, midY + h * 0.04f)
                lineTo(mRightX, topY)
                lineTo(mRightX, bottomY)
            }
            drawPath(
                path = mPath,
                color = SkyBlueBright,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
