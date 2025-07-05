package com.so5km.qrstrainer.ui.listen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.speech.tts.TextToSpeech
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import com.so5km.qrstrainer.AppState
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.audio.AudioForegroundService
import com.so5km.qrstrainer.audio.SharedAudioState
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.databinding.FragmentListenBinding
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.ui.BaseAudioFragment
import java.util.Locale

class ListenFragment : BaseAudioFragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentListenBinding? = null
    private val binding get() = _binding!!

    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var textToSpeech: TextToSpeech
    
    private var currentSequence: String = ""
    private var isSequenceRevealed = false
    private var sequenceCount = 0
    private var isAutoRevealEnabled = true
    private var autoRevealDelayMs = 500L // Default delay before auto-reveal, will be overridden by settings
    
    // Delay progress tracking
    private var delayHandler: Handler? = null
    private var delayRunnable: Runnable? = null
    private var delayStartTime: Long = 0
    private var totalDelayTime: Long = 0
    
    // Training states
    enum class ListeningState {
        READY,      // Ready to start
        PLAYING,    // Audio playing
        PAUSED,     // Audio paused
        WAITING,    // Waiting for reveal after audio
        REVEALED    // Sequence revealed
    }
    
    private var currentState = ListeningState.READY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListenBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeComponents()
        setupUI()
        updateUIState()

        return root
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(requireContext(), this)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Update the TTS delay from settings
        val settings = getSettings()
        autoRevealDelayMs = settings.ttsDelayMs.toLong()
        updateDisplay()
        
        // Observe SharedAudioState
        observeSharedAudioState()
        
        // If audio was playing when we paused, update UI to show stopped state
        if (wasPlayingWhenPaused) {
            wasPlayingWhenPaused = false
            currentState = ListeningState.READY
            updateUIState()
        }
    }
    
    private fun observeSharedAudioState() {
        SharedAudioState.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (!isPlaying && currentState == ListeningState.PLAYING) {
                // Audio finished playing
                currentState = ListeningState.WAITING
                updateUIState()
                
                // Auto-reveal if enabled
                if (isAutoRevealEnabled) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentState == ListeningState.WAITING) {
                            revealSequence()
                        }
                    }, autoRevealDelayMs)
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Shutdown TextToSpeech
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        
        hideSequenceDelayProgress() // Clean up delay handler
        
        _binding = null
    }

    private fun initializeComponents() {
        // Initialize with dummy progress tracker just for sequence generation
        val progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
    }

    private fun setupUI() {
        updateDisplay()
        
        // Main controls
        binding.buttonStart.setOnClickListener {
            if (isSessionActive) {
                // If session is active, replay current sequence
                playCurrentSequence()
            } else {
                // Start a new session
                startSession()
            }
        }
        
        binding.buttonPause.setOnClickListener {
            pausePlayback()
        }
        
        binding.buttonStop.setOnClickListener {
            stopSession()
        }
        
        // Sequence controls
        binding.buttonReplay.setOnClickListener {
            playCurrentSequence()
        }
        
        binding.buttonReveal.setOnClickListener {
            revealSequence()
        }
        
        binding.buttonNext.setOnClickListener {
            startNewSequence()
        }
        
        // Auto-reveal switch
        binding.switchAutoReveal.setOnCheckedChangeListener { _, isChecked ->
            isAutoRevealEnabled = isChecked
            binding.textAutoRevealStatus.text = if (isChecked) 
                "Auto-reveal ON" 
            else 
                "Auto-reveal OFF"
            
            // Update instruction text based on auto-reveal state
            binding.textInstructions.text = if (isChecked) {
                "Continuous listening mode. Random sequences will play automatically."
            } else {
                "Listen to sequences and memorize them. After each sequence, the characters will be revealed."
            }
            
            // If currently in waiting state and auto-reveal is turned on, reveal immediately
            if (isChecked && currentState == ListeningState.WAITING) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == ListeningState.WAITING) {
                        revealSequence()
                    }
                }, 100) // Short delay to avoid UI glitches
            }
        }
    }

    private fun updateUIState() {
        when (currentState) {
            ListeningState.READY -> {
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                
                if (isSessionActive) {
                    binding.buttonStart.text = "▶ REPLAY"
                    binding.buttonStop.visibility = View.VISIBLE
                } else {
                    binding.buttonStart.text = "▶ START"
                }
                
                binding.textStatus.text = if (isSessionActive) "Session active - ready for next sequence" else "Ready to start listening"
                binding.textCurrentSequence.text = ""
                isSequenceRevealed = false
            }
            ListeningState.PLAYING -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.VISIBLE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = "Playing sequence..."
                binding.textCurrentSequence.text = ""
                isSequenceRevealed = false
            }
            ListeningState.PAUSED -> {
                binding.buttonStart.text = "▶ RESUME"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = "Paused - Press Resume to continue"
            }
            ListeningState.WAITING -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.VISIBLE
                binding.buttonReveal.visibility = View.VISIBLE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = "Sequence complete - Press Reveal to see characters"
            }
            ListeningState.REVEALED -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.VISIBLE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.VISIBLE
                binding.textStatus.text = "Sequence revealed - Press Next for a new sequence"
            }
        }
    }

    private fun updateDisplay() {
        val settings = getSettings()
        
        // Update speed display
        binding.textWpm.text = "${settings.speedWpm} WPM"
        
        // Update auto-reveal switch
        binding.switchAutoReveal.isChecked = isAutoRevealEnabled
        binding.textAutoRevealStatus.text = if (isAutoRevealEnabled) 
            "Auto-reveal ON" 
        else 
            "Auto-reveal OFF"
        
        // Update instruction text based on auto-reveal state
        binding.textInstructions.text = if (isAutoRevealEnabled) {
            "Continuous listening mode. Random sequences will play automatically."
        } else {
            "Listen to sequences and memorize them. After each sequence, the characters will be revealed."
        }
    }
    
    /**
     * Start a new listening session
     */
    private fun startSession() {
        isSessionActive = true
        sequenceCount = 0
        
        // Start continuous noise if enabled
        startContinuousNoise()
        
        // Start a new sequence
        startNewSequence()
    }
    
    /**
     * Stop the current session
     */
    private fun stopSession() {
        isSessionActive = false
        morseGenerator.stop()
        stopContinuousNoise()
        
        currentState = ListeningState.READY
        updateUIState()
    }
    
    /**
     * Start a new random sequence
     */
    private fun startNewSequence() {
        val settings = getSettings()
        
        // Generate a new random sequence
        currentSequence = sequenceGenerator.generateSequence(
            settings.kochLevel,
            settings.groupSizeMin,
            settings.groupSizeMax,
            settings.lettersOnlyMode
        )
        
        sequenceCount++
        
        // Update sequence count display
        binding.textSequenceCount.text = "Sequence: $sequenceCount"
        
        // Reset revealed state
        isSequenceRevealed = false
        binding.textCurrentSequence.text = ""
        
        // Play the sequence
        playCurrentSequence()
    }
    
    /**
     * Play the current sequence
     */
    private fun playCurrentSequence() {
        if (currentSequence.isEmpty()) {
            // Generate a sequence if none exists
            startNewSequence()
            return
        }
        
        // Update UI state
        currentState = ListeningState.PLAYING
        updateUIState()
        
        val settings = getSettings()
        
        // Play the sequence
        morseGenerator.playSequence(
            currentSequence,
            settings,
            source = "listen",
            startNoise = false,
            onComplete = {
                // This is called when playback completes
                Handler(Looper.getMainLooper()).post {
                    if (currentState == ListeningState.PLAYING) {
                        currentState = ListeningState.WAITING
                        updateUIState()
                        
                        // Auto-reveal if enabled
                        if (isAutoRevealEnabled) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (currentState == ListeningState.WAITING) {
                                    revealSequence()
                                }
                            }, autoRevealDelayMs)
                        }
                    }
                }
            }
        )
    }
    
    /**
     * Reveal the current sequence
     */
    private fun revealSequence() {
        if (currentSequence.isEmpty()) return
        
        // Update UI
        binding.textCurrentSequence.text = currentSequence
        isSequenceRevealed = true
        currentState = ListeningState.REVEALED
        updateUIState()
        
        // Speak the sequence if TTS is initialized
        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking.not()) {
            val settings = getSettings()
            
            // Prepare the text to speak
            val textToSpeak = currentSequence.replace(" ", ", ")
            
            // Set the speech rate
            textToSpeech.setSpeechRate(settings.ttsSpeechRate)
            
            // Speak the text
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "qrs_sequence")
            } else {
                @Suppress("DEPRECATION")
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null)
            }
            
            // If auto-reveal is enabled, start a new sequence after a delay
            if (isAutoRevealEnabled) {
                val nextSequenceDelay = 2000L + (currentSequence.length * 300L) // Base delay + time per character
                
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isSessionActive && isAutoRevealEnabled && currentState == ListeningState.REVEALED) {
                        startNewSequence()
                    }
                }, nextSequenceDelay)
            }
        }
    }
    
    /**
     * Show progress bar during sequence delay
     */
    private fun showSequenceDelayProgress(delayMs: Long) {
        // Cancel any existing delay
        hideSequenceDelayProgress()
        
        // Set up new delay
        delayHandler = Handler(Looper.getMainLooper())
        totalDelayTime = delayMs
        delayStartTime = System.currentTimeMillis()
        
        binding.layoutSequenceDelayOverlay.visibility = View.VISIBLE
        binding.progressSequenceDelay.max = 100
        binding.progressSequenceDelay.progress = 0
        
        delayRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() - delayStartTime
                val progress = ((elapsedTime.toFloat() / totalDelayTime) * 100).toInt()
                
                if (progress >= 100) {
                    binding.progressSequenceDelay.progress = 100
                    binding.layoutSequenceDelayOverlay.visibility = View.GONE
                    delayHandler = null
                } else {
                    binding.progressSequenceDelay.progress = progress
                    delayHandler?.postDelayed(this, 16) // Update at ~60fps
                }
            }
        }
        
        delayHandler?.post(delayRunnable!!)
    }
    
    /**
     * Hide sequence delay progress bar
     */
    private fun hideSequenceDelayProgress() {
        delayRunnable?.let { delayHandler?.removeCallbacks(it) }
        delayHandler = null
        binding.layoutSequenceDelayOverlay?.visibility = View.GONE
    }

    /**
     * TextToSpeech initialization callback
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to US English
            val result = textToSpeech.setLanguage(Locale.US)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("ListenFragment", "Language not supported")
            } else {
                android.util.Log.d("ListenFragment", "TextToSpeech initialized successfully")
            }
        } else {
            android.util.Log.e("ListenFragment", "TextToSpeech initialization failed")
        }
    }
    
    /**
     * Called when screen turns on
     */
    override fun onScreenOn() {
        super.onScreenOn()
        // Nothing additional needed
    }
    
    /**
     * Called when screen turns off
     */
    override fun onScreenOff() {
        super.onScreenOff()
        // Nothing additional needed
    }
} 