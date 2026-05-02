package com.applimiter

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0
    private var timeLeftText = "00:00"

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A2744")
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8899BB")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private val oval = RectF()

    fun setProgress(value: Int) {
        progress = value
        updateProgressColor()
        invalidate()
    }

    fun setTimeLeft(text: String) {
        timeLeftText = text
        invalidate()
    }

    private fun updateProgressColor() {
        val color = when {
            progress >= 90 -> Color.parseColor("#FF4444")
            progress >= 70 -> Color.parseColor("#FF9944")
            else -> Color.parseColor("#44BB88")
        }
        progressPaint.color = color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - 30f

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background arc
        canvas.drawArc(oval, -90f, 360f, false, backgroundPaint)

        // Progress arc
        val sweepAngle = 360f * progress / 100f
        canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)

        // Percentage text
        canvas.drawText("$progress%", cx, cy - 10f, textPaint)

        // Time left label
        canvas.drawText("осталось", cx, cy + 30f, labelPaint)
    }
}
