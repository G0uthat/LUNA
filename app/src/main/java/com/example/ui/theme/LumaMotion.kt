package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * Refresh-rate-adaptive motion system for Luma.
 *
 * Calibrates spring physics, tween durations, VSync frame alignment, deceleration curves,
 * and Lottie playback cadence to the display's active/configured refresh rate (60..240 Hz).
 *
 * - At 60 Hz (16.67 ms/frame): gently elongates short transitions, softens stiffness, and raises
 *   damping so low-frame-count motion never feels choppy or stepped.
 * - At 90 Hz (11.11 ms/frame): balanced reference feel.
 * - At 120–240 Hz (8.33–4.17 ms/frame): eliminates harsh snaps and spring wobble via smooth
 *   long-tail deceleration curves, elevated critical damping, and exact VSync frame quantization.
 */
@Immutable
data class LumaMotionProfile(
    val effectiveHz: Float,
    val frameMs: Float,
    val durationScale: Float,
    val stiffnessScale: Float,
    val dampingFloor: Float,
    val dampingBoost: Float,
    val lottieSpeed: Float,
    val standardEasing: CubicBezierEasing,
    val emphasizedEasing: CubicBezierEasing,
    val exitEasing: CubicBezierEasing
) {
    /**
     * Scales a base duration (calibrated for 90 Hz reference) and snaps it to an integer
     * multiple of the panel's VSync frame period so animations settle cleanly on a real frame.
     */
    fun duration(baseMs: Int, minFrames: Int = 3): Int {
        if (baseMs <= 0) return 0
        val scaled = baseMs * durationScale
        val frames = (scaled / frameMs).roundToInt().coerceAtLeast(minFrames)
        return (frames * frameMs).roundToInt().coerceAtLeast(32)
    }

    /**
     * Scales a long breathing/ambient cycle (e.g. spotlight pulse, halo shimmer) smoothly.
     */
    fun ambientDuration(baseMs: Int): Int {
        val factor = when {
            effectiveHz <= 65f -> 1.08f
            effectiveHz <= 95f -> 1.00f
            effectiveHz <= 125f -> 0.96f
            else -> 0.92f
        }
        val frames = ((baseMs * factor) / frameMs).roundToInt().coerceAtLeast(12)
        return (frames * frameMs).roundToInt()
    }

    fun <T> tweenSpec(
        baseMs: Int,
        delayMs: Int = 0,
        easing: Easing = standardEasing
    ): TweenSpec<T> = tween(
        durationMillis = duration(baseMs),
        delayMillis = if (delayMs > 0) duration(delayMs, minFrames = 1) else 0,
        easing = easing
    )

    fun <T> emphasizedTween(
        baseMs: Int,
        delayMs: Int = 0
    ): TweenSpec<T> = tweenSpec(
        baseMs = baseMs,
        delayMs = delayMs,
        easing = emphasizedEasing
    )

    fun <T> exitTween(
        baseMs: Int,
        delayMs: Int = 0
    ): TweenSpec<T> = tweenSpec(
        baseMs = baseMs,
        delayMs = delayMs,
        easing = exitEasing
    )

    /**
     * Calibrated spring that prevents stiff/choppy snaps on both 60 Hz and 120–240 Hz panels.
     */
    fun <T> springSpec(
        baseDamping: Float = 0.86f,
        baseStiffness: Float = 360f,
        visibilityThreshold: T? = null
    ): SpringSpec<T> {
        val tunedDamping = (baseDamping + dampingBoost)
            .coerceAtLeast(dampingFloor)
            .coerceAtMost(0.98f)
        val tunedStiffness = (baseStiffness * stiffnessScale)
            .coerceIn(140f, 680f)
        return spring(
            dampingRatio = tunedDamping,
            stiffness = tunedStiffness,
            visibilityThreshold = visibilityThreshold
        )
    }

    /** Crisp tactile spring for button/card press scales and pill toggles. */
    fun <T> tactileSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.84f,
            baseStiffness = 460f,
            visibilityThreshold = visibilityThreshold
        )

    /** Smooth responsive spring for dock drawers, menus, and popovers. */
    fun <T> modalSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.88f,
            baseStiffness = 340f,
            visibilityThreshold = visibilityThreshold
        )

    /** Silky critically-damped glide spring for spotlights, floating banners, and bounds morphing. */
    fun <T> glideSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.90f,
            baseStiffness = 290f,
            visibilityThreshold = visibilityThreshold
        )

    /** Expressive but controlled spring for bookmark ribbon drops & celebratory micro-feedback. */
    fun <T> expressiveSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        springSpec(
            baseDamping = 0.78f,
            baseStiffness = 360f,
            visibilityThreshold = visibilityThreshold
        )
}

