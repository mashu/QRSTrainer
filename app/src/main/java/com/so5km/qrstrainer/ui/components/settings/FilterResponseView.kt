package com.so5km.qrstrainer.ui.components.settings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class FilterResponseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val responsePaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val centerFreqPaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    
    private val gridPaint = Paint().apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        textSize = 20f
        isAntiAlias = true
    }
    
    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private var centerFrequency: Float = 600f
    private var bandwidth: Float = 50f
    private var showRinging: Boolean = false
    
    init {
        updateThemeColors()
    }
    
    private fun updateThemeColors() {
        // Get theme colors
        val typedValue = TypedValue()
        val theme = context.theme
        
        // Set surface background color
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        setBackgroundColor(typedValue.data)
        
        // Primary color for response curve
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data
        responsePaint.color = primaryColor
        
        // Set fill paint with transparency
        fillPaint.color = primaryColor
        fillPaint.alpha = 30
        
        // Secondary color for center frequency line
        theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)
        centerFreqPaint.color = typedValue.data
        
        // Outline color for grid
        theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
        gridPaint.color = typedValue.data
        
        // On surface color for text
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        textPaint.color = typedValue.data
        
        // Force redraw
        invalidate()
    }
    
    /**
     * Call this method when theme changes to update colors
     */
    fun refreshThemeColors() {
        updateThemeColors()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateThemeColors() // Update colors when attached to ensure current theme
    }
    
    fun setCenterFrequency(hz: Float) {
        centerFrequency = hz
        invalidate()
    }
    
    fun setBandwidth(hz: Float) {
        bandwidth = hz
        invalidate()
    }
    
    fun setShowRinging(show: Boolean) {
        showRinging = show
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Draw grid
        drawGrid(canvas, width, height)
        
        // Draw frequency response
        drawFrequencyResponse(canvas, width, height)
        
        // Draw center frequency line
        drawCenterFrequency(canvas, width, height)
        
        // Draw labels
        drawLabels(canvas, width, height)
        
        // Draw ringing visualization if enabled
        if (showRinging) {
            drawRingingVisualization(canvas, width, height)
        }
    }
    
    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        // Horizontal lines (dB scale)
        for (i in 0..5) {
            val y = height * i / 5
            canvas.drawLine(0f, y, width, y, gridPaint)
        }
        
        // Vertical lines (frequency scale)
        for (i in 0..10) {
            val x = width * i / 10
            canvas.drawLine(x, 0f, x, height * 0.8f, gridPaint)
        }
    }
    
    private fun drawFrequencyResponse(canvas: Canvas, width: Float, height: Float) {
        val path = Path()
        val fillPath = Path()
        val samples = 200
        
        // Frequency range: center ± 500 Hz
        val minFreq = centerFrequency - 500
        val maxFreq = centerFrequency + 500
        
        fillPath.moveTo(0f, height * 0.8f)
        
        for (i in 0..samples) {
            val x = width * i / samples
            val freq = minFreq + (maxFreq - minFreq) * i / samples
            
            // Butterworth filter response
            val response = calculateFilterResponse(freq)
            val y = height * 0.8f * (1 - response)
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        fillPath.lineTo(width, height * 0.8f)
        fillPath.close()
        
        // Draw filled area
        canvas.drawPath(fillPath, fillPaint)
        
        // Draw response curve
        canvas.drawPath(path, responsePaint)
    }
    
    private fun calculateFilterResponse(freq: Float): Float {
        // Butterworth filter response
        val normalizedFreq = (freq - centerFrequency) / (bandwidth / 2)
        val order = 4.0 // Filter order (sharper = more ringing)
        
        return 1f / sqrt(1 + normalizedFreq.toDouble().pow(2 * order)).toFloat()
    }
    
    private fun drawCenterFrequency(canvas: Canvas, width: Float, height: Float) {
        val x = width / 2
        canvas.drawLine(x, 0f, x, height * 0.8f, centerFreqPaint)
    }
    
    private fun drawRingingVisualization(canvas: Canvas, width: Float, height: Float) {
        // Draw impulse response showing ringing
        val startY = height * 0.85f
        val endY = height * 0.95f
        val centerY = (startY + endY) / 2
        
        val ringingPaint = Paint().apply {
            // Use tertiary color for ringing visualization
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorTertiary, typedValue, true)
            color = typedValue.data
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val path = Path()
        val samples = 200
        
        for (i in 0..samples) {
            val x = width * i / samples
            val t = i.toFloat() / samples * 10 // Time in units
            
            // Damped oscillation to show ringing
            val amplitude = exp(-t * bandwidth / 100) * cos(2 * PI * centerFrequency * t / 1000)
            val y = centerY - (endY - startY) * 0.4f * amplitude.toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, ringingPaint)
        
        // Label
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 16f
        canvas.drawText("Impulse Response (Ringing)", 10f, startY - 5f, textPaint)
    }
    
    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        textPaint.textSize = 20f
        
        // Center frequency label
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${centerFrequency.toInt()}Hz", width / 2, height * 0.8f + 30f, textPaint)
        
        // Bandwidth label
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("BW: ${bandwidth.toInt()}Hz", 10f, 30f, textPaint)
        
        // dB scale
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 16f
        canvas.drawText("0dB", width - 5f, 20f, textPaint)
        canvas.drawText("-40dB", width - 5f, height * 0.8f - 5f, textPaint)
    }
} 