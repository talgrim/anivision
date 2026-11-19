package com.anivision.app.vision.gl

/**
 * GLSL ES 1.00 sources for the vision pipeline (valid in an ES 3.0 context too). Pipeline order
 * follows `export/matrix.json`: decode sRGB, blur in linear light, matrix, clamp, encode sRGB.
 *
 * Three programs are built from one fragment template:
 * - [Variant.COLOUR]: camera texture to screen, no blur.
 * - [Variant.BLUR_HORIZONTAL]: camera texture to a linear-light float texture.
 * - [Variant.BLUR_VERTICAL]: that texture to screen, with the colour step.
 *
 * The two colour functions and the shadow lift are copied verbatim from
 * `export/animal_vision.glsl`; `VisionShadersTest` fails if the export changes.
 */
internal object VisionShaders {
    enum class Variant(val oesSource: Boolean, val blur: Boolean, val colourOut: Boolean) {
        COLOUR(oesSource = true, blur = false, colourOut = true),
        BLUR_HORIZONTAL(oesSource = true, blur = true, colourOut = false),
        BLUR_VERTICAL(oesSource = false, blur = true, colourOut = true),
    }

    /** Largest blur radius in taps; sigma beyond `MAX_RADIUS / 3` is truncated. */
    const val MAX_RADIUS = 32

    val COLOUR_FUNCTIONS = """
vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

vec3 linearToSrgb(vec3 c) {
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
""".trim()

    /** The optional night-boost shadow lift (artistic, see `LowLight.md`); gamma 1.0 is off. */
    val SHADOW_LIFT_FUNCTIONS = """
const vec3 LUMA = vec3(0.2126729, 0.7151522, 0.072175); // linear-light luminance weights

// Shadow lift (artistic, optional): luminance gamma that keeps colour ratios; gamma 1.0 is off.
// Applied to linear light after the blur and before the matrix.
vec3 shadowLift(vec3 rgb, float gamma) {
    float y = clamp(dot(LUMA, rgb), 1.0e-6, 1.0);
    float peak = max(max(rgb.r, rgb.g), max(rgb.b, 1.0e-6));
    return rgb * min(pow(y, gamma - 1.0), max(1.0 / peak, 1.0));
}
""".trim()

    val VERTEX = """
attribute vec2 aPosition;
varying vec2 vUv;
void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
""".trimStart()

    fun fragment(variant: Variant): String = buildString {
        if (variant.oesSource) appendLine("#extension GL_OES_EGL_image_external : require")
        if (variant.oesSource) appendLine("#define SOURCE_OES")
        if (variant.blur) appendLine("#define BLUR")
        if (variant.colourOut) appendLine("#define COLOUR_OUT")
        appendLine("#define MAX_RADIUS $MAX_RADIUS")
        append(FRAGMENT_BODY)
    }

    private val FRAGMENT_BODY = """
precision highp float;

varying vec2 vUv;

#ifdef SOURCE_OES
uniform samplerExternalOES uTexture;
uniform mat4 uTexMatrix;
#else
precision highp sampler2D; // the default (lowp) would quantise the half-float blur texture
uniform sampler2D uTexture;
#endif
uniform mat3 uColorMatrix;
uniform vec2 uStep;
uniform float uSigma; // blur sigma in pixels at the frame centre
uniform float uPeriphery; // blur grows to sigma * (1 + uPeriphery) in the corners; 0 = uniform blur
uniform vec2 uSize; // output size in pixels
uniform float uShadowGamma; // shadow lift: luminance gamma, 1.0 = off

$COLOUR_FUNCTIONS

$SHADOW_LIFT_FUNCTIONS

// Linear-light colour at output-space uv; clamp-to-edge at the frame border.
vec3 fetchLinear(vec2 uv) {
    uv = clamp(uv, 0.0, 1.0);
#ifdef SOURCE_OES
    // YUV to RGB can overshoot [0, 1]; a negative here makes pow() in srgbToLinear NaN, and the
    // blur then smears that NaN over its whole window as black blocks.
    vec3 camera = texture2D(uTexture, (uTexMatrix * vec4(uv, 0.0, 1.0)).xy).rgb;
    return srgbToLinear(clamp(camera, 0.0, 1.0));
#else
    return texture2D(uTexture, uv).rgb;
#endif
}

#ifdef BLUR
vec3 blurred(vec2 uv) {
    // Peripheral blur: sigma(r) = sigma * (1 + periphery * r^2), r^2 = 0 at the frame centre and
    // 1 in the corners (matrix.json acuity.peripheral_blur). Each pass uses its own pixel's r^2.
    vec2 fromCentre = (vUv - 0.5) * uSize;
    float r2 = dot(fromCentre, fromCentre) / (0.25 * dot(uSize, uSize));
    float sigma = uSigma * (1.0 + uPeriphery * r2);
    float radius = min(ceil(3.0 * sigma), float(MAX_RADIUS));
    float k = -0.5 / (sigma * sigma);
    vec3 sum = fetchLinear(uv);
    float weightSum = 1.0;
    for (int i = 1; i <= MAX_RADIUS; i++) {
        float fi = float(i);
        if (fi > radius) break;
        float w = exp(k * fi * fi);
        vec2 offset = uStep * fi;
        sum += w * (fetchLinear(uv + offset) + fetchLinear(uv - offset));
        weightSum += 2.0 * w;
    }
    return sum / weightSum;
}
#endif

void main() {
#ifdef BLUR
    vec3 linear = blurred(vUv);
#else
    vec3 linear = fetchLinear(vUv);
#endif
#ifdef COLOUR_OUT
    vec3 shown = uColorMatrix * shadowLift(linear, uShadowGamma);
    gl_FragColor = vec4(linearToSrgb(clamp(shown, 0.0, 1.0)), 1.0);
#else
    gl_FragColor = vec4(linear, 1.0);
#endif
}
""".trimStart()
}
