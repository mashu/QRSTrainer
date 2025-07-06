package com.so5km.qrstrainer

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.training.SequenceGenerator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var morseGenerator: MorseCodeGenerator
    private lateinit var progressTracker: ProgressTracker
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var settings: TrainingSettings
    
    private lateinit var sequenceDisplay: TextView
    private lateinit var buttonStart: Button
    private lateinit var buttonReveal: Button
    private lateinit var buttonNext: Button
    private lateinit var buttonReplay: Button
    private lateinit var levelDisplay: TextView
    private lateinit var streakDisplay: TextView
    
    private var currentSequence = ""
    private var isRevealed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializeComponents()
        createUI()
        setupListeners()
    }
    
    private fun initializeComponents() {
        morseGenerator = MorseCodeGenerator(this)
        progressTracker = ProgressTracker(this)
        sequenceGenerator = SequenceGenerator(progressTracker)
        settings = TrainingSettings.default()
    }
    
    private fun createUI() {
        // Create layout programmatically for simplicity
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(64, 64, 64, 64)
        }
        
        // Title
        val title = TextView(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "🎧 QRS Trainer - Morse Code Training"
            textSize = 20f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        
        // Sequence display
        sequenceDisplay = TextView(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "Ready to train! Press Start to begin."
            textSize = 18f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setBackgroundColor(0xFF000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 32, 32, 32)
            minHeight = 200
        }
        
        // Control buttons
        buttonStart = Button(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "START"
            textSize = 16f
        }
        
        buttonReveal = Button(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "REVEAL"
            textSize = 16f
            visibility = android.view.View.GONE
        }
        
        buttonNext = Button(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "NEXT"
            textSize = 16f
            visibility = android.view.View.GONE
        }
        
        buttonReplay = Button(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "REPLAY"
            textSize = 16f
            visibility = android.view.View.GONE
        }
        
        // Status displays
        levelDisplay = TextView(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "Level: ${progressTracker.getCurrentLevel()}"
            textSize = 16f
        }
        
        streakDisplay = TextView(this).apply {
            id = androidx.core.view.ViewCompat.generateViewId()
            text = "Streak: ${progressTracker.getCurrentStreak()}"
            textSize = 16f
        }
        
        // Add views to layout
        layout.addView(title)
        layout.addView(sequenceDisplay)
        layout.addView(buttonStart)
        layout.addView(buttonReveal)
        layout.addView(buttonNext)
        layout.addView(buttonReplay)
        layout.addView(levelDisplay)
        layout.addView(streakDisplay)
        
        // Create constraints programmatically
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(layout)
        
        // Title constraints
        constraintSet.connect(title.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, 32)
        constraintSet.connect(title.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(title.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // Sequence display constraints
        constraintSet.connect(sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.TOP, title.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // Button constraints
        constraintSet.connect(buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.TOP, sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        constraintSet.connect(buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.END, buttonReveal.id, androidx.constraintlayout.widget.ConstraintSet.START, 16)
        
        constraintSet.connect(buttonReveal.id, androidx.constraintlayout.widget.ConstraintSet.TOP, sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(buttonReveal.id, androidx.constraintlayout.widget.ConstraintSet.START, buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.END, 16)
        constraintSet.connect(buttonReveal.id, androidx.constraintlayout.widget.ConstraintSet.END, buttonNext.id, androidx.constraintlayout.widget.ConstraintSet.START, 16)
        
        constraintSet.connect(buttonNext.id, androidx.constraintlayout.widget.ConstraintSet.TOP, sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(buttonNext.id, androidx.constraintlayout.widget.ConstraintSet.START, buttonReveal.id, androidx.constraintlayout.widget.ConstraintSet.END, 16)
        constraintSet.connect(buttonNext.id, androidx.constraintlayout.widget.ConstraintSet.END, buttonReplay.id, androidx.constraintlayout.widget.ConstraintSet.START, 16)
        
        constraintSet.connect(buttonReplay.id, androidx.constraintlayout.widget.ConstraintSet.TOP, sequenceDisplay.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(buttonReplay.id, androidx.constraintlayout.widget.ConstraintSet.START, buttonNext.id, androidx.constraintlayout.widget.ConstraintSet.END, 16)
        constraintSet.connect(buttonReplay.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        // Status displays
        constraintSet.connect(levelDisplay.id, androidx.constraintlayout.widget.ConstraintSet.TOP, buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(levelDisplay.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        
        constraintSet.connect(streakDisplay.id, androidx.constraintlayout.widget.ConstraintSet.TOP, buttonStart.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 48)
        constraintSet.connect(streakDisplay.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        
        constraintSet.applyTo(layout)
        setContentView(layout)
    }
    
    private fun setupListeners() {
        buttonStart.setOnClickListener { startTraining() }
        buttonReveal.setOnClickListener { revealSequence() }
        buttonNext.setOnClickListener { nextSequence() }
        buttonReplay.setOnClickListener { replaySequence() }
    }
    
    private fun startTraining() {
        currentSequence = sequenceGenerator.generateSequence(
            settings.sequenceLength,
            progressTracker.getCurrentLevel()
        )
        isRevealed = false
        
        sequenceDisplay.text = "🎵 Listen to the sequence..."
        updateButtonVisibility(false, false, false, false)
        
        playSequence()
    }
    
    private fun playSequence() {
        lifecycleScope.launch {
            try {
                morseGenerator.playSequence(currentSequence, settings)
                
                // After playing, show reveal and replay options
                updateButtonVisibility(false, true, false, true)
                sequenceDisplay.text = "What did you hear? Press REVEAL to see the answer."
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Audio playback error: ${e.message}", Toast.LENGTH_LONG).show()
                updateButtonVisibility(true, false, false, false)
            }
        }
    }
    
    private fun revealSequence() {
        isRevealed = true
        sequenceDisplay.text = "📡 Sequence: $currentSequence"
        updateButtonVisibility(false, false, true, true)
        updateStats()
    }
    
    private fun nextSequence() {
        startTraining()
    }
    
    private fun replaySequence() {
        sequenceDisplay.text = "🎵 Replaying sequence..."
        playSequence()
    }
    
    private fun updateButtonVisibility(start: Boolean, reveal: Boolean, next: Boolean, replay: Boolean) {
        buttonStart.visibility = if (start) android.view.View.VISIBLE else android.view.View.GONE
        buttonReveal.visibility = if (reveal) android.view.View.VISIBLE else android.view.View.GONE
        buttonNext.visibility = if (next) android.view.View.VISIBLE else android.view.View.GONE
        buttonReplay.visibility = if (replay) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    private fun updateStats() {
        levelDisplay.text = "Level: ${progressTracker.getCurrentLevel()}"
        streakDisplay.text = "Streak: ${progressTracker.getCurrentStreak()}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::morseGenerator.isInitialized) {
            morseGenerator.release()
        }
    }
} 