package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Provides tactile haptic feedback (both View/Compose HapticFeedback and hardware Vibrator effects)
 * for task completion and custom navigation/settings buttons.
 */
class HapticFeedbackController(
    private val context: Context,
    private val view: View? = null,
    private val composeHaptic: HapticFeedback? = null
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Subtle, crisp tactile tick for navigation tabs, theme toggle, menu buttons, and filter chips.
     */
    fun performNavigationTap() {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                ?: composeHaptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(12L, 80))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(12L)
            }
        } catch (_: Exception) {
            // Ignore on unsupported hardware
        }
    }

    /**
     * Distinct tactile confirmation pulse when a task is marked as completed (or toggled).
     */
    fun performTaskCompletion(isCompleting: Boolean = true) {
        try {
            if (isCompleting && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    ?: composeHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = if (isCompleting) {
                    VibrationEffect.EFFECT_CLICK
                } else {
                    VibrationEffect.EFFECT_TICK
                }
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = if (isCompleting) 28L else 14L
                val amplitude = if (isCompleting) 150 else 90
                vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (isCompleting) 28L else 14L)
            }
        } catch (_: Exception) {
            // Ignore on unsupported hardware
        }
    }
}

@Composable
fun rememberHapticController(): HapticFeedbackController {
    val context = LocalContext.current
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(context, view, composeHaptic) {
        HapticFeedbackController(
            context = context,
            view = view,
            composeHaptic = composeHaptic
        )
    }
}
