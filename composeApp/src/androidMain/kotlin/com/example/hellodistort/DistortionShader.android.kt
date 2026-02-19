package com.example.hellodistort

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

private const val DISTORTION_AGSL = """
    uniform shader content;
    uniform float2 touchPoint;
    uniform float2 resolution;
    uniform float intensity;
    uniform float time;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 touch = touchPoint / resolution;

        float2 dir = uv - touch;
        float dist = length(dir);

        // Radius of the effect
        float radius = 0.25;
        float falloff = smoothstep(radius, 0.0, dist) * intensity;

        // Radial displacement — push text outward from touch
        float2 norm = dist > 0.001 ? normalize(dir) : float2(0.0, 0.0);
        float displaceStrength = falloff * 0.08;

        // Add a subtle wave/ripple
        float ripple = sin(dist * 40.0 - time * 6.0) * 0.003 * falloff;

        float2 displaced = uv + norm * (displaceStrength + ripple);

        // Chromatic aberration — offset R, G, B channels differently
        float chromaStrength = falloff * 0.025;
        float2 rUV = displaced + norm * chromaStrength;
        float2 gUV = displaced;
        float2 bUV = displaced - norm * chromaStrength;

        half4 rSample = content.eval(rUV * resolution);
        half4 gSample = content.eval(gUV * resolution);
        half4 bSample = content.eval(bUV * resolution);

        return half4(rSample.r, gSample.g, bSample.b, gSample.a);
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
actual fun createDistortionEffect(): DistortionEffect = AgslDistortionEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslDistortionEffect : DistortionEffect {
    private val shader = RuntimeShader(DISTORTION_AGSL)

    override fun createModifier(
        touchX: Float,
        touchY: Float,
        intensity: Float,
        time: Float,
        width: Float,
        height: Float,
    ) = androidx.compose.ui.Modifier.graphicsLayer {
        shader.setFloatUniform("touchPoint", touchX, touchY)
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("intensity", intensity)
        shader.setFloatUniform("time", time)
        renderEffect = android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
