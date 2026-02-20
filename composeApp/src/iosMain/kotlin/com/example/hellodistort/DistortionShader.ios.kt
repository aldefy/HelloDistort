package com.example.hellodistort

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// SkSL shader — nearly identical to AGSL (both are based on Skia's shading language).
// This runs on iOS via Skia, which Compose Multiplatform uses for rendering on non-Android.
private const val DISTORTION_SKSL = """
    uniform shader content;
    uniform float2 touchPoint;
    uniform float2 velocity;
    uniform float2 resolution;

    half4 main(float2 p) {
        float dist = length(touchPoint - p);
        float falloff = pow(clamp(1.0 - dist / 190.0, 0.0, 1.0), 2.0);
        float2 m = -velocity * falloff * 1.5;

        half3 c = half3(0.0, 0.0, 0.0);
        for (float i = 0.0; i < 10.0; i += 1.0) {
            float s = 0.175 + 0.005 * i;
            c += half3(
                content.eval(p + s * m).r,
                content.eval(p + (s + 0.06) * m).g,
                content.eval(p + (s + 0.12) * m).b
            );
        }
        return half4(c / 10.0, 1.0);
    }
"""

actual fun createDistortionEffect(): DistortionEffect = SkiaDistortionEffect()

private class SkiaDistortionEffect : DistortionEffect {
    private val effect = RuntimeEffect.makeForShader(DISTORTION_SKSL)

    override fun createModifier(
        touchX: Float,
        touchY: Float,
        velocityX: Float,
        velocityY: Float,
        width: Float,
        height: Float,
    ) = Modifier.graphicsLayer {
        val builder = RuntimeShaderBuilder(effect)
        builder.uniform("touchPoint", touchX, touchY)
        builder.uniform("velocity", velocityX, velocityY)
        builder.uniform("resolution", width, height)

        val imageFilter = ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = builder,
            shaderName = "content",
            input = null
        )
        renderEffect = imageFilter.asComposeRenderEffect()
    }
}
