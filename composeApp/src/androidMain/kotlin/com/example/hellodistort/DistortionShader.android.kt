package com.example.hellodistort

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

// =============================================================================
// DEV NOTES: AGSL (Android Graphics Shading Language) Crash Course
// =============================================================================
//
// WHAT IS AGSL?
// AGSL is Android's shader language, introduced in Android 13 (API 33/Tiramisu).
// It's based on SkSL (Skia Shading Language), which itself is inspired by GLSL.
// Think of it as: "GLSL but for Android's 2D rendering pipeline (Skia)."
//
// WHERE DOES IT RUN?
// The shader runs on the GPU. For every single pixel of the view it's attached to,
// the GPU calls your main() function. On a 1080×2400 phone, that's 2,592,000
// calls PER FRAME at 60fps. GPUs are massively parallel so this is fast.
//
// HOW IS IT DIFFERENT FROM GLSL/METAL?
// - AGSL uses `content.eval(coord)` to sample the source view (like texture2D in GLSL)
// - Metal uses `layer.sample(coord)` — same idea, different syntax
// - AGSL uses `half4` (16-bit float) and `float` (32-bit) — same as Metal
// - AGSL doesn't have texture samplers — you get a `shader` uniform instead
// - The entry point is `half4 main(float2 fragCoord)` — returns the pixel color
//
// KEY TYPES:
//   float    — 32-bit floating point number (e.g., 3.14)
//   float2   — 2D vector of floats: (x, y). Used for positions, UVs, velocities
//   float3   — 3D vector: (x, y, z) or (r, g, b)
//   float4   — 4D vector: (x, y, z, w) or (r, g, b, a)
//   half     — 16-bit float (less precise but faster)
//   half3    — 3D vector of half-precision floats
//   half4    — 4D vector of half-precision floats (this is what we return as pixel color)
//   shader   — A special type representing the source content we're distorting
//
// KEY FUNCTIONS USED IN THIS SHADER:
//   length(v)       — Returns the magnitude of a vector: sqrt(x² + y²)
//   clamp(x, lo, hi) — Clamps x between lo and hi: max(lo, min(hi, x))
//   pow(base, exp)  — base raised to the power of exp: base^exp
//   content.eval(p) — Samples the source view at pixel coordinate p, returns half4 color
//
// =============================================================================

// =============================================================================
// THE ORIGINAL METAL SHADER (by Daniel Kuntz @dankuntz)
// This is what we're porting. It's a SwiftUI [[stitchable]] shader.
// =============================================================================
//
//   [[stitchable]]  ← This attribute tells SwiftUI this can be used as a visual effect
//   half4 w(         ← Returns a color (RGBA) for each pixel
//       float2 p,    ← The current pixel position in the view (in points)
//       SwiftUI::Layer a,  ← The source layer (the rendered view content)
//       float2 l,    ← The drag location — where the finger is (in points)
//       float2 v     ← The drag velocity — how fast the finger is moving (points/frame)
//   ) {
//       // Step 1: Compute the motion vector
//       float2 m = -v * pow(clamp(1 - length(l - p) / 190, 0., 1.), 2) * 1.5;
//
//       // Step 2: Multi-sample with chromatic aberration
//       half3 c = 0;
//       for (float i = 0; i < 10; i++) {
//           float s = .175 + .005 * i;
//           c += half3(
//               a.sample(p + s * m).r,            ← Red channel
//               a.sample(p + (s + .025) * m).g,   ← Green channel (slightly further)
//               a.sample(p + (s + .05) * m).b      ← Blue channel (even further)
//           );
//       }
//       return half4(c / 10, 1);  ← Average 10 samples, full alpha
//   }
//
// =============================================================================

