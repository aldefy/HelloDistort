package com.example.hellodistort

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val distortionEffect = remember { createDistortionEffect() }
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    var smoothedVelocity by remember { mutableStateOf(Offset.Zero) }
    var width by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(1f) }

    val shaderModifier = distortionEffect.createModifier(
        touchX = touchPoint.x,
        touchY = touchPoint.y,
        velocityX = smoothedVelocity.x,
        velocityY = smoothedVelocity.y,
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
                    smoothedVelocity = Offset.Zero
                    var prevPosition = down.position
                    var prevTime = down.uptimeMillis
                    down.consume()

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val currentPos = change.position
                        val currentTime = change.uptimeMillis
                        val dt = (currentTime - prevTime).coerceAtLeast(1)

                        // SwiftUI's `predictedEndLocation - location` gives a momentum-like
                        // delta (~200-500px for moderate drags). It's roughly velocity * 0.3s.
                        // We compute px/ms then scale by ~250ms to approximate that range.
                        val rawVelocity = Offset(
                            (currentPos.x - prevPosition.x) / dt * 250f,
                            (currentPos.y - prevPosition.y) / dt * 250f,
                        )

                        // Exponential smoothing (matching SwiftUI DragGesture behavior)
                        val smoothing = 0.4f
                        smoothedVelocity = Offset(
                            smoothedVelocity.x * (1f - smoothing) + rawVelocity.x * smoothing,
                            smoothedVelocity.y * (1f - smoothing) + rawVelocity.y * smoothing,
                        )

                        touchPoint = currentPos
                        prevPosition = currentPos
                        prevTime = currentTime
                        event.changes.forEach { it.consume() }
                    } while (event.type != PointerEventType.Release &&
                        event.changes.any { it.pressed })

                    // On release, zero out velocity so effect disappears
                    smoothedVelocity = Offset.Zero
                }
            }
    ) {
        Text(
            text = buildString {
                repeat(120) { append("Compose! ") }
            },
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            lineHeight = 46.sp,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp)
                .then(shaderModifier),
        )
    }
}