fun motionProfileFor(refreshRateHz: Float): LumaMotionProfile {
    val hz = refreshRateHz.coerceIn(60f, 240f)
    val frameMs = 1000f / hz

    val durationScale = interpolateAnchors(
        hz,
        60f to 1.12f,
        90f to 1.00f,
        120f to 0.94f,
        144f to 0.90f,
        240f to 0.86f
    )
    val stiffnessScale = interpolateAnchors(
        hz,
        60f to 0.84f,
        90f to 1.00f,
        120f to 1.08f,
        144f to 1.12f,
        240f to 1.16f
    )
    // Both low Hz (prevents choppy bounce) and very high Hz (prevents harsh jittery snap)
    // benefit from clean damping; 90 Hz stays as the crisp baseline.
    val dampingBoost = interpolateAnchors(
        hz,
        60f to 0.06f,
        90f to 0.02f,
        120f to 0.03f,
        144f to 0.04f,
        240f to 0.05f
    )
    val dampingFloor = interpolateAnchors(
        hz,
        60f to 0.84f,
        90f to 0.78f,
        120f to 0.82f,
        144f to 0.84f,
        240f to 0.86f
    )
    val lottieSpeed = interpolateAnchors(
        hz,
        60f to 0.94f,
        90f to 1.00f,
        120f to 1.02f,
        144f to 1.04f,
        240f to 1.06f
    )

    // Smooth deceleration curves tailored to frame density
    val stdX1 = interpolateAnchors(hz, 60f to 0.22f, 90f to 0.18f, 120f to 0.16f, 144f to 0.14f, 240f to 0.12f)
    val stdX2 = interpolateAnchors(hz, 60f to 0.36f, 90f to 0.32f, 120f to 0.30f, 144f to 0.28f, 240f to 0.25f)

    val empX1 = interpolateAnchors(hz, 60f to 0.18f, 90f to 0.14f, 120f to 0.12f, 144f to 0.10f, 240f to 0.09f)
    val empX2 = interpolateAnchors(hz, 60f to 0.28f, 90f to 0.24f, 120f to 0.22f, 144f to 0.20f, 240f to 0.18f)

    val exitX1 = interpolateAnchors(hz, 60f to 0.36f, 90f to 0.34f, 120f to 0.32f, 144f to 0.30f, 240f to 0.28f)

    return LumaMotionProfile(
        effectiveHz = hz,
        frameMs = frameMs,
        durationScale = durationScale,
        stiffnessScale = stiffnessScale,
        dampingFloor = dampingFloor,
        dampingBoost = dampingBoost,
        lottieSpeed = lottieSpeed,
        standardEasing = CubicBezierEasing(stdX1, 1f, stdX2, 1f),
        emphasizedEasing = CubicBezierEasing(empX1, 1f, empX2, 1f),
        exitEasing = CubicBezierEasing(exitX1, 0f, 0.82f, 0.32f)
    )
}

private fun interpolateAnchors(x: Float, vararg points: Pair<Float, Float>): Float {
    if (points.isEmpty()) return 1f
    if (x <= points.first().first) return points.first().second
    if (x >= points.last().first) return points.last().second
    for (i in 0 until points.lastIndex) {
        val (x0, y0) = points[i]
        val (x1, y1) = points[i + 1]
        if (x in x0..x1) {
            val t = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
            return y0 + (y1 - y0) * t
        }
    }
    return points.last().second
}

val LocalLumaMotion = staticCompositionLocalOf { motionProfileFor(60f) }
