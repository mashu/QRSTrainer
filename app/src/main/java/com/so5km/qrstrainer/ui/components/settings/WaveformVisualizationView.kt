package com.so5km.qrstrainer.ui.components.settings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class WaveformVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val waveformPaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val envelopePaint = Paint().apply {
        color = Color.parseColor("#FF9800")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.parseColor("#757575")
        textSize = 24f
        isAntiAlias = true
    }
    
    private var riseTimeMs: Float = 5f
    private var frequency: Float = 600f
    private var showEnvelope: Boolean = true
    
    fun setRiseTime(ms: Float) {
        riseTimeMs = ms
        invalidate()
    }
    
    fun setFrequency(hz: Float) {
        frequency = hz
        invalidate()
    }
    
    fun setShowEnvelope(show: Boolean) {
        showEnvelope = show
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        
        // Draw grid
        drawGrid(canvas, width, height)
        
        // Draw waveform
        drawWaveform(canvas, width, centerY)
        
        // Draw envelope if enabled
        if (showEnvelope) {
            drawEnvelope(canvas, width, centerY)
        }
        
        // Draw labels
        drawLabels(canvas, width, height)
    }
    
    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        // Horizontal lines
        for (i in 0..4) {
            val y = height * i / 4
            canvas.drawLine(0f, y, width, y, gridPaint)
        }
        
        // Vertical lines
        for (i in 0..10) {
            val x = width * i / 10
            canvas.drawLine(x, 0f, x, height, gridPaint)
        }
    }
    
    private fun drawWaveform(canvas: Canvas, width: Float, centerY: Float) {
        val path = Path()
        val samples = 500
        val amplitude = centerY * 0.8f
        
        // Duration for one dit (60ms at 20 WPM)
        val ditDuration = 60f // ms
        val totalDuration = ditDuration * 1.5f // Show 1.5 dits
        
        for (i in 0..samples) {
            val x = width * i / samples
            val t = totalDuration * i / samples / 1000f // Convert to seconds
            
            // Generate sine wave
            val y = if (t * 1000 < ditDuration) {
                centerY - amplitude * sin(2 * PI * frequency * t).toFloat() * getEnvelopeValue(t * 1000, ditDuration)
            } else {
                centerY
            }
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, waveformPaint)
    }
    
    private fun drawEnvelope(canvas: Canvas, width: Float, centerY: Float) {
        val path = Path()
        val samples = 100
        val amplitude = centerY * 0.8f
        val ditDuration = 60f
        
        // Draw upper envelope
        for (i in 0..samples) {
            val x = width * i / samples
            val t = ditDuration * 1.5f * i / samples
            
            val envelope = if (t < ditDuration) {
                getEnvelopeValue(t, ditDuration)
            } else {
                0f
            }
            
            val y = centerY - amplitude * envelope
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, envelopePaint)
        
        // Draw lower envelope
        path.reset()
        for (i in 0..samples) {
            val x = width * i / samples
            val t = ditDuration * 1.5f * i / samples
            
            val envelope = if (t < ditDuration) {
                getEnvelopeValue(t, ditDuration)
            } else {
                0f
            }
            
            val y = centerY + amplitude * envelope
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, envelopePaint)
    }
    
    private fun getEnvelopeValue(t: Float, duration: Float): Float {
        val riseTime = riseTimeMs
        val fallTime = riseTimeMs
        
        return when {
            t < riseTime -> {
                // Raised cosine rise
                0.5f * (1 - cos(PI * t / riseTime)).toFloat()
            }
            t < duration - fallTime -> {
                // Sustain
                1f
            }
            t < duration -> {
                // Raised cosine fall
                val fallT = t - (duration - fallTime)
                0.5f * (1 + cos(PI * fallT / fallTime)).toFloat()
            }
            else -> 0f
        }
    }
    
    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        // Rise time label
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Rise: ${riseTimeMs.toInt()}ms", 10f, height - 10f, textPaint)
        
        // Frequency label
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${frequency.toInt()}Hz", width - 10f, height - 10f, textPaint)
    }
} 