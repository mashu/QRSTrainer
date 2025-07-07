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
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    
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
        
        setupCollapsibleSections()
        setupSliders()
        observeSettings()
        setupAnimations()
    }
    
    private fun setupCollapsibleSections() {
        // Audio Settings Section
        binding.cardAudioSettings?.setOnClickListener {
            toggleSection(binding.layoutAudioSettings)
        }
        
        // Training Settings Section
        binding.cardTrainingSettings?.setOnClickListener {
            toggleSection(binding.layoutTrainingSettings)
        }
        
        // Noise Settings Section
        binding.cardNoiseSettings?.setOnClickListener {
            toggleSection(binding.layoutNoiseSettings)
        }
    }
    
    private fun toggleSection(layout: View?) {
        layout?.let { view ->
            val isExpanded = view.visibility == View.VISIBLE
            
            if (isExpanded) {
                // Collapse
                view.animate()
                    .alpha(0f)
                    .translationY(-20f)
                    .setDuration(200)
                    .withEndAction {
                        view.visibility = View.GONE
                    }
                    .start()
            } else {
                // Expand
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
        // Setup WPM, frequency, volume sliders
        // This will integrate with your existing TrainingSettings
        binding.sliderWpm?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // Update WPM setting
                binding.textWpmValue?.text = "${value.toInt()} WPM"
            }
        }
        
        binding.sliderFrequency?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // Update frequency setting
                binding.textFrequencyValue?.text = "${value.toInt()} Hz"
            }
        }
        
        binding.sliderVolume?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // Update volume setting
                binding.textVolumeValue?.text = "${value.toInt()}%"
            }
        }
        
        binding.switchNoise?.setOnCheckedChangeListener { _, isChecked ->
            // Update noise setting
            binding.layoutNoiseControls?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.buttonTestAudio?.setOnClickListener {
            // Test audio with current settings
        }
        
        binding.buttonResetSettings?.setOnClickListener {
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
        // Update sliders and labels with current settings
        binding.apply {
            sliderWpm?.value = settings.wpm.toFloat()
            sliderFrequency?.value = settings.frequency.toFloat()
            sliderVolume?.value = (settings.volume * 100).toInt().toFloat()
            
            textWpmValue?.text = "${settings.wpm} WPM"
            textFrequencyValue?.text = "${settings.frequency} Hz"
            textVolumeValue?.text = "${(settings.volume * 100).toInt()}%"
            
            switchNoise?.isChecked = settings.noiseEnabled
            layoutNoiseControls?.visibility = if (settings.noiseEnabled) View.VISIBLE else View.GONE
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
            card?.alpha = 0f
            card?.translationY = 100f
            card?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(300)
                ?.setStartDelay((index * 100).toLong())
                ?.start()
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
        // Reset to default settings
        val defaultSettings = TrainingSettings.default()
        updateUI(defaultSettings)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

