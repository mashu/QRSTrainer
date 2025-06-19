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
    var bandwidthHz = 500f  // Primary filter bandwidth
    var secondaryBandwidthHz = 500f  // Secondary filter bandwidth
    var qFactor = 5.0f
    var backgroundNoise = 0.1f
    var primaryFilterOffset = 0f  // Hz offset from center
    var secondaryFilterOffset = 0f  // Hz offset from center
    
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
            val primaryCenterFreq = centerFrequency + primaryFilterOffset
            val response = calculateFilterResponse(freq, primaryCenterFreq, bandwidthHz, qFactor)
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
        
        // Secondary filter - independent bandwidth, same Q factor
        val secondaryBandwidth = secondaryBandwidthHz  // Independent bandwidth
        val secondaryQ = qFactor  // Same Q factor as primary
        
        for (i in 0 until width.toInt()) {
            val freq = (i / width) * 2000f
            val secondaryCenterFreq = centerFrequency + secondaryFilterOffset
            val response = calculateFilterResponse(freq, secondaryCenterFreq, secondaryBandwidth, secondaryQ)
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
        val secondaryBandwidth = secondaryBandwidthHz  // Independent bandwidth
        val secondaryQ = qFactor  // Same Q factor
        
        for (i in 0 until width.toInt()) {
            val freq = (i / width) * 2000f
            val primaryCenterFreq = centerFrequency + primaryFilterOffset
            val secondaryCenterFreq = centerFrequency + secondaryFilterOffset
            val response1 = calculateFilterResponse(freq, primaryCenterFreq, bandwidthHz, qFactor)
            val response2 = calculateFilterResponse(freq, secondaryCenterFreq, secondaryBandwidth, secondaryQ)
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
        // Clean filter response - flat top with smooth rolloff, no artifacts
        val freqDiff = abs(freq - centerFreq)
        val halfBandwidth = bandwidth / 2f
        
        return when {
            // Completely flat passband in the center
            freqDiff <= halfBandwidth * 0.8f -> {
                1f  // Perfectly flat, no ripples
            }
            // Smooth transition region - gradual rolloff
            freqDiff <= halfBandwidth * 1.2f -> {
                val transitionRatio = (freqDiff - halfBandwidth * 0.8f) / (halfBandwidth * 0.4f)
                1f - (transitionRatio * transitionRatio * 0.3f)  // Smooth quadratic transition
            }
            // Steeper rolloff controlled by Q factor
            else -> {
                val rolloffRatio = (freqDiff - halfBandwidth * 1.2f) / halfBandwidth
                val response = 1f / (1f + (rolloffRatio * q).pow(2))
                (response * 0.7f).coerceAtLeast(0.001f)  // Scale down and set minimum
            }
        }.coerceIn(0f, 1f)
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
        
        // Combined filter info
        textPaint.textSize = 16f
        textPaint.color = combinedFilterColor
        val combinedInfo = "Combined: ${calculateCombinedBandwidth().toInt()} Hz BW"
        canvas.drawText(combinedInfo, 10f, 30f, textPaint)
        textPaint.color = textColor // Reset color
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
    fun updateFilter(bandwidth: Int, secondaryBandwidth: Int, qFactor: Float, noiseLevel: Float, primaryOffset: Int = 0, secondaryOffset: Int = 0) {
        this.bandwidthHz = bandwidth.toFloat()
        this.secondaryBandwidthHz = secondaryBandwidth.toFloat()
        this.qFactor = qFactor
        this.backgroundNoise = noiseLevel
        this.primaryFilterOffset = primaryOffset.toFloat()
        this.secondaryFilterOffset = secondaryOffset.toFloat()
        invalidate()
    }
    
    /**
     * Calculate the effective combined bandwidth of the cascaded filters
     */
    private fun calculateCombinedBandwidth(): Float {
        // For cascaded filters, the combined response is narrower than either individual filter
        // This is an approximation - in reality it depends on the overlap and offset
        val primaryCenter = centerFrequency + primaryFilterOffset
        val secondaryCenter = centerFrequency + secondaryFilterOffset
        val centerOffset = abs(primaryCenter - secondaryCenter)
        
        return when {
            centerOffset < 50f -> {
                // Overlapping filters - combined BW is narrower than the narrower filter
                minOf(bandwidthHz, secondaryBandwidthHz) * 0.7f
            }
            centerOffset < 150f -> {
                // Partially overlapping - combined BW is between the two
                (bandwidthHz + secondaryBandwidthHz) * 0.6f
            }
            else -> {
                // Widely separated - combined BW approaches the sum
                bandwidthHz + secondaryBandwidthHz - centerOffset * 0.5f
            }
        }.coerceAtLeast(50f) // Minimum realistic bandwidth
    }
} 