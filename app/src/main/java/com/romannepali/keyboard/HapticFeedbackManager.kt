package com.romannepali.keyboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Manages fine-tuned tactile haptic feedback for keyboard interactions.
 * Provides distinct tactile sensations for letter typing, spacebar, deletion,
 * action/enter, and long-press gestures, scaled by the user's intensity pref.
 */
class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    enum class FeedbackType {
        KEY_LETTER,
        KEY_SPACE,
        KEY_DELETE,
        KEY_ACTION,
        LONG_PRESS,
        GESTURE_TICK
    }

    /** Triggers a fine-tuned tactile response based on the key type. */
    fun performKeyHaptic(view: View? = null, type: FeedbackType = FeedbackType.KEY_LETTER) {
        if (!Prefs.vibration(context)) return
        val intensity = Prefs.vibrationIntensity(context)
        if (intensity <= 0) return

        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intensity >= 40) {
                    val effect = when (type) {
                        FeedbackType.KEY_LETTER -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        FeedbackType.KEY_SPACE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        FeedbackType.KEY_DELETE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        FeedbackType.KEY_ACTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        FeedbackType.LONG_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        FeedbackType.GESTURE_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    }
                    vibrator.vibrate(effect)
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val (duration, baseAmp) = when (type) {
                        FeedbackType.KEY_LETTER -> 10L to 50
                        FeedbackType.KEY_SPACE -> 15L to 80
                        FeedbackType.KEY_DELETE -> 14L to 100
                        FeedbackType.KEY_ACTION -> 22L to 140
                        FeedbackType.LONG_PRESS -> 35L to 180
                        FeedbackType.GESTURE_TICK -> 8L to 40
                    }
                    val amplitude = ((baseAmp * intensity) / 100).coerceIn(1, 255)
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                    return
                } else {
                    @Suppress("DEPRECATION")
                    val duration = when (type) {
                        FeedbackType.KEY_LETTER -> (12L * intensity / 100).coerceAtLeast(1L)
                        FeedbackType.KEY_SPACE -> (18L * intensity / 100).coerceAtLeast(2L)
                        FeedbackType.KEY_DELETE -> (16L * intensity / 100).coerceAtLeast(2L)
                        FeedbackType.KEY_ACTION -> (25L * intensity / 100).coerceAtLeast(5L)
                        FeedbackType.LONG_PRESS -> (40L * intensity / 100).coerceAtLeast(10L)
                        FeedbackType.GESTURE_TICK -> (10L * intensity / 100).coerceAtLeast(1L)
                    }
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                    return
                }
            } catch (_: Exception) {
                // Fall through to the view-based haptic fallback below.
            }
        }

        view?.let { v ->
            when (type) {
                FeedbackType.KEY_LETTER, FeedbackType.GESTURE_TICK ->
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                FeedbackType.KEY_SPACE, FeedbackType.KEY_ACTION ->
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                FeedbackType.KEY_DELETE ->
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                FeedbackType.LONG_PRESS ->
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /** Demonstration pulse for the settings "test vibration" button. */
    fun testVibration() {
        val intensity = Prefs.vibrationIntensity(context)
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = ((160 * intensity) / 100).coerceIn(1, 255)
                    vibrator.vibrate(VibrationEffect.createOneShot(30L, amplitude))
                    return
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30L)
                    return
                }
            } catch (_: Exception) {
                // Ignore: no vibrator or security exception.
            }
        }
    }
}
