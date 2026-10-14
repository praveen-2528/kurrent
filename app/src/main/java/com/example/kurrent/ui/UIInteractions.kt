package com.example.kurrent.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation

object UIInteractions {

    /**
     * 3D Y-Axis Flip Animation for Active Recall (Front to Back or Back to Front).
     * Duration: 220ms total (fast, crisp, and interruptible).
     */
    fun flipCard(context: Context, frontView: View, backView: View, toBack: Boolean) {
        val distance = 8000
        val scale = context.resources.displayMetrics.density * distance
        frontView.cameraDistance = scale
        backView.cameraDistance = scale

        val visibleView = if (toBack) frontView else backView
        val invisibleView = if (toBack) backView else frontView

        // 1. Flip out currently visible view (0 to 90 degrees)
        val flipOut = ObjectAnimator.ofFloat(visibleView, "rotationY", 0f, if (toBack) 90f else -90f).apply {
            duration = 110
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 2. Flip in incoming view (-90 to 0 degrees)
        val flipIn = ObjectAnimator.ofFloat(invisibleView, "rotationY", if (toBack) -90f else 90f, 0f).apply {
            duration = 110
            interpolator = AccelerateDecelerateInterpolator()
        }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibleView.visibility = View.GONE
                invisibleView.visibility = View.VISIBLE
                flipIn.start()
            }
        })

        flipOut.start()
    }

    /**
     * Subtle 200ms horizontal shake animation (±6px) for wrong quiz options.
     */
    fun shakeView(view: View) {
        val shake = TranslateAnimation(0f, 6f, 0f, 0f).apply {
            duration = 200
            interpolator = CycleInterpolator(3f) // Shakes 3 times in 200ms
        }
        view.startAnimation(shake)
    }

    /**
     * Crisp light haptic tap for correct answer.
     */
    fun hapticSuccess(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Soft double-vibrate haptic rejection for wrong answer.
     */
    fun hapticError(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
