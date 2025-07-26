package com.so5km.qrstrainer.ui.components.settings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.*

/**
 * Visualizes the frequency spectrum of audio passing through the filter
 * Shows both the input spectrum and filtered output spectrum
 */
class SpectrumVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val inputSpectrumPaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 100
    }
    
    private val outputSpectrumPaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val gridPaint = Paint().apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 50
    }
    
    private val textPaint = Paint().apply {
        textSize = 16f
        isAntiAlias = true
    }
    
    private val filterResponsePaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    
    private var inputSpectrum: FloatArray? = null
    private var outputSpectrum: FloatArray? = null
    private var filterResponse: FloatArray? = null
    private var centerFrequency: Float = 600f
    private var bandwidth: Float = 500f
    private var filterOrder: Int = 4
    private var sampleRate: Int = 44100
    private var showFullSpectrum: Boolean = false // Toggle between full and zoomed view
    
    init {
        updateThemeColors()
        
        // Make clickable to toggle between views
        setOnClickListener {
            showFullSpectrum = !showFullSpectrum
            invalidate()
        }
    }
    
    private fun updateThemeColors() {
        val typedValue = TypedValue()
        val theme = context.theme
        
        // Background
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        setBackgroundColor(typedValue.data)
        
        // Input spectrum (gray)
        theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
        inputSpectrumPaint.color = typedValue.data
        
        // Output spectrum (primary color)
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        outputSpectrumPaint.color = typedValue.data
        
        // Filter response (secondary color)
        theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)
        filterResponsePaint.color = typedValue.data
        
        // Grid
        theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
        gridPaint.color = typedValue.data
        gridPaint.alpha = 30
        
        // Text
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        textPaint.color = typedValue.data
    }
    
    fun updateSpectrum(input: FloatArray?, output: FloatArray?) {
        inputSpectrum = input
        outputSpectrum = output
        // Generate filter response for display
        filterResponse = generateFilterResponse()
        invalidate()
    }
    
    fun setFilterParameters(centerFreq: Float, bw: Float, order: Int = 4) {
        centerFrequency = centerFreq
        bandwidth = bw
        filterOrder = order
        filterResponse = generateFilterResponse()
        invalidate()
    }
    
    fun setSampleRate(rate: Int) {
        sampleRate = rate
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Draw grid
        drawGrid(canvas, width, height)
        
        // Draw spectrums and filter response
        inputSpectrum?.let { 
            drawSpectrum(canvas, it, inputSpectrumPaint, width, height, "Input", true) 
        }
        
        // Draw filter response curve
        filterResponse?.let {
            drawFilterResponse(canvas, it, filterResponsePaint, width, height)
        }
        
        outputSpectrum?.let { 
            drawSpectrum(canvas, it, outputSpectrumPaint, width, height, "Output", false) 
        }
        
        // Draw labels
        drawLabels(canvas, width, height)
        
        // Draw view mode indicator
        drawViewMode(canvas, width, height)
    }
    
    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        // Horizontal lines (dB scale)
        val dbSteps = arrayOf(0, -20, -40, -60, -80)
        for (i in dbSteps.indices) {
            val y = height * i / (dbSteps.size - 1)
            canvas.drawLine(0f, y, width, y, gridPaint)
            
            // Draw dB labels
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 12f
            canvas.drawText("${dbSteps[i]}dB", width - 5f, y + 15f, textPaint)
        }
        
        // Vertical lines (frequency scale)
        for (i in 0..10) {
            val x = width * i / 10
            canvas.drawLine(x, 0f, x, height, gridPaint)
        }
    }
    

    
    private fun drawSpectrum(
        canvas: Canvas, 
        spectrum: FloatArray, 
        paint: Paint, 
        width: Float, 
        height: Float,
        label: String,
        fillArea: Boolean
    ) {
        if (spectrum.isEmpty()) return
        
        // Draw label
        textPaint.textSize = 12f
        textPaint.color = paint.color
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, 10f, if (label == "Input") 20f else 35f, textPaint)
        
        val path = Path()
        val (minFreq, maxFreq) = getFrequencyRange()
        val freqRange = maxFreq - minFreq
        
        // Find the frequency range to display
        val nyquist = sampleRate / 2f
        val freqBinWidth = nyquist / spectrum.size
        
        var firstPoint = true
        for (i in spectrum.indices) {
            val freq = i * freqBinWidth
            
            if (freq >= minFreq && freq <= maxFreq) {
                val x = ((freq - minFreq) / freqRange * width).coerceIn(0f, width)
                
                // Convert to dB scale
                val db = if (spectrum[i] > 0) {
                    20 * log10(spectrum[i].toDouble()).coerceIn(-80.0, 0.0)
                } else -80.0
                
                val normalized = (db + 80) / 80 // Map -80 to 0 dB to 0-1 range
                val y = height * 0.9f * (1 - normalized.toFloat().coerceIn(0f, 1f))
                
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        
        // Fill area under input spectrum for better visibility
        if (fillArea && inputSpectrum != null) {
            val fillPath = Path(path)
            fillPath.lineTo(width, height * 0.9f)
            fillPath.lineTo(0f, height * 0.9f)
            fillPath.close()
            
            val fillPaint = Paint(paint).apply {
                style = Paint.Style.FILL
                alpha = 20
            }
            canvas.drawPath(fillPath, fillPaint)
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun drawFilterResponse(
        canvas: Canvas,
        response: FloatArray,
        paint: Paint,
        width: Float,
        height: Float
    ) {
        if (response.isEmpty()) return
        
        val path = Path()
        val (minFreq, maxFreq) = getFrequencyRange()
        val freqRange = maxFreq - minFreq
        val nyquist = sampleRate / 2f
        val freqBinWidth = nyquist / response.size
        
        var firstPoint = true
        for (i in response.indices) {
            val freq = i * freqBinWidth
            
            if (freq >= minFreq && freq <= maxFreq) {
                val x = ((freq - minFreq) / freqRange * width).coerceIn(0f, width)
                
                // Filter response is already in linear scale 0-1
                val y = height * 0.9f * (1 - response[i])
                
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        
        canvas.drawPath(path, paint)
        
        // Draw -3dB line
        val db3 = 0.707f // -3dB point
        val y3db = height * 0.9f * (1 - db3)
        val db3Paint = Paint(paint).apply {
            pathEffect = null
            alpha = 100
            strokeWidth = 1f
        }
        canvas.drawLine(0f, y3db, width, y3db, db3Paint)
        
        // Label the -3dB line
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f
        canvas.drawText("-3dB", 5f, y3db - 5f, textPaint)
    }
    
    private fun generateFilterResponse(): FloatArray {
        val size = 256
        val response = FloatArray(size)
        val nyquist = sampleRate / 2f
        
        for (i in 0 until size) {
            val freq = (i.toFloat() / size) * nyquist
            response[i] = calculateButterworthResponse(freq)
        }
        
        return response
    }
    
    private fun calculateButterworthResponse(freq: Float): Float {
        val normalizedFreq = 2 * abs(freq - centerFrequency) / bandwidth
        
        return if (normalizedFreq <= 1) {
            // In passband - slight ripple for realism
            1f - 0.05f * normalizedFreq * normalizedFreq
        } else {
            // Outside passband - Butterworth rolloff
            val numSections = (filterOrder + 1) / 2
            1f / sqrt(1 + normalizedFreq.pow(2 * numSections)).toFloat()
        }
    }
    
    private fun getFrequencyRange(): Pair<Float, Float> {
        return if (showFullSpectrum) {
            // Show full spectrum
            Pair(0f, sampleRate / 2f)
        } else {
            // Zoom around center frequency (±2x bandwidth)
            val zoom = bandwidth * 2
            Pair(
                (centerFrequency - zoom).coerceAtLeast(0f),
                (centerFrequency + zoom).coerceAtMost(sampleRate / 2f)
            )
        }
    }
    
    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f
        
        // Frequency labels
        val (minFreq, maxFreq) = getFrequencyRange()
        for (i in 0..5) {
            val freq = minFreq + (maxFreq - minFreq) * i / 5
            val x = width * i / 5
            canvas.drawText("${freq.toInt()}Hz", x, height - 5f, textPaint)
        }
        
        // Legend with better positioning
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 14f
        
        var legendX = 15f
        val legendY = 25f
        
        // Input spectrum
        inputSpectrumPaint.style = Paint.Style.FILL
        canvas.drawCircle(legendX, legendY, 4f, inputSpectrumPaint)
        canvas.drawText("Input", legendX + 10f, legendY + 5f, textPaint)
        legendX += 70f
        
        // Filter response
        canvas.drawLine(legendX - 10f, legendY, legendX + 10f, legendY, filterResponsePaint)
        canvas.drawText("Filter", legendX + 15f, legendY + 5f, textPaint)
        legendX += 70f
        
        // Output spectrum
        outputSpectrumPaint.style = Paint.Style.FILL
        canvas.drawCircle(legendX, legendY, 4f, outputSpectrumPaint)
        canvas.drawText("Output", legendX + 10f, legendY + 5f, textPaint)
        
        // Reset styles
        inputSpectrumPaint.style = Paint.Style.STROKE
        outputSpectrumPaint.style = Paint.Style.STROKE
        
        // Add description
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Shows how the filter shapes the spectrum", width / 2, 50f, textPaint)
    }
    
    private fun drawViewMode(canvas: Canvas, width: Float, height: Float) {
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 14f
        val mode = if (showFullSpectrum) "Full Spectrum" else "Zoomed View"
        canvas.drawText("Tap to toggle: $mode", width - 10f, height - 15f, textPaint)
    }
} 