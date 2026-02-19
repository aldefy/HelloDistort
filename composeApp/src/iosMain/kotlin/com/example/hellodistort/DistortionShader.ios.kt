package com.example.hellodistort

import androidx.compose.ui.Modifier

// iOS: shader effects via RenderEffect are not available in Compose for iOS yet.
// Provide a no-op implementation so the project compiles on iOS.
actual fun createDistortionEffect(): DistortionEffect = NoOpDistortionEffect()

private class NoOpDistortionEffect : DistortionEffect {
    override fun createModifier(
        touchX: Float,
        touchY: Float,
        intensity: Float,
        time: Float,
        width: Float,
        height: Float,
    ): Modifier = Modifier
}
