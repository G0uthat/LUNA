package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.example.R
import com.example.ui.theme.LocalLumaMotion

/**
 * Smooth Lottie beacon animation used to draw attention to the active spotlighted element
 * during the interactive tour, dynamically tinted to Luma's current palette accent and
 * paced to the active display refresh rate.
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
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 0.96f * motion.lottieSpeed
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
 * Crisp one-shot Lottie spark reveal animation replayed smoothly whenever a new word
 * opens in the contextual dictionary or when advancing to a new interactive tour step.
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
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = 1,
            speed = 1.02f * motion.lottieSpeed
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            dynamicProperties = dynamicProperties,
            renderMode = RenderMode.AUTOMATIC,
            modifier = modifier
        )
    }
}

/**
 * Fluid 3-dot wave Lottie animation replacing harsh spinners during live dictionary
 * translation and Wikipedia/definition lookup.
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
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 1.08f * motion.lottieSpeed
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
