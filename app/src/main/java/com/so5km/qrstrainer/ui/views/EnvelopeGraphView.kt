package com.so5km.qrstrainer.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.so5km.qrstrainer.R
import kotlin.math.*

/**
 * Custom view to display the audio envelope curve
 * Shows how the signal rises and falls based on keying style and envelope timing
 */
class EnvelopeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var envelopeMs = 5
    private var keyingStyle = 0 // 0=Hard, 1=Soft, 2=Smooth
    private var toneFrequency = 600 // Hz
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 50
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    init {
        // Get colors from theme
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.EnvelopeGraphView)
        try {
            // Use default colors if custom attributes aren't defined
            paint.color = context.getColor(R.color.accent_green)
            fillPaint.color = context.getColor(R.color.accent_green)
            gridPaint.color = context.getColor(R.color.grey_400)
            textPaint.color = context.getColor(R.color.grey_800)
        } finally {
            typedArray.recycle()
        }
    }
    
    fun updateEnvelope(envelopeMs: Int, keyingStyle: Int, toneFrequency: Int = 600) {
        this.envelopeMs = envelopeMs
        this.keyingStyle = keyingStyle
        this.toneFrequency = toneFrequency
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 40f
        
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding - 60f // Extra space for labels
        
        // Draw background
        canvas.drawRect(0f, 0f, width, height, Paint().apply {
            color = context.getColor(R.color.card_background)
            style = Paint.Style.FILL
        })
        
        // Draw grid
        drawGrid(canvas, padding, graphWidth, graphHeight)
        
        // Draw envelope curve
        drawEnvelopeCurve(canvas, padding, graphWidth, graphHeight)
        
        // Draw labels
        drawLabels(canvas, padding, width, height, graphHeight)
    }
    
    private fun drawGrid(canvas: Canvas, padding: Float, graphWidth: Float, graphHeight: Float) {
        // Horizontal grid lines (amplitude)
        for (i in 0..4) {
            val y = padding + (i * graphHeight / 4)
            canvas.drawLine(padding, y, padding + graphWidth, y, gridPaint)
        }
        
        // Vertical grid lines (time)
        for (i in 0..8) {
            val x = padding + (i * graphWidth / 8)
            canvas.drawLine(x, padding, x, padding + graphHeight, gridPaint)
        }
    }
    
    private fun drawEnvelopeCurve(canvas: Canvas, padding: Float, graphWidth: Float, graphHeight: Float) {
        val path = Path()
        val fillPath = Path()
        
        // Calculate envelope parameters
        val totalDuration = 100 // ms for visualization
        val envelopeDuration = minOf(envelopeMs, totalDuration / 4)
        val sustainDuration = totalDuration - (2 * envelopeDuration)
        
        val points = 200
        var isFirstPoint = true
        
        fillPath.moveTo(padding, padding + graphHeight) // Start at bottom
        
        for (i in 0..points) {
            val timeRatio = i.toFloat() / points
            val x = padding + timeRatio * graphWidth
            
            // Calculate amplitude based on time and keying style
            val timeMs = timeRatio * totalDuration
            val amplitude = calculateEnvelopeAmplitude(timeMs, envelopeDuration, sustainDuration, totalDuration)
            
            val y = padding + graphHeight - (amplitude * graphHeight)
            
            if (isFirstPoint) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
                isFirstPoint = false
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        // Close fill path
        fillPath.lineTo(padding + graphWidth, padding + graphHeight)
        fillPath.close()
        
        // Draw filled area
        canvas.drawPath(fillPath, fillPaint)
        
        // Draw envelope curve
        canvas.drawPath(path, paint)
    }
    
    private fun calculateEnvelopeAmplitude(timeMs: Float, envelopeDuration: Int, sustainDuration: Int, totalDuration: Int): Float {
        // Calculate frequency-dependent sharpness factor
        // Higher frequencies (800-1000Hz) create sharper envelopes
        // Lower frequencies (300-500Hz) create gentler envelopes
        val frequencyFactor = when {
            toneFrequency >= 800 -> 1.4f // Sharp
            toneFrequency >= 600 -> 1.0f // Normal
            toneFrequency >= 400 -> 0.7f // Gentle
            else -> 0.5f // Very gentle
        }
        
        return when {
            timeMs <= envelopeDuration -> {
                // Rise phase
                val fadeRatio = timeMs / envelopeDuration
                when (keyingStyle) {
                    0 -> fadeRatio.pow(1.0f / frequencyFactor) // Hard keying - affected by frequency
                    1 -> 0.5f * (1.0f - cos(PI.toFloat() * fadeRatio.pow(1.0f / frequencyFactor))) // Soft keying - cosine with frequency effect
                    2 -> sin(PI.toFloat() * fadeRatio.pow(1.0f / frequencyFactor) / 2.0f).pow(2) // Smooth keying - sine squared with frequency effect
                    else -> fadeRatio
                }
            }
            timeMs <= envelopeDuration + sustainDuration -> {
                1.0f // Sustain phase
            }
            else -> {
                // Fall phase
                val remainingTime = totalDuration - timeMs
                val fadeRatio = remainingTime / envelopeDuration
                when (keyingStyle) {
                    0 -> fadeRatio.pow(1.0f / frequencyFactor) // Hard keying - affected by frequency
                    1 -> 0.5f * (1.0f - cos(PI.toFloat() * fadeRatio.pow(1.0f / frequencyFactor))) // Soft keying - cosine with frequency effect
                    2 -> sin(PI.toFloat() * fadeRatio.pow(1.0f / frequencyFactor) / 2.0f).pow(2) // Smooth keying - sine squared with frequency effect
                    else -> fadeRatio
                }
            }
        }
    }
    
    private fun drawLabels(canvas: Canvas, padding: Float, width: Float, height: Float, graphHeight: Float) {
        // Y-axis labels (amplitude)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 20f
        for (i in 0..4) {
            val y = padding + (i * graphHeight / 4) + 8f
            val amplitude = (4 - i) * 25 // 0%, 25%, 50%, 75%, 100%
            canvas.drawText("${amplitude}%", padding - 10f, y, textPaint)
        }
        
        // X-axis labels (time)
        textPaint.textAlign = Paint.Align.CENTER
        val timeLabels = arrayOf("0", "25", "50", "75", "100")
        for (i in timeLabels.indices) {
            val x = padding + (i * width / (timeLabels.size - 1))
            canvas.drawText("${timeLabels[i]}ms", x, height - 20f, textPaint)
        }
        
        // Title
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        val styleText = when (keyingStyle) {
            0 -> "Hard Keying"
            1 -> "Soft Keying"
            2 -> "Smooth Keying"
            else -> "Unknown"
        }
        val frequencyEffect = when {
            toneFrequency >= 800 -> "Sharp"
            toneFrequency >= 600 -> "Normal"
            toneFrequency >= 400 -> "Gentle"
            else -> "Very Gentle"
        }
        canvas.drawText("$styleText (${envelopeMs}ms) - ${toneFrequency}Hz ($frequencyEffect)", width / 2, 30f, textPaint)
    }
} 