// =============================================================================
// THE AGSL SHADER — LINE BY LINE
// =============================================================================
//
// This string is compiled at runtime by Android's RuntimeShader API.
// It gets passed to the GPU as a fragment shader program.
//
private const val DISTORTION_AGSL = """

    // =========================================================================
    // UNIFORMS — values passed in from Kotlin every frame
    // =========================================================================
    // "uniform" means this value is set from CPU code (Kotlin side) and is
    // constant for ALL pixels in a single frame. It changes between frames
    // as the user drags their finger.
    //
    // Think of uniforms as the "inputs" or "parameters" to your shader.
    // Every pixel sees the same uniform values — they're "uniform" across pixels.

    // The source content being distorted.
    // In AGSL, `shader` is a special type that represents the rendered view.
    // We sample it with `content.eval(float2)` to get the color at any position.
    // This is equivalent to `SwiftUI::Layer` in Metal or `sampler2D` in GLSL.
    // The name "content" must match the string passed to createRuntimeShaderEffect().
    uniform shader content;

    // The finger's current position in PIXEL coordinates.
    // (0,0) is top-left of the view. Updates every frame as user drags.
    // In the Metal version, this is the `l` parameter (location).
    uniform float2 touchPoint;

    // The drag velocity in PIXELS per frame (exponentially smoothed on Kotlin side).
    // This is the key input that drives the entire effect.
    // Positive X = moving right, Positive Y = moving down.
    // When the user isn't dragging, this is (0,0) and the shader does nothing.
    // In the Metal version, this is the `v` parameter (velocity).
    uniform float2 velocity;

    // The view dimensions in pixels. Not used in the core math but available
    // if we ever need UV coordinates (fragCoord / resolution).
    uniform float2 resolution;


    // =========================================================================
    // main() — called once PER PIXEL, PER FRAME
    // =========================================================================
    // `p` is the position of THIS pixel in the view (in pixel coordinates).
    // We must return a half4 (r, g, b, a) — the color to draw at this pixel.
    //
    // On a 1080×2400 screen, this function runs ~2.6 million times per frame.
    // The GPU runs thousands of these in parallel so it's still fast.

    half4 main(float2 p) {

        // =====================================================================
        // STEP 1: How far is this pixel from the finger?
        // =====================================================================
        // `touchPoint - p` gives us a vector from this pixel TO the finger.
        // `length()` gives us the scalar distance (magnitude of that vector).
        //
        // Example: if finger is at (500, 800) and this pixel is at (500, 700),
        // then dist = length((500,800) - (500,700)) = length((0,100)) = 100 pixels.

        float dist = length(touchPoint - p);


        // =====================================================================
        // STEP 2: Compute the falloff — how much does this pixel get affected?
        // =====================================================================
        // We want pixels NEAR the finger to be heavily distorted, and pixels
        // FAR from the finger to be untouched. The magic number 190 is the
        // radius in pixels — beyond 190px from the finger, no effect at all.
        //
        // Let's trace through the math step by step:
        //
        //   1 - dist / 190
        //     At dist=0   (finger):  1 - 0/190     = 1.0  (full effect)
        //     At dist=95  (midway):  1 - 95/190    = 0.5  (half effect)
        //     At dist=190 (edge):    1 - 190/190   = 0.0  (no effect)
        //     At dist=300 (outside): 1 - 300/190   = -0.58 (negative!)
        //
        //   clamp(..., 0.0, 1.0)
        //     Clamps the result to [0, 1]. So anything beyond 190px becomes 0.
        //     This is our linear falloff: 1 at center, 0 at edge.
        //
        //   pow(..., 2.0)
        //     Squares the clamped value. This makes the falloff QUADRATIC
        //     instead of linear. Why? Because a linear falloff looks unnatural.
        //     Squaring it means the effect drops off slowly near the finger
        //     and rapidly near the edge:
        //       At dist=0:   pow(1.0, 2) = 1.0
        //       At dist=47:  pow(0.75, 2) = 0.5625  (still strong at 25% of radius)
        //       At dist=95:  pow(0.5, 2) = 0.25     (weak at 50% of radius)
        //       At dist=142: pow(0.25, 2) = 0.0625  (barely visible at 75%)
        //       At dist=190: pow(0.0, 2) = 0.0      (gone)

        float falloff = pow(clamp(1.0 - dist / 190.0, 0.0, 1.0), 2.0);


        // =====================================================================
        // STEP 3: Compute the MOTION VECTOR
        // =====================================================================
        // This is the heart of the effect. The motion vector `m` determines:
        //   - DIRECTION of the smear (opposite to drag velocity)
        //   - MAGNITUDE of the smear (proportional to speed × falloff)
        //
        // Breaking it down:
        //   -velocity       → Negate the velocity. If finger moves RIGHT (+x),
        //                     the smear goes LEFT (-x). This creates a "trailing"
        //                     effect, like the content is being dragged behind.
        //
        //   * falloff       → Scale by distance falloff. Pixels near the finger
        //                     get the full motion vector. Pixels far away get zero.
        //                     This localizes the effect around the touch point.
        //
        //   * 1.5           → Amplification factor. Makes the smear 50% stronger
        //                     than the raw velocity. This is a tuning constant —
        //                     try 1.0 for subtle, 2.0 for dramatic, 3.0 for crazy.
        //
        // When velocity is (0, 0) — i.e., finger is still or not touching —
        // then m = (0, 0) and the shader becomes a no-op (returns original pixel).

        float2 m = -velocity * falloff * 1.5;


        // =====================================================================
        // STEP 4: Multi-tap sampling with CHROMATIC ABERRATION
        // =====================================================================
        // Instead of sampling the content once, we sample it 10 TIMES at
        // slightly different positions along the motion vector, then average.
        // This creates DIRECTIONAL MOTION BLUR — like a camera capturing
        // motion during a long exposure.
        //
        // On top of that, we sample R, G, B channels at DIFFERENT offsets.
        // This creates CHROMATIC ABERRATION — the rainbow fringing you see
        // on the edges of the distorted text. It mimics how a real lens
        // refracts different wavelengths of light at different angles.
        //
        // Initialize the color accumulator to black.
        // We'll add 10 color samples to this, then divide by 10 at the end.

        half3 c = half3(0.0, 0.0, 0.0);

        // Loop 10 times (i = 0, 1, 2, ... 9)
        for (float i = 0.0; i < 10.0; i += 1.0) {

            // The spread factor `s` controls how far along the motion vector
            // we sample. It ranges from 0.175 (i=0) to 0.220 (i=9).
            //
            // Why start at 0.175 and not 0? Starting at ~17.5% along the
            // motion vector (instead of at the pixel itself) creates a gap
            // between the original content and the smear. This makes the
            // distortion look more like a motion trail than a simple blur.
            //
            // The 0.005 step means each sample is 0.5% further along the vector.
            // Total spread: 0.220 - 0.175 = 0.045 = 4.5% of the motion vector.
            // This is a NARROW band — the motion blur is tight, not spread wide.

            float s = 0.175 + 0.005 * i;

            // Now the chromatic aberration: we sample R, G, B at different offsets.
            //
            // RED:   sampled at position  p + s * m
            //        This is the "closest" sample to the original position.
            //
            // GREEN: sampled at position  p + (s + 0.06) * m
            //        This is 6% further along the motion vector than red.
            //        (Original was 0.025 — bumped for more vivid color split)
            //
            // BLUE:  sampled at position  p + (s + 0.12) * m
            //        This is 12% further along the motion vector than red.
            //        (Original was 0.05 — bumped for more vivid color split)
            //
            // The wider the gap between R/G/B offsets, the more the colors
            // separate → stronger rainbow fringing. Original Metal values
            // were 0.025/0.05. We use 0.06/0.12 for ~2.4x more vivid chroma.
            //
            // content.eval(coord) samples the source view at the given pixel
            // coordinate. It's like texture2D() in GLSL or layer.sample() in Metal.
            // The .r / .g / .b swizzles extract individual color channels.

            c += half3(
                content.eval(p + s * m).r,
                content.eval(p + (s + 0.06) * m).g,
                content.eval(p + (s + 0.12) * m).b
            );

            // After this loop iteration, we've accumulated one set of
            // RGB values from three slightly different positions.
            // By the end of 10 iterations, `c` contains the sum of
            // 10 red samples, 10 green samples, and 10 blue samples.
        }

        // =====================================================================
        // STEP 5: Average and return
        // =====================================================================
        // Divide by 10 to get the average of all samples.
        // This averaging is what makes it a blur — if all 10 samples hit the
        // same color, you get that color back. If they span across an edge
        // (e.g., white text → black background), you get a gradient.
        //
        // Alpha is hardcoded to 1.0 (fully opaque). The original content's
        // alpha is ignored — we always draw a solid pixel.
        //
        // When velocity is (0,0): m=(0,0), so ALL samples hit position p,
        // meaning c = 10 * original_color, and c/10 = original_color.
        // The shader becomes a perfect no-op. Zero cost when not touching.

        return half4(c / 10.0, 1.0);
    }
"""

