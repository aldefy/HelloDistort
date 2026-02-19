package com.example.hellodistort

import androidx.compose.ui.Modifier

interface DistortionEffect {
    fun createModifier(
        touchX: Float,
        touchY: Float,
        velocityX: Float,
        velocityY: Float,
        width: Float,
        height: Float,
    ): Modifier
}

expect fun createDistortionEffect(): DistortionEffect
