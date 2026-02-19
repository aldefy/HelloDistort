package com.example.hellodistort

import androidx.compose.ui.Modifier

actual fun createDistortionEffect(): DistortionEffect = NoOpDistortionEffect()

private class NoOpDistortionEffect : DistortionEffect {
    override fun createModifier(
        touchX: Float,
        touchY: Float,
        velocityX: Float,
        velocityY: Float,
        width: Float,
        height: Float,
    ): Modifier = Modifier
}
