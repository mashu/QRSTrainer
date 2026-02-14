package com.so5km.qrstrainer.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * Centralized animation utilities to eliminate UI animation redundancy
 */
object AnimationUtils {
    
    /**
     * Standard entrance animation for cards/views
     */
    fun animateEntranceStaggered(
        views: List<View>,
        duration: Long = 300L,
        staggerDelay: Long = 100L,
        fromTranslationY: Float = 100f
    ) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = fromTranslationY
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(index * staggerDelay)
                .start()
        }
    }
    
    /**
     * Standard entrance animation for a single view
     */
    fun animateEntrance(
        view: View,
        duration: Long = 300L,
        fromTranslationY: Float = 100f,
        delay: Long = 0L
    ) {
        view.alpha = 0f
        view.translationY = fromTranslationY
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(delay)
            .start()
    }
    
    /**
     * Slide in from right animation
     */
    fun slideInFromRight(
        view: View,
        duration: Long = 200L,
        delay: Long = 0L
    ) {
        view.translationX = view.width.toFloat()
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .start()
    }
    
    /**
     * Slide in from left animation
     */
    fun slideInFromLeft(
        view: View,
        duration: Long = 200L,
        delay: Long = 0L
    ) {
        view.translationX = -view.width.toFloat()
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .start()
    }
    
    /**
     * Pulse animation for feedback
     */
    fun pulseAnimation(
        view: View,
        scaleFactor: Float = 1.1f,
        duration: Long = 300L
    ) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleFactor, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleFactor, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            setDuration(duration)
            interpolator = AccelerateDecelerateInterpolator()
        }.start()
    }
    
    /**
     * Shake animation for errors
     */
    fun shakeAnimation(
        view: View,
        amplitude: Float = 20f,
        duration: Long = 400L
    ) {
        val shake = ObjectAnimator.ofFloat(
            view, "translationX", 0f, -amplitude, amplitude, -amplitude, amplitude, 0f
        ).apply {
            setDuration(duration)
            interpolator = AccelerateDecelerateInterpolator()
        }
        shake.start()
    }
    
    /**
     * Fade transition between views
     */
    fun crossFade(
        viewOut: View,
        viewIn: View,
        duration: Long = 200L,
        onComplete: (() -> Unit)? = null
    ) {
        viewOut.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                viewOut.visibility = View.GONE
                viewIn.alpha = 0f
                viewIn.visibility = View.VISIBLE
                viewIn.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .withEndAction { onComplete?.invoke() }
                    .start()
            }
            .start()
    }
    
    /**
     * Expandable section animation
     */
    fun expandSection(
        view: View,
        duration: Long = 200L
    ) {
        view.alpha = 0f
        view.translationY = -20f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .start()
    }
    
    /**
     * Collapse section animation
     */
    fun collapseSection(
        view: View,
        duration: Long = 200L,
        onComplete: (() -> Unit)? = null
    ) {
        view.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(duration)
            .withEndAction {
                view.visibility = View.GONE
                onComplete?.invoke()
            }
            .start()
    }
    
    /**
     * Continuous pulse animation (e.g., for loading states)
     */
    fun startContinuousPulse(
        view: View,
        scaleFactor: Float = 1.1f,
        duration: Long = 600L
    ): Animator {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleFactor, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleFactor, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
        }
        
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            setDuration(duration)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
    
    /**
     * Progress bar animation
     */
    @Suppress("UNUSED_PARAMETER")
    fun animateProgress(
        progressView: View,
        fromProgress: Int,
        toProgress: Int,
        duration: Long = 1000L,
        onUpdate: ((Int) -> Unit)? = null
    ) {
        val animator = ObjectAnimator.ofInt(fromProgress, toProgress)
        animator.duration = duration
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            onUpdate?.invoke(progress)
        }
        animator.start()
    }
} 