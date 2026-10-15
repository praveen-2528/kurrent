package com.example.kurrent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.kurrent.R
import kotlin.math.sin

/**
 * Custom 3-Bar Sinusoidal Breathing Audio Waveform Visualizer.
 * Renders 3 phase-shifted vertical rounded bars oscillating smoothly at 60/120fps.
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.teal_accent)
        style = Paint.Style.FILL
    }

    private val density = context.resources.displayMetrics.density
    private val barWidth = 3.5f * density
    private val barGap = 3.5f * density
    private val minBarHeight = 4f * density
    private val maxBarHeight = 18f * density
    private val cornerRadius = barWidth / 2f

    private val barRects = Array(3) { RectF() }
    private val barHeights = FloatArray(3) { minBarHeight }

    // Phase offsets for the 3 bars: 0°, 120°, 240°
    private val phaseOffsets = floatArrayOf(
        0f,
        (2.0 * Math.PI / 3.0).toFloat(),
        (4.0 * Math.PI / 3.0).toFloat()
    )

    private var currentPhase: Float = 0f
    private var isPlaying: Boolean = false

    private val animator = ValueAnimator.ofFloat(0f, (2.0 * Math.PI).toFloat()).apply {
        duration = 850L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { va ->
            currentPhase = va.animatedValue as Float
            updateWaveHeights()
            invalidate()
        }
    }

    private fun updateWaveHeights() {
        if (!isPlaying) return
        for (i in 0..2) {
            val sinVal = (sin(currentPhase + phaseOffsets[i]) + 1.0) / 2.0 // [0, 1]
            barHeights[i] = minBarHeight + (maxBarHeight - minBarHeight) * sinVal.toFloat()
        }
    }

    fun setPlaying(playing: Boolean) {
        if (this.isPlaying == playing) return
        this.isPlaying = playing
        if (playing) {
            if (!animator.isRunning) {
                animator.start()
            }
        } else {
            animator.cancel()
            // Smoothly collapse bars to resting dots
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 200L
                addUpdateListener { va ->
                    val frac = va.animatedFraction
                    for (i in 0..2) {
                        barHeights[i] = minBarHeight + (barHeights[i] - minBarHeight) * (1f - frac)
                    }
                    invalidate()
                }
                start()
            }
        }
    }

    fun isPlaying(): Boolean = isPlaying

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = (3 * barWidth) + (2 * barGap)
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f

        for (i in 0..2) {
            val left = startX + i * (barWidth + barGap)
            val right = left + barWidth
            val h = barHeights[i]
            val top = centerY - (h / 2f)
            val bottom = centerY + (h / 2f)

            barRects[i].set(left, top, right, bottom)
            canvas.drawRoundRect(barRects[i], cornerRadius, cornerRadius, barPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
