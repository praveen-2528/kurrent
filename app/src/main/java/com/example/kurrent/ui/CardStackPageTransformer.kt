package com.example.kurrent.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.max
import kotlin.math.min

/**
 * High-Fidelity Physics-Based Card Stack Transformer for ViewPager2.
 * Implements:
 * 1. 8000dp camera perspective to prevent distortion.
 * 2. Velocity-based tilt angle (12° max) on active swipe.
 * 3. Layered depth scaling (0.94x -> 1.0x) as cards are revealed from the physical deck.
 * 4. Micro-elevation translation along the Y-axis.
 */
class CardStackPageTransformer : ViewPager2.PageTransformer {

    private val baseScale = 0.94f
    private val maxTiltAngle = 12f

    override fun transformPage(page: View, position: Float) {
        val density = page.resources.displayMetrics.density
        page.cameraDistance = 8000f * density

        when {
            position < -1f -> {
                // Completely swiped off-screen to the left
                page.alpha = 0f
                page.rotation = -maxTiltAngle
            }
            position <= 0f -> {
                // Top active card being swiped out to the left
                // Dynamic tilt angle proportional to swipe progress (0° to -12°)
                page.rotation = position * maxTiltAngle
                // Natural linear alpha fadeout (1.0 -> 0.4)
                page.alpha = 1f + (position * 0.6f)
                page.scaleX = 1f
                page.scaleY = 1f
                page.translationX = 0f
                page.translationY = 0f
            }
            position <= 3f -> {
                // Stacked cards underneath waiting in the deck
                // Depth scaling: smoothly scales up from 0.94x -> 1.0x as position moves from 1.0 -> 0.0
                val scaleFactor = min(1f, max(baseScale, 1f - (position * (1f - baseScale))))
                page.scaleX = scaleFactor
                page.scaleY = scaleFactor

                // Subtle alpha stacking
                page.alpha = max(0f, 1f - (position * 0.25f))

                // Physical deck alignment: counteract ViewPager horizontal slide
                page.translationX = -position * page.width + (position * 10f * density)

                // Micro-elevation along Y-axis to give physical card layering
                page.translationY = -position * (14f * density)

                // Stacked cards remain flat until activated
                page.rotation = 0f
            }
            else -> {
                // Deep background cards
                page.alpha = 0f
            }
        }
    }
}
