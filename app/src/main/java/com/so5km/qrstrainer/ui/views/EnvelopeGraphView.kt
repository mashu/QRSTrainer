package com.so5km.qrstrainer.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.so5km.qrstrainer.R
import kotlin.math.*

/**
 * Custom view to display audio envelope visualization
 * Shows how envelope and keying style affect the audio signal
 */
class EnvelopeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Envelope parameters
    private var envelopeMs = 5 // Rise/fall time in ms
    private var keyingStyle = 0 // 0=Hard, 1=Soft, 2=Smooth
    private var volume = 0.7f // 0.0-1.0
    
    // Colors
    private var envelopeColor = Color.parseColor("#4CAF50") // Green
    private var signalColor = Color.parseColor("#2196F3") // Blue
    private var gridColor = Color.parseColor("#E0E0E0") // Light gray
    private var textColor = Color.parseColor("#424242") // Dark gray
    
    init {
        textPaint.apply {
            textSize = 24f
            color = textColor
        }
        
        gridPaint.apply {
            color = gridColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val graphHeight = height * 0.8f
        val graphTop = height * 0.1f
        
        // Draw background
        canvas.drawColor(Color.WHITE)
        
        // Draw grid
        drawGrid(canvas, width, graphHeight, graphTop)
        
        // Draw envelope
        drawEnvelope(canvas, width, graphHeight, graphTop)
        
        // Draw signal
        drawSignal(canvas, width, graphHeight, graphTop)
        
        // Draw labels
        drawLabels(canvas, width, height)
    }
    
    private fun drawGrid(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        // Vertical grid lines (time)
        for (i in 0..10) {
            val x = (i / 10f) * width
            canvas.drawLine(x, graphTop, x, graphTop + graphHeight, gridPaint)
        }
        
        // Horizontal grid lines (amplitude)
        for (i in 0..4) {
            val y = graphTop + (i / 4f) * graphHeight
            canvas.drawLine(0f, y, width, y, gridPaint)
        }
    }
    
    private fun drawEnvelope(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        paint.apply {
            color = envelopeColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        
        val path = Path()
        
        // Calculate envelope parameters
        val totalDuration = 100f // Total duration in ms
        val dotDuration = 50f // Dot duration in ms
        val envelopeSamples = (envelopeMs / totalDuration) * width
        
        // Calculate effective envelope based on keying style
        val effectiveEnvelopeSamples = when (keyingStyle) {
            0 -> envelopeSamples * 0.5f // Hard keying - shorter envelope
            1 -> envelopeSamples // Soft keying - normal envelope
            2 -> envelopeSamples * 2.0f // Smooth keying - longer envelope
            else -> envelopeSamples
        }
        
        // Draw envelope path
        path.moveTo(0f, graphTop + graphHeight) // Start at bottom left
        
        // Attack phase
        for (i in 0 until effectiveEnvelopeSamples.toInt()) {
            val x = i.toFloat()
            val ratio = i / effectiveEnvelopeSamples
            val amplitude = calculateEnvelopeShape(ratio, keyingStyle) * volume
            val y = graphTop + graphHeight * (1f - amplitude)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Sustain phase
        val sustainStart = effectiveEnvelopeSamples
        val sustainEnd = width - effectiveEnvelopeSamples
        path.lineTo(sustainEnd, graphTop + graphHeight * (1f - volume))
        
        // Release phase
        for (i in 0 until effectiveEnvelopeSamples.toInt()) {
            val x = sustainEnd + i
            val ratio = i / effectiveEnvelopeSamples
            val amplitude = (1f - calculateEnvelopeShape(ratio, keyingStyle)) * volume
            val y = graphTop + graphHeight * (1f - amplitude)
            path.lineTo(x, y)
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun calculateEnvelopeShape(ratio: Float, keyingStyle: Int): Float {
        return when (keyingStyle) {
            0 -> { // Hard keying - linear
                ratio.coerceIn(0f, 1f)
            }
            1 -> { // Soft keying - cosine shape
                (0.5f * (1f - cos(PI.toFloat() * ratio))).coerceIn(0f, 1f)
            }
            2 -> { // Smooth keying - sigmoid shape
                val x = (ratio * 2f) - 1f // -1 to 1
                (1f / (1f + exp(-5f * x))).coerceIn(0f, 1f)
            }
            else -> ratio.coerceIn(0f, 1f)
        }
    }
    
    private fun drawSignal(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        paint.apply {
            color = signalColor
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        val path = Path()
        
        // Calculate signal parameters
        val frequency = 600 // Hz
        val sampleRate = 44100 // Hz
        val samplesPerPixel = sampleRate / (width / 0.1f) // 0.1 seconds total width
        
        // Draw signal path
        for (i in 0 until width.toInt()) {
            val x = i.toFloat()
            
            // Calculate envelope at this point
            val envelopeFactor = calculateEnvelopeFactorAtPosition(x, width)
            
            // Calculate signal amplitude with envelope applied
            val angle = 2.0f * PI.toFloat() * i * frequency / (sampleRate / samplesPerPixel)
            val signalAmplitude = sin(angle) * envelopeFactor * volume
            
            val y = graphTop + graphHeight / 2f * (1f - signalAmplitude)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun calculateEnvelopeFactorAtPosition(x: Float, width: Float): Float {
        // Calculate envelope parameters
        val effectiveEnvelopeSamples = when (keyingStyle) {
            0 -> (envelopeMs / 100f) * width * 0.5f // Hard keying - shorter envelope
            1 -> (envelopeMs / 100f) * width // Soft keying - normal envelope
            2 -> (envelopeMs / 100f) * width * 2.0f // Smooth keying - longer envelope
            else -> (envelopeMs / 100f) * width
        }
        
        val sustainStart = effectiveEnvelopeSamples
        val sustainEnd = width - effectiveEnvelopeSamples
        
        return when {
            x < sustainStart -> {
                val ratio = x / effectiveEnvelopeSamples
                calculateEnvelopeShape(ratio, keyingStyle)
            }
            x > sustainEnd -> {
                val ratio = (x - sustainEnd) / effectiveEnvelopeSamples
                1f - calculateEnvelopeShape(ratio, keyingStyle)
            }
            else -> 1f
        }
    }
    
    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        // Time labels
        textPaint.textSize = 16f
        val timeLabels = arrayOf("0", "25", "50", "75", "100")
        for (i in timeLabels.indices) {
            val x = (i / 4f) * width
            val label = "${timeLabels[i]} ms"
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth/2, height - 10f, textPaint)
        }
        
        // Envelope info
        textPaint.textSize = 14f
        val keyingStyleText = when (keyingStyle) {
            0 -> "Hard Keying"
            1 -> "Soft Keying"
            2 -> "Smooth Keying"
            else -> "Unknown"
        }
        canvas.drawText("Envelope: $envelopeMs ms, $keyingStyleText", 10f, 20f, textPaint)
        
        // Volume info
        val volumePercent = (volume * 100).toInt()
        canvas.drawText("Volume: $volumePercent%", 10f, 40f, textPaint)
    }
    
    /**
     * Update envelope parameters
     */
    fun updateEnvelope(envelopeMs: Int) {
        this.envelopeMs = envelopeMs
        invalidate()
    }
    
    /**
     * Update keying style
     */
    fun updateKeyingStyle(style: Int) {
        this.keyingStyle = style
        invalidate()
    }
    
    /**
     * Update volume
     */
    fun updateVolume(volume: Float) {
        this.volume = volume
        invalidate()
    }
    
    /**
     * Update all parameters at once
     */
    fun updateParameters(envelopeMs: Int, keyingStyle: Int, volume: Float) {
        this.envelopeMs = envelopeMs
        this.keyingStyle = keyingStyle
        this.volume = volume
        invalidate()
    }
} 