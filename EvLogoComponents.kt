package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Exact vector replica of the uploaded EV monogram logo (`ev` in vibrant #F0146E hot-pink/magenta).
 * Drawn on Canvas so it scales crisply at any size (top bar 20dp, bottom nav 27dp, loader 150dp)
 * with optional glowing pulse effect.
 */
@Composable
fun EvLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = Color(0xFFF0146E),
    glowAlpha: Float = 0f
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.125f

        if (glowAlpha > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.45f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w * 0.62f
                ),
                radius = w * 0.62f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }

        // Draw the 'e' loop tilted slightly (-16 deg) on the left + connected 'v' on the right
        val eCenter = Offset(w * 0.34f, h * 0.54f)
        val eRadius = w * 0.23f

        rotate(degrees = -16f, pivot = eCenter) {
            // Outer arc of 'e' (from 15 deg around to 330 deg)
            drawArc(
                color = color,
                startAngle = 18f,
                sweepAngle = 315f,
                useCenter = false,
                topLeft = Offset(eCenter.x - eRadius, eCenter.y - eRadius),
                size = androidx.compose.ui.geometry.Size(eRadius * 2, eRadius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Crossbar of 'e'
            drawLine(
                color = color,
                startPoint = Offset(eCenter.x - eRadius * 0.92f, eCenter.y),
                endPoint = Offset(eCenter.x + eRadius * 0.98f, eCenter.y),
                strokeWidth = stroke * 0.92f,
                cap = StrokeCap.Butt
            )
        }

        // Draw the sharp 'v' on the right, interlocking with the bottom-right of 'e'
        val vPath = Path().apply {
            moveTo(w * 0.54f, h * 0.49f)
            lineTo(w * 0.67f, h * 0.77f)
            lineTo(w * 0.90f, h * 0.27f)
        }
        drawPath(
            path = vPath,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Miter,
                miter = 6f
            )
        )
    }
}

/**
 * Full-screen `#cms-loader` splash overlay with pulsing EV logo and sliding gradient bar,
 * matching `#cms-loader`, `.loader-brand-img`, and `.loader-goldbar-inner` from the HTML.
 */
@Composable
fun EvLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier.testTag("cms_loader_overlay")
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "evLoader")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoScale"
        )
        val glow by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoGlow"
        )
        val barProgress by infiniteTransition.animateFloat(
            initialValue = -0.45f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "barSlide"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(scale)) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFF0146E).copy(alpha = 0.40f * glow), Color.Transparent)
                                )
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ev_logo),
                        contentDescription = "EV SPORTS",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(150.dp)
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1FFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.36f)
                            .offset {
                                IntOffset((barProgress * 160.dp.toPx()).roundToInt(), 0)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFF0146E),
                                        Color(0xFFFF8FBB),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}
