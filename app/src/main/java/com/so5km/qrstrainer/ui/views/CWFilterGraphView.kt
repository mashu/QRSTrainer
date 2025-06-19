package com.so5km.qrstrainer.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.so5km.qrstrainer.R
import kotlin.math.*

/**
 * Custom view to display CW filter response characteristics
 * Shows how filter bandwidth and Q factor affect the signal
 */
class CWFilterGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Filter parameters
    var centerFrequency = 600f  // Hz
    var bandwidthHz = 500f
    var qFactor = 5.0f
    var backgroundNoise = 0.1f
    
    // Colors
    private var primaryFilterColor = Color.parseColor("#4CAF50")
    private var secondaryFilterColor = Color.parseColor("#2196F3") 
    private var combinedFilterColor = Color.parseColor("#FF9800")
    private var noiseColor = Color.parseColor("#F44336")
    private var gridColor = Color.parseColor("#E0E0E0")
    private var textColor = Color.parseColor("#424242")
    
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
        
        // Draw frequency response curves
        drawPrimaryFilter(canvas, width, graphHeight, graphTop)
        drawSecondaryFilter(canvas, width, graphHeight, graphTop)
        drawCombinedFilter(canvas, width, graphHeight, graphTop)
        drawNoiseFloor(canvas, width, graphHeight, graphTop)
        
        // Draw labels and legend
        drawLabels(canvas, width, height)
        drawLegend(canvas, width, height)
    }
    
    private fun drawGrid(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        // Vertical grid lines (frequency)
        for (i in 0..10) {
            val x = (i / 10f) * width
            canvas.drawLine(x, graphTop, x, graphTop + graphHeight, gridPaint)
        }
        
        // Horizontal grid lines (amplitude)
        for (i in 0..8) {
            val y = graphTop + (i / 8f) * graphHeight
            canvas.drawLine(0f, y, width, y, gridPaint)
        }
    }
    
    private fun drawPrimaryFilter(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        val path = Path()
        paint.apply {
            color = primaryFilterColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        
        val points = mutableListOf<PointF>()
        
        // Generate filter response curve (bandpass with Q factor)
        for (i in 0 until width.toInt()) {
            val freq = (i / width) * 2000f // 0-2000 Hz range
            val response = calculateFilterResponse(freq, centerFrequency, bandwidthHz, qFactor)
            val x = i.toFloat()
            val y = graphTop + graphHeight * (1f - response)
            points.add(PointF(x, y))
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun drawSecondaryFilter(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        val path = Path()
        paint.apply {
            color = secondaryFilterColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        
        // Secondary filter with different characteristics (wider, lower Q)
        val secondaryBandwidth = bandwidthHz * 1.5f
        val secondaryQ = qFactor * 0.7f
        
        for (i in 0 until width.toInt()) {
            val freq = (i / width) * 2000f
            val response = calculateFilterResponse(freq, centerFrequency, secondaryBandwidth, secondaryQ)
            val x = i.toFloat()
            val y = graphTop + graphHeight * (1f - response)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
        paint.pathEffect = null // Reset path effect
    }
    
    private fun drawCombinedFilter(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        val path = Path()
        paint.apply {
            color = combinedFilterColor
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        
        // Combined response (multiplication of both filters)
        val secondaryBandwidth = bandwidthHz * 1.5f
        val secondaryQ = qFactor * 0.7f
        
        for (i in 0 until width.toInt()) {
            val freq = (i / width) * 2000f
            val response1 = calculateFilterResponse(freq, centerFrequency, bandwidthHz, qFactor)
            val response2 = calculateFilterResponse(freq, centerFrequency, secondaryBandwidth, secondaryQ)
            val combinedResponse = response1 * response2
            val x = i.toFloat()
            val y = graphTop + graphHeight * (1f - combinedResponse)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun drawNoiseFloor(canvas: Canvas, width: Float, graphHeight: Float, graphTop: Float) {
        paint.apply {
            color = noiseColor
            strokeWidth = 2f
            style = Paint.Style.STROKE
            alpha = 100
        }
        
        val noiseY = graphTop + graphHeight * (1f - backgroundNoise)
        canvas.drawLine(0f, noiseY, width, noiseY, paint)
        paint.alpha = 255 // Reset alpha
    }
    
    private fun calculateFilterResponse(freq: Float, centerFreq: Float, bandwidth: Float, q: Float): Float {
        // Calculate bandpass filter response with Q factor
        val normalizedFreq = (freq - centerFreq) / (bandwidth / 2f)
        val response = 1f / sqrt(1f + (q * normalizedFreq).pow(2))
        
        // Add some ringing characteristics for high Q
        if (q > 10f && abs(normalizedFreq) < 2f) {
            val ringingFactor = 1f + 0.1f * sin(normalizedFreq * PI * 4).toFloat()
            return (response * ringingFactor).coerceIn(0f, 1f)
        }
        
        return response.coerceIn(0f, 1f)
    }
    
    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        // Frequency labels
        textPaint.textSize = 20f
        val freqLabels = arrayOf("0", "400", "800", "1200", "1600", "2000")
        for (i in freqLabels.indices) {
            val x = (i / 5f) * width
            val label = "${freqLabels[i]} Hz"
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth/2, height - 10f, textPaint)
        }
        
        // Amplitude label
        canvas.save()
        canvas.rotate(-90f, 30f, height/2)
        canvas.drawText("Response", 30f, height/2, textPaint)
        canvas.restore()
    }
    
    private fun drawLegend(canvas: Canvas, width: Float, height: Float) {
        textPaint.textSize = 18f
        val legendY = 30f
        var legendX = width - 200f
        
        // Primary filter
        paint.apply {
            color = primaryFilterColor
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(legendX, legendY, legendX + 30f, legendY, paint)
        canvas.drawText("Primary Filter", legendX + 35f, legendY + 5f, textPaint)
        
        // Secondary filter
        legendX = width - 200f
        val legendY2 = legendY + 25f
        paint.apply {
            color = secondaryFilterColor
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawLine(legendX, legendY2, legendX + 30f, legendY2, paint)
        canvas.drawText("Secondary Filter", legendX + 35f, legendY2 + 5f, textPaint)
        paint.pathEffect = null
        
        // Combined filter
        val legendY3 = legendY2 + 25f
        paint.apply {
            color = combinedFilterColor
            strokeWidth = 4f
        }
        canvas.drawLine(legendX, legendY3, legendX + 30f, legendY3, paint)
        canvas.drawText("Combined Response", legendX + 35f, legendY3 + 5f, textPaint)
        
        // Noise floor
        val legendY4 = legendY3 + 25f
        paint.apply {
            color = noiseColor
            strokeWidth = 2f
            alpha = 100
        }
        canvas.drawLine(legendX, legendY4, legendX + 30f, legendY4, paint)
        canvas.drawText("Noise Floor", legendX + 35f, legendY4 + 5f, textPaint)
        paint.alpha = 255
    }
    
    /**
     * Update filter parameters and redraw
     */
    fun updateFilter(bandwidth: Int, qFactor: Float, noiseLevel: Float) {
        this.bandwidthHz = bandwidth.toFloat()
        this.qFactor = qFactor
        this.backgroundNoise = noiseLevel
        invalidate()
    }
} 