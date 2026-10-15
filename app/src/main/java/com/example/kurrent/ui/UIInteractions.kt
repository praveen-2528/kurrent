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
     * Organic 3D Y-Axis Flip Animation for Spaced Repetition & Active Recall.
     * Implements:
     * 1. 8000dp camera perspective to prevent distortion.
     * 2. Subtle 1.04x lift toward the camera at mid-point.
     * 3. Apex haptic tick at 90° transition.
     * 4. Natural acceleration into flip, smooth deceleration into landing.
     */
    fun flipCard(context: Context, frontView: View, backView: View, toBack: Boolean) {
        val density = context.resources.displayMetrics.density
        val cameraScale = 8000f * density
        frontView.cameraDistance = cameraScale
        backView.cameraDistance = cameraScale

        val visibleView = if (toBack) frontView else backView
        val invisibleView = if (toBack) backView else frontView

        // Phase 1: Flip out from 0° to 90° with a subtle lift towards user (1.0x -> 1.04x)
        val targetFlipOutAngle = if (toBack) 90f else -90f
        val rotOut = ObjectAnimator.ofFloat(visibleView, "rotationY", 0f, targetFlipOutAngle)
        val scaleXOut = ObjectAnimator.ofFloat(visibleView, "scaleX", 1.0f, 1.04f)
        val scaleYOut = ObjectAnimator.ofFloat(visibleView, "scaleY", 1.0f, 1.04f)

        val flipOutSet = android.animation.AnimatorSet().apply {
            playTogether(rotOut, scaleXOut, scaleYOut)
            duration = 150L
            interpolator = android.view.animation.AccelerateInterpolator(1.4f)
        }

        // Phase 2: Flip in from 90° to 0° settling back down (1.04x -> 1.0x)
        val startFlipInAngle = if (toBack) -90f else 90f
        invisibleView.rotationY = startFlipInAngle
        invisibleView.scaleX = 1.04f
        invisibleView.scaleY = 1.04f

        val rotIn = ObjectAnimator.ofFloat(invisibleView, "rotationY", startFlipInAngle, 0f)
        val scaleXIn = ObjectAnimator.ofFloat(invisibleView, "scaleX", 1.04f, 1.0f)
        val scaleYIn = ObjectAnimator.ofFloat(invisibleView, "scaleY", 1.04f, 1.0f)

        val flipInSet = android.animation.AnimatorSet().apply {
            playTogether(rotIn, scaleXIn, scaleYIn)
            duration = 170L
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
        }

        flipOutSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibleView.visibility = View.GONE
                invisibleView.visibility = View.VISIBLE

                // Apex haptic tick on 90° flip point
                invisibleView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                flipInSet.start()
            }
        })

        flipOutSet.start()
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
