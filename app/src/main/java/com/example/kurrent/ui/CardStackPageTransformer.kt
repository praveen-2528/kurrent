package com.example.kurrent.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max

/**
 * Physics-based Card Stack Transformer for ViewPager2.
 * Subtly scales incoming card from 95% to 100% size and 70% to 100% opacity,
 * simulating a tactile physical deck of flashcards.
 */
class CardStackPageTransformer : ViewPager2.PageTransformer {

    private val minScale = 0.95f
    private val minAlpha = 0.70f

    override fun transformPage(page: View, position: Float) {
        val absPos = abs(position)

        if (position < -1) {
            // Page is way off-screen to the left
            page.alpha = 0f
        } else if (position <= 1) {
            // Page is transitioning between [-1, 1]
            // Modify the default slide transition to shrink and fade the page
            val scaleFactor = max(minScale, 1 - absPos * (1 - minScale))
            val alphaFactor = max(minAlpha, 1 - absPos * (1 - minAlpha))

            page.scaleX = scaleFactor
            page.scaleY = scaleFactor
            page.alpha = alphaFactor

            // Counteract default slide margin to keep deck close
            page.translationX = -position * (page.width / 15f)
        } else {
            // Page is way off-screen to the right
            page.alpha = 0f
        }
    }
}
