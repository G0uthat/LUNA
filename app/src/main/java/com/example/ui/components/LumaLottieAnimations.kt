package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.example.R
import com.example.ui.theme.LocalLumaMotion

/**
 * Custom silky EaseInOut cubic-Bezier curve calibrated for Lottie progress playback
 * across 60–240 Hz displays (gentle acceleration, velvety deceleration, zero harsh snap).
 */
val LumaEaseInOutCurve = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

/**
 * Smooth Lottie beacon animation used to draw attention to the active spotlighted element
 * during the initial interactive tour, driven with a custom EaseInOut interpolation curve
 * and paced to the active display refresh rate.
 */
@Composable
fun LumaTourBeaconLottie(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val motion = LocalLumaMotion.current
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_tour_beacon)
    )
    val transition = rememberInfiniteTransition(label = "lottie_tour_beacon_ease")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.ambientDuration(1950),
                easing = LumaEaseInOutCurve
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "lottie_tour_beacon_progress"
    )
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = SimpleColorFilter(tint.toArgb()),
            keyPath = arrayOf("**")
        )
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        renderMode = RenderMode.AUTOMATIC,
        modifier = modifier
    )
}

/**
 * Crisp one-shot Lottie spark reveal animation driven by a custom EaseInOut curve,
 * replayed smoothly whenever a new word opens in the contextual dictionary or when
 * advancing to a new interactive tour step.
 */
@Composable
fun LumaSparkRevealLottie(
    replayKey: Any,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val motion = LocalLumaMotion.current
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_spark_reveal)
    )
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = SimpleColorFilter(tint.toArgb()),
            keyPath = arrayOf("**")
        )
    )
    key(replayKey) {
        val progressAnim = remember { Animatable(0f) }
        LaunchedEffect(composition, motion.effectiveHz) {
            if (composition != null) {
                progressAnim.snapTo(0f)
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = motion.duration(860),
                        easing = LumaEaseInOutCurve
                    )
                )
            }
        }
        LottieAnimation(
            composition = composition,
            progress = { progressAnim.value },
            dynamicProperties = dynamicProperties,
            renderMode = RenderMode.AUTOMATIC,
            modifier = modifier
        )
    }
}

/**
 * Fluid 3-dot wave Lottie loading animation driven by custom EaseInOut interpolation
 * for live dictionary translation, Wikipedia summary lookup, and embedded dictionary tabs.
 */
@Composable
fun LumaDictionaryPulseLottie(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val motion = LocalLumaMotion.current
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_dict_pulse)
    )
    val transition = rememberInfiniteTransition(label = "lottie_dict_pulse_ease")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.ambientDuration(1320),
                easing = LumaEaseInOutCurve
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "lottie_dict_pulse_progress"
    )
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = SimpleColorFilter(tint.toArgb()),
            keyPath = arrayOf("**")
        )
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        renderMode = RenderMode.AUTOMATIC,
        modifier = modifier
    )
}
