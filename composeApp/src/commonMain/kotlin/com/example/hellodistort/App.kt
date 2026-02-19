package com.example.hellodistort

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val distortionEffect = remember { createDistortionEffect() }
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    var isTouching by remember { mutableStateOf(false) }
    val intensity = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(1f) }

    // Animate time for ripple effect
    LaunchedEffect(Unit) {
        val startTime = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { frameTime ->
                time = (frameTime - startTime) / 1000f
            }
        }
    }

    // Animate intensity on touch/release
    LaunchedEffect(isTouching) {
        if (isTouching) {
            intensity.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                )
            )
        } else {
            intensity.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            )
        }
    }

    val shaderModifier = distortionEffect.createModifier(
        touchX = touchPoint.x,
        touchY = touchPoint.y,
        intensity = intensity.value,
        time = time,
        width = width,
        height = height,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touchPoint = down.position
                    isTouching = true
                    down.consume()

                    do {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position
                        if (pos != null) {
                            touchPoint = pos
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.type != PointerEventType.Release &&
                        event.changes.any { it.pressed })

                    isTouching = false
                }
            }
    ) {
        // The text content that gets distorted
        Text(
            text = buildString {
                repeat(80) { append("Hello, world! ") }
            },
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 34.sp,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 48.dp)
                .then(shaderModifier),
        )
    }
}
