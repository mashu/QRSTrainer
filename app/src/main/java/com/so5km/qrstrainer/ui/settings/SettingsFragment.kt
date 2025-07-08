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

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var audioManager: AudioManager
    
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
        
        initializeComponents()
        setupCollapsibleSections()
        setupThemeControls()
        setupAudioSliders()
        setupGroupSliders()
        setupTimingSliders()
        setupLevelSliders()
        setupCharacterSwitches()
        setupNoiseControls()
        setupButtons()
        observeSettings()
        setupAnimations()
        preventSliderScrolling()
    }
    
    private fun initializeComponents() {
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        audioManager = AudioManager(requireContext())
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
        
        // Noise Settings Section
        binding.cardNoiseSettings.setOnClickListener {
            toggleSection(binding.layoutNoiseSettings)
        }
        
        // Start with audio section expanded
        binding.layoutAudioSettings.visibility = View.VISIBLE
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
                // Expand with animation
                view.alpha = 0f
                view.translationY = -20f
                view.visibility = View.VISIBLE
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start()
            }
        }
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
                
                // Update settings
                updateSettings { it.copy(themeMode = newThemeMode) }
                
                // Refresh visualization colors before activity recreates
                binding.waveformVisualization.refreshThemeColors()
                binding.filterResponseView.refreshThemeColors()
                
                // Update theme immediately via MainActivity
                (requireActivity() as? com.so5km.qrstrainer.MainActivity)?.updateTheme(newThemeMode)
                
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
                binding.textCurrentLevelValue.text = getString(R.string.value_level, level)
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
        }
        
        // Numbers Switch
        binding.switchUseNumbers.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(useNumbers = isChecked) }
        }
        
        // Punctuation Switch
        binding.switchUsePunctuation.setOnCheckedChangeListener { _, isChecked ->
            updateSettings { it.copy(usePunctuation = isChecked) }
        }
        
        // Custom Characters
        binding.editCustomCharacters.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val customChars = binding.editCustomCharacters.text?.toString() ?: ""
                updateSettings { it.copy(customCharacterSet = customChars.uppercase()) }
            }
        }
    }
    
    private fun setupNoiseControls() {
        // Noise Settings
        binding.switchNoise.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNoiseControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSettings { it.copy(noiseEnabled = isChecked) }
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
            }
        }
        
        // Initialize filter visualization
        binding.filterResponseView.apply {
            setCenterFrequency(binding.sliderFrequency.value)
            setBandwidth(binding.sliderNoiseBandwidth.value)
            setShowRinging(true)
        }
    }
    
    private fun setupButtons() {
        binding.buttonTestAudio.setOnClickListener {
            testAudioSettings()
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
            
            // Level settings
            sliderCurrentLevel.value = settings.currentLevel.toFloat()
            switchLockLevel.isChecked = settings.lockLevel
            sliderCorrectToLevelUp.value = settings.correctAnswersToLevelUp.toFloat()
            sliderIncorrectToDrop.value = settings.incorrectAnswersToDropLevel.toFloat()
            
            textCurrentLevelValue.text = getString(R.string.value_level, settings.currentLevel)
            textCorrectToLevelUpValue.text = settings.correctAnswersToLevelUp.toString()
            textIncorrectToDropValue.text = settings.incorrectAnswersToDropLevel.toString()
            
            // Character settings
            switchUseProsigns.isChecked = settings.useProsigns
            switchUseNumbers.isChecked = settings.useNumbers
            switchUsePunctuation.isChecked = settings.usePunctuation
            editCustomCharacters.setText(settings.customCharacterSet)
            
            // Noise settings
            switchNoise.isChecked = settings.noiseEnabled
            layoutNoiseControls.visibility = if (settings.noiseEnabled) View.VISIBLE else View.GONE
            
            if (settings.noiseEnabled) {
                sliderNoiseVolume.value = (settings.noiseVolume * 100)
                sliderNoiseBandwidth.value = settings.noiseBandwidthHz
                textNoiseVolumeValue.text = getString(R.string.value_percent, (settings.noiseVolume * 100).toInt())
                textNoiseBandwidthValue.text = getString(R.string.value_hz, settings.noiseBandwidthHz.toInt())
            }
            
            // Update visualizations with current settings
            waveformVisualization.apply {
                setFrequency(settings.frequency.toFloat())
                setRiseTime(settings.riseTimeMs.toFloat())
            }
            
            filterResponseView.apply {
                setCenterFrequency(settings.frequency.toFloat())
                setBandwidth(settings.noiseBandwidthHz)
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
        val testSequence = "TEST"
        
        // Check if already playing - if so, stop it
        if (binding.buttonTestAudio.text == "Stop Test") {
            audioManager.stopPlayback()
            binding.buttonTestAudio.apply {
                isEnabled = true
                text = "Test Audio"
            }
            return
        }
        
        binding.buttonTestAudio.apply {
            isEnabled = true
            text = "Stop Test"
        }
        
        lifecycleScope.launch {
            try {
                audioManager.playSequence(testSequence, settings)
                
                // Re-enable button after test completes
                binding.buttonTestAudio.apply {
                    isEnabled = true
                    text = "Test Audio"
                }
            } catch (e: Exception) {
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
    
    private fun setupAnimations() {
        // Staggered entrance animations
        val cards = listOf(
            binding.cardAudioSettings,
            binding.cardGroupSettings,
            binding.cardTimingSettings,
            binding.cardLevelSettings,
            binding.cardCharacterSettings,
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
        val defaultSettings = TrainingSettings.default()
        storeViewModel.dispatch(AppAction.UpdateSettings(defaultSettings))
        
        // Show confirmation
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Settings reset to defaults",
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        audioManager.release()
        _binding = null
    }
}