// =============================================================================
// KOTLIN SIDE: Wiring the shader to Compose
// =============================================================================

// The `actual` keyword means this is the Android-specific implementation of
// the `expect fun createDistortionEffect()` declared in commonMain.
// On iOS, a different `actual` implementation is provided (currently a no-op).
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
actual fun createDistortionEffect(): DistortionEffect = AgslDistortionEffect()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslDistortionEffect : DistortionEffect {

    // RuntimeShader compiles the AGSL source code into a GPU program.
    // This happens once when the object is created — it's cached.
    // The compiled shader is then reused every frame with different uniforms.
    private val shader = RuntimeShader(DISTORTION_AGSL)

    override fun createModifier(
        touchX: Float,
        touchY: Float,
        velocityX: Float,
        velocityY: Float,
        width: Float,
        height: Float,
    ) = androidx.compose.ui.Modifier.graphicsLayer {
        // graphicsLayer {} gives us access to the RenderEffect API.
        // Everything inside this lambda runs every frame during composition.

        // setFloatUniform() pushes values from Kotlin → GPU.
        // These must match the `uniform` declarations in the AGSL string above.
        // The names are string-matched — typo = silent failure (no error, no effect).
        shader.setFloatUniform("touchPoint", touchX, touchY)
        shader.setFloatUniform("velocity", velocityX, velocityY)
        shader.setFloatUniform("resolution", width, height)

        // createRuntimeShaderEffect() wraps our shader as a RenderEffect.
        // The second parameter "content" tells Android which uniform in our shader
        // represents the source content. Android will bind the rendered view
        // pixels to this uniform automatically.
        //
        // .asComposeRenderEffect() converts Android's RenderEffect to Compose's
        // GraphicsLayerScope.renderEffect type.
        renderEffect = android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
