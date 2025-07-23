package com.so5km.qrstrainer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.audio.AudioManager
import com.so5km.qrstrainer.ui.components.settings.WaveformVisualizationView
import com.so5km.qrstrainer.ui.components.settings.FilterResponseView
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.so5km.qrstrainer.audio.NoiseGenerator

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var audioManager: AudioManager
    private lateinit var progressTracker: ProgressTracker
    private lateinit var noiseGenerator: NoiseGenerator
    
    private var testJob: Job? = null
    private var isContinuousTestRunning = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        audioManager = AudioManager(requireContext())
        progressTracker = ProgressTracker(requireContext())
        noiseGenerator = NoiseGenerator()
        
        initializeComponents()
        setupCollapsibleSections()
        setupThemeControls()
        setupAudioSliders()
        setupGroupSliders()
        setupTimingSliders()
        setupLevelSliders()
        setupCharacterSwitches()
        setupTtsControls()
        setupAutoRevealControls()
        setupNoiseControls()
        setupButtons()
        observeSettings()
        setupAnimations()
        preventSliderScrolling()
    }
    
    private fun initializeComponents() {
        // Components are already initialized in onViewCreated
        setupAudioSliders()
        setupGroupSliders()
        setupTimingSliders()
        setupLevelSliders()
        setupCharacterSwitches()
        setupTtsControls()
        setupNoiseControls()
        setupButtons()
        observeSettings()
        setupAnimations()
    }
    
    private fun setupCollapsibleSections() {
        // Audio Settings Section
        binding.cardAudioSettings.setOnClickListener {
            toggleSection(binding.layoutAudioSettings)
        }
        
        // Group Settings Section
        binding.cardGroupSettings.setOnClickListener {
            toggleSection(binding.layoutGroupSettings)
        }
        
        // Timing Settings Section
        binding.cardTimingSettings.setOnClickListener {
            toggleSection(binding.layoutTimingSettings)
        }
        
        // Level Settings Section
        binding.cardLevelSettings.setOnClickListener {
            toggleSection(binding.layoutLevelSettings)
        }
        
        // Character Settings Section
        binding.cardCharacterSettings.setOnClickListener {
            toggleSection(binding.layoutCharacterSettings)
        }
        
        // TTS Settings Section
        binding.cardTtsSettings.setOnClickListener {
            toggleSection(binding.layoutTtsSettings)
        }
        
        // Auto-Reveal Settings Section
        binding.cardAutorevealSettings.setOnClickListener {
            toggleSection(binding.layoutAutorevealSettings)
        }
        
        // Noise Settings Section
        binding.cardNoiseSettings.setOnClickListener {
            toggleSection(binding.layoutNoiseSettings)
        }
        
        // Trainer Audio Settings Section
        binding.cardTrainerAudioSettings.setOnClickListener {
            toggleSection(binding.layoutTrainerAudioSettings)
        }
        
        // Listen Audio Settings Section
        binding.cardListenAudioSettings.setOnClickListener {
            toggleSection(binding.layoutListenAudioSettings)
        }
        
        // Listen Groups Settings Section
        binding.cardListenGroupsSettings.setOnClickListener {
            toggleSection(binding.layoutListenGroupsSettings)
        }
        
        // Restore last opened section or default to audio
        restoreLastOpenedSection()
    }
    
    private fun toggleSection(layout: View?) {
        layout?.let { view ->
            val isExpanded = view.visibility == View.VISIBLE
            
            if (isExpanded) {
                // Collapse with animation
                view.animate()
                    .alpha(0f)
                    .translationY(-20f)
                    .setDuration(200)
                    .withEndAction {
                        view.visibility = View.GONE
                    }
                    .start()
            } else {
                // Collapse all other sections first
                collapseAllSections()
                
                // Expand with animation
                view.alpha = 0f
                view.translationY = -20f
                view.visibility = View.VISIBLE
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start()
                
                // Save the opened section
                saveLastOpenedSection(view)
            }
        }
    }
    
    private fun collapseAllSections() {
        val sections = listOf(
            binding.layoutAudioSettings,
            binding.layoutGroupSettings,
            binding.layoutTimingSettings,
            binding.layoutLevelSettings,
            binding.layoutCharacterSettings,
            binding.layoutTtsSettings,
            binding.layoutAutorevealSettings,
            binding.layoutNoiseSettings,
            binding.layoutTrainerAudioSettings,
            binding.layoutListenAudioSettings,
            binding.layoutListenGroupsSettings
        )
        
        sections.forEach { section ->
            if (section.visibility == View.VISIBLE) {
                section.visibility = View.GONE
            }
        }
    }
    
    private fun saveLastOpenedSection(layout: View) {
        val sectionName = when (layout) {
            binding.layoutAudioSettings -> "audio"
            binding.layoutGroupSettings -> "group"
            binding.layoutTimingSettings -> "timing"
            binding.layoutLevelSettings -> "level"
            binding.layoutCharacterSettings -> "character"
            binding.layoutTtsSettings -> "tts"
            binding.layoutAutorevealSettings -> "autoreveal"
            binding.layoutNoiseSettings -> "noise"
            binding.layoutTrainerAudioSettings -> "trainer_audio"
            binding.layoutListenAudioSettings -> "listen_audio"
            binding.layoutListenGroupsSettings -> "listen_groups"
            else -> "audio"
        }
        
        requireContext().getSharedPreferences("settings_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("last_opened_section", sectionName)
            .apply()
    }
    
    private fun restoreLastOpenedSection() {
        val prefs = requireContext().getSharedPreferences("settings_ui", android.content.Context.MODE_PRIVATE)
        val lastSection = prefs.getString("last_opened_section", "audio")
        
        val sectionToOpen = when (lastSection) {
            "audio" -> binding.layoutAudioSettings
            "group" -> binding.layoutGroupSettings
            "timing" -> binding.layoutTimingSettings
            "level" -> binding.layoutLevelSettings
            "character" -> binding.layoutCharacterSettings
            "tts" -> binding.layoutTtsSettings
            "autoreveal" -> binding.layoutAutorevealSettings
            "noise" -> binding.layoutNoiseSettings
            "trainer_audio" -> binding.layoutTrainerAudioSettings
            "listen_audio" -> binding.layoutListenAudioSettings
            "listen_groups" -> binding.layoutListenGroupsSettings
            else -> binding.layoutAudioSettings
        }
        
        sectionToOpen.visibility = View.VISIBLE
    }
    
    private fun setupThemeControls() {
        // Theme controls are now available via long press on Test Audio button
        // This provides theme selection without needing additional UI elements
    }
    
    private fun showThemeSelectionDialog() {
        val themeOptions = arrayOf("Light", "Dark", "System")
        val currentSettings = storeViewModel.settings.value
        val currentSelection = when (currentSettings.themeMode) {
            "light" -> 0
            "dark" -> 1
            "system" -> 2
            else -> 0
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Theme")
            .setSingleChoiceItems(themeOptions, currentSelection) { dialog, which ->
                val newThemeMode = when (which) {
                    0 -> "light"
                    1 -> "dark"
                    2 -> "system"
                    else -> "light"
                }
                
                // Update settings - this will trigger MainActivity's observer to recreate
                updateSettings { it.copy(themeMode = newThemeMode) }
                
                // Refresh visualization colors before activity recreates
                binding.waveformVisualization.refreshThemeColors()
                binding.filterResponseView.refreshThemeColors()
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setupAudioSliders() {
        // Character Speed (WPM) Slider
        binding.sliderWpm.addOnChangeListener { _, value, fromUser ->
            val wpm = value.toInt()
            binding.textWpmValue.text = getString(R.string.value_wpm, wpm)
            
            if (fromUser) {
                updateSettings { it.copy(wpm = wpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (binding.sliderEffectiveWpm.value > value) {
                    binding.sliderEffectiveWpm.value = value
                    // Update effective WPM text since it was changed programmatically
                    binding.textEffectiveWpmValue.text = getString(R.string.value_wpm, value.toInt())
                    updateSettings { it.copy(effectiveWpm = value.toInt()) }
                }
            }
        }
        
        // Effective Speed (Farnsworth) Slider
        binding.sliderEffectiveWpm.addOnChangeListener { _, value, fromUser ->
            val effectiveWpm = value.toInt()
            binding.textEffectiveWpmValue.text = getString(R.string.value_wpm, effectiveWpm)
            
            if (fromUser) {
                updateSettings { it.copy(effectiveWpm = effectiveWpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (value > binding.sliderWpm.value) {
                    binding.sliderWpm.value = value
                    // Update WPM text since it was changed programmatically
                    binding.textWpmValue.text = getString(R.string.value_wpm, value.toInt())
                    updateSettings { it.copy(wpm = value.toInt()) }
                }
            }
        }
        
        // Frequency Slider
        binding.sliderFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val frequency = value.toInt()
                binding.textFrequencyValue.text = getString(R.string.value_hz, frequency)
                updateSettings { it.copy(frequency = frequency) }
                
                // Update both visualizations
                binding.filterResponseView.setCenterFrequency(value)
                binding.waveformVisualization.setFrequency(value)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    updateSpectrumVisualization()
                }
            }
        }
        
        // Volume Slider
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val volume = value / 100f
                binding.textVolumeValue.text = getString(R.string.value_percent, value.toInt())
                updateSettings { it.copy(volume = volume) }
            }
        }
        
        // Rise Time Slider
        binding.sliderRiseTime.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textRiseTimeValue.text = getString(R.string.value_ms, value.toInt())
                updateSettings { it.copy(riseTimeMs = value.toDouble()) }
                
                // Update waveform visualization
                binding.waveformVisualization.setRiseTime(value)
            }
        }
        
        // Initialize waveform visualization
        binding.waveformVisualization.apply {
            setFrequency(binding.sliderFrequency.value)
            setRiseTime(binding.sliderRiseTime.value)
        }
    }
    
    private fun setupGroupSliders() {
        // Min Group Size
        binding.sliderMinGroupSize.addOnChangeListener { _, value, fromUser ->
            val minSize = value.toInt()
            binding.textMinGroupSizeValue.text = getString(R.string.value_chars, minSize)
            
            if (fromUser) {
                updateSettings { it.copy(minGroupSize = minSize) }
                
                // Ensure max group size is at least min size
                if (binding.sliderMaxGroupSize.value < value) {
                    binding.sliderMaxGroupSize.value = value
                    // Update max group size text since it was changed programmatically
                    binding.textMaxGroupSizeValue.text = getString(R.string.value_chars, value.toInt())
                    updateSettings { it.copy(maxGroupSize = value.toInt()) }
                }
            }
        }
        
        // Max Group Size
        binding.sliderMaxGroupSize.addOnChangeListener { _, value, fromUser ->
            val maxSize = value.toInt()
            binding.textMaxGroupSizeValue.text = getString(R.string.value_chars, maxSize)
            
            if (fromUser) {
                updateSettings { it.copy(maxGroupSize = maxSize) }
                
                // Ensure min group size doesn't exceed max size
                if (binding.sliderMinGroupSize.value > value) {
                    binding.sliderMinGroupSize.value = value
                    // Update min group size text since it was changed programmatically
                    binding.textMinGroupSizeValue.text = getString(R.string.value_chars, value.toInt())
                    updateSettings { it.copy(minGroupSize = value.toInt()) }
                }
            }
        }
        
        // Sequence Length (groups per sequence)
        binding.sliderSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val length = value.toInt()
                binding.textSequenceLengthValue.text = "$length groups"
                updateSettings { it.copy(sequenceLength = length) }
            }
        }
    }
    
    private fun setupTimingSliders() {
        // Number of Repeats
        binding.sliderNumberOfRepeats.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val repeats = value.toInt()
                binding.textNumberOfRepeatsValue.text = getString(R.string.value_repeats, repeats)
                updateSettings { it.copy(numberOfRepeats = repeats) }
            }
        }
        
        // Sequence Delay
        binding.sliderSequenceDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textSequenceDelayValue.text = getString(R.string.value_seconds, delaySeconds)
                updateSettings { it.copy(sequenceDelayMs = value.toLong()) }
            }
        }
        
        // Repeat Delay
        binding.sliderRepeatDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textRepeatDelayValue.text = getString(R.string.value_seconds, delaySeconds)
                updateSettings { it.copy(repeatDelayMs = value.toLong()) }
            }
        }
        
        // Group Delay
        binding.sliderGroupDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textGroupDelayValue.text = getString(R.string.value_seconds, delaySeconds)
                updateSettings { it.copy(groupDelayMs = value.toLong()) }
            }
        }
    }
    
    private fun setupLevelSliders() {
        // Current Level
        binding.sliderCurrentLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                
                // Calculate max level based on current character settings
                val currentSettings = storeViewModel.settings.value
                val maxLevel = TrainingSettings.calculateMaxLevel(
                    useNumbers = currentSettings.useNumbers,
                    usePunctuation = currentSettings.usePunctuation,
                    useProsigns = currentSettings.useProsigns,
                    customCharacterSet = currentSettings.customCharacterSet
                )
                
                binding.textCurrentLevelValue.text = getString(R.string.value_level, level) + " / $maxLevel"
                updateSettings { it.copy(currentLevel = level) }
            }
        }
        
        // Lock Level Switch
        binding.switchLockLevel.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(lockLevel = isChecked) }
        }
        
        // Correct Answers to Level Up
        binding.sliderCorrectToLevelUp.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.textCorrectToLevelUpValue.text = count.toString()
                updateSettings { it.copy(correctAnswersToLevelUp = count) }
            }
        }
        
        // Incorrect Answers to Drop Level
        binding.sliderIncorrectToDrop.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.textIncorrectToDropValue.text = count.toString()
                updateSettings { it.copy(incorrectAnswersToDropLevel = count) }
            }
        }
    }
    
    private fun setupCharacterSwitches() {
        // Prosigns Switch
        binding.switchUseProsigns.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(useProsigns = isChecked) }
            updateLevelSliderRange()
        }
        
        // Numbers Switch
        binding.switchUseNumbers.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(useNumbers = isChecked) }
            updateLevelSliderRange()
        }
        
        // Punctuation Switch
        binding.switchUsePunctuation.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(usePunctuation = isChecked) }
            updateLevelSliderRange()
        }
        
        // Custom Characters
        binding.editCustomCharacters.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val customChars = binding.editCustomCharacters.text?.toString() ?: ""
                updateSettings { it.copy(customCharacterSet = customChars.uppercase()) }
                updateLevelSliderRange()
            }
        }
    }
    
    /**
     * Update the level slider range when character settings change
     */
    private fun updateLevelSliderRange() {
        val currentSettings = storeViewModel.settings.value
        val maxLevel = TrainingSettings.calculateMaxLevel(
            useNumbers = currentSettings.useNumbers,
            usePunctuation = currentSettings.usePunctuation,
            useProsigns = currentSettings.useProsigns,
            customCharacterSet = currentSettings.customCharacterSet
        )
        
        // Update slider maximum
        binding.sliderCurrentLevel.valueTo = maxLevel.toFloat()
        
        // Ensure current level doesn't exceed new maximum
        val currentLevel = currentSettings.currentLevel.coerceIn(1, maxLevel)
        if (currentLevel != currentSettings.currentLevel) {
            binding.sliderCurrentLevel.value = currentLevel.toFloat()
            updateSettings { it.copy(currentLevel = currentLevel) }
        }
        
        // Update the display text to show current range
        binding.textCurrentLevelValue.text = getString(R.string.value_level, currentLevel) + " / $maxLevel"
    }
    
    private fun setupTtsControls() {
        // TTS settings are now controlled only from the Listen tab
        
        // TTS Volume Slider
        binding.sliderTtsVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val volume = value / 100f
                binding.textTtsVolumeValue.text = getString(R.string.value_percent, value.toInt())
                updateSettings { it.copy(ttsVolume = volume) }
            }
        }
        
        // TTS Speech Rate Slider
        binding.sliderTtsSpeechRate.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textTtsSpeechRateValue.text = "${String.format("%.1f", value)}x"
                updateSettings { it.copy(ttsSpeechRate = value) }
            }
        }
        
        // TTS Pitch Slider
        binding.sliderTtsPitch.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textTtsPitchValue.text = "${String.format("%.1f", value)}x"
                updateSettings { it.copy(ttsPitch = value) }
            }
        }
        
        // TTS Delay Slider
        binding.sliderTtsDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textTtsDelayValue.text = "${String.format("%.1f", delaySeconds)}s"
                updateSettings { it.copy(ttsDelayMs = value.toLong()) }
            }
        }
    }
    
    private fun setupAutoRevealControls() {
        // Auto-Reveal Enable Switch
        binding.switchAutoRevealEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(autoRevealEnabled = isChecked) }
        }
        
        // Speak Answer Switch 
        binding.switchSpeakAnswer.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(ttsSpeakInListenMode = isChecked) }
        }
        
        // Auto-Reveal Delay Slider
        binding.sliderAutoRevealDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textAutoRevealDelayValue.text = if (value == 0f) "0s" else "${String.format("%.1f", delaySeconds)}s"
                updateSettings { it.copy(autoRevealDelayMs = value.toLong()) }
            }
        }
        
        // Post-Reveal Delay Slider
        binding.sliderPostRevealDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textPostRevealDelayValue.text = if (value == 0f) "0s" else "${String.format("%.1f", delaySeconds)}s"
                updateSettings { it.copy(postRevealDelayMs = value.toLong()) }
            }
        }
        
        // Listen WPM Sliders
        binding.sliderListenWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val wpm = value.toInt()
                binding.textListenWpmValue.text = "$wpm WPM"
                updateSettings { it.copy(listenWpm = wpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (binding.sliderListenEffectiveWpm.value > value) {
                    binding.sliderListenEffectiveWpm.value = value
                    binding.textListenEffectiveWpmValue.text = "${value.toInt()} WPM"
                    updateSettings { it.copy(listenEffectiveWpm = value.toInt()) }
                }
            }
        }
        
        binding.sliderListenEffectiveWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val effectiveWpm = value.toInt()
                binding.textListenEffectiveWpmValue.text = "$effectiveWpm WPM"
                updateSettings { it.copy(listenEffectiveWpm = effectiveWpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (value > binding.sliderListenWpm.value) {
                    binding.sliderListenWpm.value = value
                    binding.textListenWpmValue.text = "${value.toInt()} WPM"
                    updateSettings { it.copy(listenWpm = value.toInt()) }
                }
            }
        }
        
        // Listen Group Size Sliders  
        binding.sliderListenMinGroupSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textListenMinGroupSizeValue.text = value.toInt().toString()
                // Ensure max is not less than min
                if (binding.sliderListenMaxGroupSize.value < value) {
                    binding.sliderListenMaxGroupSize.value = value
                }
                updateSettings { it.copy(listenMinGroupSize = value.toInt()) }
            }
        }
        
        binding.sliderListenMaxGroupSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textListenMaxGroupSizeValue.text = value.toInt().toString()
                // Ensure min is not greater than max
                if (binding.sliderListenMinGroupSize.value > value) {
                    binding.sliderListenMinGroupSize.value = value
                }
                updateSettings { it.copy(listenMaxGroupSize = value.toInt()) }
            }
        }
        
        // Listen Sequence Length Slider
        binding.sliderListenSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textListenSequenceLengthValue.text = value.toInt().toString()
                updateSettings { it.copy(listenSequenceLength = value.toInt()) }
            }
        }
        
        // Listen Number of Repeats Slider
        binding.sliderListenNumberOfRepeats.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textListenNumberOfRepeatsValue.text = value.toInt().toString()
                updateSettings { it.copy(listenNumberOfRepeats = value.toInt()) }
            }
        }
        
        // Listen Group Delay Slider
        binding.sliderListenGroupDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textListenGroupDelayValue.text = if (value == 0f) "0s" else "${String.format("%.1f", delaySeconds)}s"
                updateSettings { it.copy(listenGroupDelayMs = value.toLong()) }
            }
        }
        
        // Listen Repeat Delay Slider
        binding.sliderListenRepeatDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val delaySeconds = value / 1000f
                binding.textListenRepeatDelayValue.text = if (value == 0f) "0s" else "${String.format("%.1f", delaySeconds)}s"
                updateSettings { it.copy(listenRepeatDelayMs = value.toLong()) }
            }
        }
    }
    
    private fun setupNoiseControls() {
        // Noise Settings
        binding.switchNoise.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNoiseControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSettings { it.copy(noiseEnabled = isChecked) }
            
            // Update spectrum if visible
            if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                updateSpectrumVisualization()
            }
        }
        
        // QRM switch
        binding.switchQrm.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(qrmEnabled = isChecked) }
        }
        
        binding.sliderNoiseVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val noiseVolume = value / 100f
                binding.textNoiseVolumeValue.text = getString(R.string.value_percent, value.toInt())
                updateSettings { it.copy(noiseVolume = noiseVolume) }
            }
        }
        
        binding.sliderNoiseBandwidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val bandwidth = value.toInt()
                binding.textNoiseBandwidthValue.text = getString(R.string.value_hz, bandwidth)
                updateSettings { it.copy(noiseBandwidthHz = bandwidth.toFloat()) }
                
                // Update filter visualization
                binding.filterResponseView.setBandwidth(value)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    updateSpectrumVisualization()
                }
            }
        }
        
        // Filter type radio buttons
        binding.radioGroupFilterType.setOnCheckedChangeListener { _, checkedId ->
            val filterType = when (checkedId) {
                R.id.radio_butterworth -> "butterworth"
                R.id.radio_chebyshev -> "chebyshev"
                R.id.radio_elliptic -> "elliptic"
                else -> "butterworth"
            }
            updateSettings { it.copy(filterType = filterType) }
            binding.filterResponseView.setFilterType(filterType)
        }
        
        // Filter order slider
        binding.sliderFilterOrder.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val order = value.toInt()
                binding.textFilterOrderValue.text = order.toString()
                updateSettings { it.copy(filterOrder = order) }
                binding.filterResponseView.setFilterOrder(order)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    updateSpectrumVisualization()
                }
            }
        }
        
        // Initialize filter visualization
        binding.filterResponseView.apply {
            setCenterFrequency(binding.sliderFrequency.value)
            setBandwidth(binding.sliderNoiseBandwidth.value)
            setFilterType("butterworth")
            setFilterOrder(4)
            setShowRinging(true)
        }
    }
    
    private fun setupButtons() {
        binding.buttonTestAudio.setOnClickListener {
            testAudioSettings()
        }
        
        // Add continuous test mode switch handler
        binding.switchContinuousTest.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && isContinuousTestRunning) {
                // Stop continuous test if running
                stopContinuousTest()
            }
        }
        
        // Add theme selection on long press of test button (temporary)
        binding.buttonTestAudio.setOnLongClickListener {
            showThemeSelectionDialog()
            true
        }
        
        binding.buttonResetSettings.setOnClickListener {
            showResetConfirmation()
        }
        
        // Add info about theme selection
        binding.buttonTestAudio.tooltipText = "Tap to test audio, long press to change theme"
    }
    
    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.settings.collect { settings ->
                updateUI(settings)
            }
        }
    }
    
    private fun updateUI(settings: TrainingSettings) {
        // Prevent triggering listeners while updating UI
        binding.apply {
            // Audio settings
            sliderWpm.value = settings.wpm.toFloat()
            sliderEffectiveWpm.value = settings.effectiveWpm.toFloat()
            sliderFrequency.value = settings.frequency.toFloat()
            sliderVolume.value = (settings.volume * 100)
            sliderRiseTime.value = settings.riseTimeMs.toFloat()
            
            textWpmValue.text = getString(R.string.value_wpm, settings.wpm)
            textEffectiveWpmValue.text = getString(R.string.value_wpm, settings.effectiveWpm)
            textFrequencyValue.text = getString(R.string.value_hz, settings.frequency)
            textVolumeValue.text = getString(R.string.value_percent, (settings.volume * 100).toInt())
            textRiseTimeValue.text = getString(R.string.value_ms, settings.riseTimeMs.toInt())
            
            // Group settings
            sliderMinGroupSize.value = settings.minGroupSize.toFloat()
            sliderMaxGroupSize.value = settings.maxGroupSize.toFloat()
            sliderSequenceLength.value = settings.sequenceLength.toFloat()
            
            textMinGroupSizeValue.text = getString(R.string.value_chars, settings.minGroupSize)
            textMaxGroupSizeValue.text = getString(R.string.value_chars, settings.maxGroupSize)
            textSequenceLengthValue.text = "${settings.sequenceLength} groups"
            
            // Timing settings
            sliderNumberOfRepeats.value = settings.numberOfRepeats.toFloat()
            sliderSequenceDelay.value = settings.sequenceDelayMs.toFloat()
            sliderRepeatDelay.value = settings.repeatDelayMs.toFloat()
            sliderGroupDelay.value = settings.groupDelayMs.toFloat()
            
            textNumberOfRepeatsValue.text = getString(R.string.value_repeats, settings.numberOfRepeats)
            textSequenceDelayValue.text = getString(R.string.value_seconds, settings.sequenceDelayMs / 1000f)
            textRepeatDelayValue.text = getString(R.string.value_seconds, settings.repeatDelayMs / 1000f)
            textGroupDelayValue.text = getString(R.string.value_seconds, settings.groupDelayMs / 1000f)
            
            // Level settings - Dynamic max level based on available characters
            val maxLevel = TrainingSettings.calculateMaxLevel(
                useNumbers = settings.useNumbers,
                usePunctuation = settings.usePunctuation,
                useProsigns = settings.useProsigns,
                customCharacterSet = settings.customCharacterSet
            )
            
            // Update slider maximum to reflect actual available levels
            sliderCurrentLevel.valueTo = maxLevel.toFloat()
            sliderCurrentLevel.value = settings.currentLevel.toFloat()
            
            switchLockLevel.isChecked = settings.lockLevel
            sliderCorrectToLevelUp.value = settings.correctAnswersToLevelUp.toFloat()
            sliderIncorrectToDrop.value = settings.incorrectAnswersToDropLevel.toFloat()
            
            textCurrentLevelValue.text = getString(R.string.value_level, settings.currentLevel) + " / $maxLevel"
            textCorrectToLevelUpValue.text = settings.correctAnswersToLevelUp.toString()
            textIncorrectToDropValue.text = settings.incorrectAnswersToDropLevel.toString()
            
            // Character settings
            switchUseProsigns.isChecked = settings.useProsigns
            switchUseNumbers.isChecked = settings.useNumbers
            switchUsePunctuation.isChecked = settings.usePunctuation
            editCustomCharacters.setText(settings.customCharacterSet)
            
            // TTS settings (speak toggle is now in Listen tab only)
            sliderTtsVolume.value = (settings.ttsVolume * 100)
            sliderTtsSpeechRate.value = settings.ttsSpeechRate
            sliderTtsPitch.value = settings.ttsPitch
            sliderTtsDelay.value = settings.ttsDelayMs.toFloat()
            
            textTtsVolumeValue.text = getString(R.string.value_percent, (settings.ttsVolume * 100).toInt())
            textTtsSpeechRateValue.text = "${String.format("%.1f", settings.ttsSpeechRate)}x"
            textTtsPitchValue.text = "${String.format("%.1f", settings.ttsPitch)}x"
            textTtsDelayValue.text = "${String.format("%.1f", settings.ttsDelayMs / 1000f)}s"
            
            // Auto-Reveal settings
            switchAutoRevealEnabled.isChecked = settings.autoRevealEnabled
            switchSpeakAnswer.isChecked = settings.ttsSpeakInListenMode
            sliderAutoRevealDelay.value = settings.autoRevealDelayMs.toFloat()
            textAutoRevealDelayValue.text = if (settings.autoRevealDelayMs == 0L) "0s" else "${String.format("%.1f", settings.autoRevealDelayMs / 1000f)}s"
            
            sliderPostRevealDelay.value = settings.postRevealDelayMs.toFloat()
            textPostRevealDelayValue.text = if (settings.postRevealDelayMs == 0L) "0s" else "${String.format("%.1f", settings.postRevealDelayMs / 1000f)}s"
            
            // Listen Mode Settings
            sliderListenWpm.value = settings.listenWpm.toFloat()
            textListenWpmValue.text = "${settings.listenWpm} WPM"
            
            sliderListenEffectiveWpm.value = settings.listenEffectiveWpm.toFloat()
            textListenEffectiveWpmValue.text = "${settings.listenEffectiveWpm} WPM"
            
            sliderListenMinGroupSize.value = settings.listenMinGroupSize.toFloat()
            textListenMinGroupSizeValue.text = settings.listenMinGroupSize.toString()
            
            sliderListenMaxGroupSize.value = settings.listenMaxGroupSize.toFloat()
            textListenMaxGroupSizeValue.text = settings.listenMaxGroupSize.toString()
            
            sliderListenSequenceLength.value = settings.listenSequenceLength.toFloat()
            textListenSequenceLengthValue.text = settings.listenSequenceLength.toString()
            
            sliderListenNumberOfRepeats.value = settings.listenNumberOfRepeats.toFloat()
            textListenNumberOfRepeatsValue.text = settings.listenNumberOfRepeats.toString()
            
            sliderListenGroupDelay.value = settings.listenGroupDelayMs.toFloat()
            textListenGroupDelayValue.text = if (settings.listenGroupDelayMs == 0L) "0s" else "${String.format("%.1f", settings.listenGroupDelayMs / 1000f)}s"
            
            sliderListenRepeatDelay.value = settings.listenRepeatDelayMs.toFloat()
            textListenRepeatDelayValue.text = if (settings.listenRepeatDelayMs == 0L) "0s" else "${String.format("%.1f", settings.listenRepeatDelayMs / 1000f)}s"
            
            // Noise settings
            switchNoise.isChecked = settings.noiseEnabled
            layoutNoiseControls.visibility = if (settings.noiseEnabled) View.VISIBLE else View.GONE
            
            if (settings.noiseEnabled) {
                sliderNoiseVolume.value = (settings.noiseVolume * 100)
                sliderNoiseBandwidth.value = settings.noiseBandwidthHz
                textNoiseVolumeValue.text = getString(R.string.value_percent, (settings.noiseVolume * 100).toInt())
                textNoiseBandwidthValue.text = getString(R.string.value_hz, settings.noiseBandwidthHz.toInt())
                
                // Set QRM switch
                switchQrm.isChecked = settings.qrmEnabled
                
                // Set filter type radio button
                when (settings.filterType) {
                    "butterworth" -> radioButterworth.isChecked = true
                    "chebyshev" -> radioChebyshev.isChecked = true
                    "elliptic" -> radioElliptic.isChecked = true
                }
                
                // Set filter order
                sliderFilterOrder.value = settings.filterOrder.toFloat()
                textFilterOrderValue.text = settings.filterOrder.toString()
            }
            
            // Update visualizations with current settings
            waveformVisualization.apply {
                setFrequency(settings.frequency.toFloat())
                setRiseTime(settings.riseTimeMs.toFloat())
            }
            
            filterResponseView.apply {
                setCenterFrequency(settings.frequency.toFloat())
                setBandwidth(settings.noiseBandwidthHz)
                setFilterType(settings.filterType)
                setFilterOrder(settings.filterOrder)
            }
        }
    }
    
    private fun updateSettings(update: (TrainingSettings) -> TrainingSettings) {
        val currentSettings = storeViewModel.settings.value
        val newSettings = update(currentSettings)
        val validatedSettings = TrainingSettings.validate(newSettings)
        storeViewModel.dispatch(AppAction.UpdateSettings(validatedSettings))
    }
    
    private fun testAudioSettings() {
        val settings = storeViewModel.settings.value
        
        // Check if already playing - if so, stop it
        if (binding.buttonTestAudio.text == "Stop Test") {
            stopAllTests()
            return
        }
        
        binding.buttonTestAudio.apply {
            isEnabled = true
            text = "Stop Test"
        }
        
        // Start continuous noise if enabled for testing
        if (settings.noiseEnabled) {
            audioManager.startContinuousNoise(settings)
        }
        
        if (binding.switchContinuousTest.isChecked) {
            // Start continuous character test
            startContinuousCharacterTest(settings)
        } else {
            // Normal test sequence
            testJob = lifecycleScope.launch {
                try {
                    audioManager.playSequence("TEST", settings)
                    
                    // Stop noise and re-enable button after test completes
                    audioManager.stopContinuousNoise()
                    binding.buttonTestAudio.apply {
                        isEnabled = true
                        text = "Test Audio"
                    }
                } catch (e: Exception) {
                    // Stop noise on error
                    audioManager.stopContinuousNoise()
                    binding.buttonTestAudio.apply {
                        isEnabled = true
                        text = "Test Failed"
                    }
                    
                    // Reset text after delay
                    kotlinx.coroutines.delay(2000)
                    binding.buttonTestAudio.text = "Test Audio"
                }
            }
        }
    }
    
    private fun startContinuousCharacterTest(settings: TrainingSettings) {
        isContinuousTestRunning = true
        
        // Get a character from the current level
        val levelChars = progressTracker.getCharactersForLevel(settings.currentLevel)
        val testChar = if (levelChars.isNotEmpty()) {
            levelChars.random().toString()
        } else {
            "E" // Default to E if no characters at level
        }
        
        // Show the current test character
        binding.textCurrentTestCharacter.apply {
            text = "Testing: $testChar"
            visibility = View.VISIBLE
        }
        
        // Show spectrum visualization
        binding.spectrumVisualizationView.apply {
            visibility = View.VISIBLE
            setFilterParameters(settings.frequency.toFloat(), settings.noiseBandwidthHz, settings.filterOrder)
            setSampleRate(44100)
        }
        
        testJob = lifecycleScope.launch {
            while (isContinuousTestRunning && binding.switchContinuousTest.isChecked) {
                try {
                    // Play the character
                    audioManager.playSequence(testChar, settings)
                    
                    // Update spectrum visualization if available
                    withContext(Dispatchers.Main) {
                        updateSpectrumVisualization()
                    }
                    
                    // Short pause between repetitions
                    delay(500)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsFragment", "Error in continuous test", e)
                    break
                }
            }
            
            // Clean up when loop exits
            stopAllTests()
        }
    }
    
    private fun updateSpectrumVisualization() {
        val settings = storeViewModel.settings.value
        
        // Generate test spectrum data
        val (inputSpectrum, outputSpectrum) = noiseGenerator.generateTestSpectrum(
            settings.frequency.toFloat(),
            settings.noiseBandwidthHz,
            settings.filterOrder,
            settings.noiseEnabled
        )
        
        // Update visualization
        binding.spectrumVisualizationView.apply {
            setFilterParameters(settings.frequency.toFloat(), settings.noiseBandwidthHz, settings.filterOrder)
            updateSpectrum(inputSpectrum, outputSpectrum)
        }
    }
    
    private fun stopContinuousTest() {
        isContinuousTestRunning = false
        testJob?.cancel()
        testJob = null
    }
    
    private fun stopAllTests() {
        stopContinuousTest()
        audioManager.stopPlayback()
        audioManager.stopContinuousNoise()
        binding.buttonTestAudio.apply {
            isEnabled = true
            text = "Test Audio"
        }
        // Hide the test character display
        binding.textCurrentTestCharacter.visibility = View.GONE
        // Hide spectrum visualization
        binding.spectrumVisualizationView.visibility = View.GONE
    }
    
    private fun setupAnimations() {
        // Staggered entrance animations
        val cards = listOf(
            binding.cardAudioSettings,
            binding.cardGroupSettings,
            binding.cardTimingSettings,
            binding.cardLevelSettings,
            binding.cardCharacterSettings,
            binding.cardTtsSettings,
            binding.cardNoiseSettings
        )
        
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 100f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
        }
    }
    
    private fun preventSliderScrolling() {
        // Collect all sliders in the layout
        val allSliders = listOf(
            // Audio sliders
            binding.sliderWpm,
            binding.sliderEffectiveWpm,
            binding.sliderFrequency,
            binding.sliderVolume,
            binding.sliderRiseTime,
            // Group sliders
            binding.sliderMinGroupSize,
            binding.sliderMaxGroupSize,
            binding.sliderSequenceLength,
            // Timing sliders
            binding.sliderNumberOfRepeats,
            binding.sliderSequenceDelay,
            binding.sliderRepeatDelay,
            binding.sliderGroupDelay,
            // Level sliders
            binding.sliderCurrentLevel,
            binding.sliderCorrectToLevelUp,
            binding.sliderIncorrectToDrop,
            // TTS sliders
            binding.sliderTtsVolume,
            binding.sliderTtsSpeechRate,
            binding.sliderTtsPitch,
            binding.sliderTtsDelay,
            // Noise sliders
            binding.sliderNoiseVolume,
            binding.sliderNoiseBandwidth
        )
        
        // Apply focus prevention to all sliders
        allSliders.forEach { slider ->
            slider.isFocusable = false
            slider.isFocusableInTouchMode = false
        }
    }
    
    private fun showResetConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset Settings")
            .setMessage("Are you sure you want to reset all settings to defaults?")
            .setPositiveButton("Reset") { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun resetToDefaults() {
        storeViewModel.dispatch(AppAction.ResetSettings)
        
        // Show confirmation
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Settings reset to defaults",
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }
    
    override fun onDestroyView() {
        stopAllTests() // Make sure to stop any running tests
        super.onDestroyView()
        _binding = null
    }
}
