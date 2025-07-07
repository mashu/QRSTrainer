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
import kotlinx.coroutines.launch

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
        setupSliders()
        setupButtons()
        observeSettings()
        setupAnimations()
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
        
        // Training Settings Section
        binding.cardTrainingSettings.setOnClickListener {
            toggleSection(binding.layoutTrainingSettings)
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
    
    private fun setupSliders() {
        // Character Speed (WPM) Slider
        binding.sliderWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val wpm = value.toInt()
                binding.textWpmValue.text = "$wpm WPM"
                updateSettings { it.copy(wpm = wpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (binding.sliderEffectiveWpm.value > value) {
                    binding.sliderEffectiveWpm.value = value
                }
            }
        }
        
        // Effective Speed (Farnsworth) Slider
        binding.sliderEffectiveWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val effectiveWpm = value.toInt()
                binding.textEffectiveWpmValue.text = "$effectiveWpm WPM"
                updateSettings { it.copy(effectiveWpm = effectiveWpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (value > binding.sliderWpm.value) {
                    binding.sliderWpm.value = value
                }
            }
        }
        
        // Frequency Slider
        binding.sliderFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val frequency = value.toInt()
                binding.textFrequencyValue.text = "$frequency Hz"
                updateSettings { it.copy(frequency = frequency) }
            }
        }
        
        // Volume Slider
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val volume = value / 100f
                binding.textVolumeValue.text = "${value.toInt()}%"
                updateSettings { it.copy(volume = volume) }
            }
        }
        
        // Sequence Length Slider
        binding.sliderSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val length = value.toInt()
                binding.textSequenceLengthValue.text = "$length chars"
                updateSettings { it.copy(sequenceLength = length) }
            }
        }
        
        // Noise Settings
        binding.switchNoise.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNoiseControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSettings { it.copy(noiseEnabled = isChecked) }
        }
        
        binding.sliderNoiseVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val noiseVolume = value / 100f
                binding.textNoiseVolumeValue.text = "${value.toInt()}%"
                updateSettings { it.copy(noiseVolume = noiseVolume) }
            }
        }
    }
    
    private fun setupButtons() {
        binding.buttonTestAudio.setOnClickListener {
            testAudioSettings()
        }
        
        binding.buttonResetSettings.setOnClickListener {
            showResetConfirmation()
        }
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
            
            textWpmValue.text = "${settings.wpm} WPM"
            textEffectiveWpmValue.text = "${settings.effectiveWpm} WPM"
            textFrequencyValue.text = "${settings.frequency} Hz"
            textVolumeValue.text = "${(settings.volume * 100).toInt()}%"
            
            // Training settings
            sliderSequenceLength.value = settings.sequenceLength.toFloat()
            textSequenceLengthValue.text = "${settings.sequenceLength} chars"
            
            // Noise settings
            switchNoise.isChecked = settings.noiseEnabled
            layoutNoiseControls.visibility = if (settings.noiseEnabled) View.VISIBLE else View.GONE
            
            if (settings.noiseEnabled) {
                sliderNoiseVolume.value = (settings.noiseVolume * 100)
                textNoiseVolumeValue.text = "${(settings.noiseVolume * 100).toInt()}%"
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
        
        binding.buttonTestAudio.apply {
            isEnabled = false
            text = "Playing..."
        }
        
        lifecycleScope.launch {
            try {
                audioManager.playSequence(testSequence, settings)
                
                // Re-enable button after test
                kotlinx.coroutines.delay(testSequence.length * 1000L + 500) // Rough estimate
                
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
            binding.cardTrainingSettings,
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
