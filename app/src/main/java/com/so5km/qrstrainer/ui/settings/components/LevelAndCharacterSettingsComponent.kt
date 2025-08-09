package com.so5km.qrstrainer.ui.settings.components

import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding
import android.widget.TextView
import com.so5km.qrstrainer.data.buildMasterSequence

/**
 * Component that handles level progression and character set settings
 */
class LevelAndCharacterSettingsComponent(
    private val binding: FragmentSettingsBinding,
    private val onSettingsUpdate: ((TrainingSettings) -> TrainingSettings) -> Unit
) {
    
    /**
     * Get characters for current level using progressive Koch method
     * Level 1: 2 chars, Level 2: 3 chars, Level 3: 4 chars, etc.
     * Characters are drawn from a master sequence based on enabled settings
     */
    private fun getCharactersForLevel(level: Int, settings: TrainingSettings): List<Char> {
        val masterSequence = settings.buildMasterSequence()
        val characterCount = (level + 1).coerceAtLeast(1)
        return masterSequence.take(characterCount.coerceAtMost(masterSequence.size))
    }
    
    /**
     * Format characters for display with proper spacing
     */
    private fun formatCharactersForDisplay(characters: List<Char>): String {
        return characters.joinToString("  ") { char ->
            when (char) {
                '<' -> "AR"  // AR prosign
                '>' -> "SK"  // SK prosign  
                '@' -> "AS"  // AS prosign
                else -> char.toString()
            }
        }
    }
    
    /**
     * Update character display for a specific level
     */
    private fun updateCharacterDisplay(level: Int, settings: TrainingSettings, isListenMode: Boolean = false) {
        val characters = getCharactersForLevel(level, settings)
        val displayText = formatCharactersForDisplay(characters)
        
        if (isListenMode) {
            binding.textListenLevelCharacters.text = displayText
        } else {
            binding.textCurrentLevelCharacters.text = displayText
        }
    }
    
    // Current settings - will be updated by parent fragment
    private var currentSettings: TrainingSettings = TrainingSettings.default()
    
    /**
     * Set current settings (called by parent fragment)
     */
    fun setCurrentSettings(settings: TrainingSettings) {
        currentSettings = settings
    }
    
    /**
     * Get current settings
     */
    private fun getCurrentSettings(): TrainingSettings {
        return currentSettings
    }
    
    fun setup() {
        setupLevelSliders()
        setupCharacterSwitches()
        setupTrainerBehaviorSwitches()
    }
    
    private fun setupLevelSliders() {
        // Current Level (Trainer)
        binding.sliderCurrentLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                val currentSettings = getCurrentSettings()
                
                // Calculate max level based on current character settings
                val maxLevel = TrainingSettings.calculateMaxLevel(currentSettings)
                
                binding.textCurrentLevelValue.text = binding.root.context.getString(R.string.value_level, level) + " / $maxLevel"
                
                // Update character display for trainer level
                updateCharacterDisplay(level, currentSettings, isListenMode = false)
                
                onSettingsUpdate { it.copy(currentLevel = level) }
            }
        }
        
        // Current Listen Level
        binding.sliderListenCurrentLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                val currentSettings = getCurrentSettings()
                
                // Calculate max level based on current character settings
                val maxLevel = TrainingSettings.calculateMaxLevel(currentSettings)
                
                binding.textListenCurrentLevelValue.text = binding.root.context.getString(R.string.value_level, level) + " / $maxLevel"
                
                // Update character display for listen level
                updateCharacterDisplay(level, currentSettings, isListenMode = true)
                
                onSettingsUpdate { it.copy(listenCurrentLevel = level) }
            }
        }
        
        // Lock Level Switch
        binding.switchLockLevel.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(lockLevel = isChecked) }
        }
        
        // Correct Answers to Level Up
        binding.sliderCorrectToLevelUp.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.textCorrectToLevelUpValue.text = count.toString()
                onSettingsUpdate { it.copy(correctAnswersToLevelUp = count) }
            }
        }
        
        // Incorrect Answers to Drop Level
        binding.sliderIncorrectToDrop.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.textIncorrectToDropValue.text = count.toString()
                onSettingsUpdate { it.copy(incorrectAnswersToDropLevel = count) }
            }
        }
        
        // Listen Sequences to Level Up
        binding.sliderListenSequencesToLevelUp.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val count = value.toInt()
                binding.textListenSequencesToLevelUpValue.text = if (count == 0) "Manual" else count.toString()
                onSettingsUpdate { it.copy(listenSequencesToLevelUp = count) }
            }
        }
    }
    
    private fun setupCharacterSwitches() {
        // Prosigns Switch
        binding.switchUseProsigns.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(useProsigns = isChecked) }
            updateLevelSliderRange()
            // Update character displays for both trainer and listen levels using post()
            binding.sliderCurrentLevel.post {
                val currentSettings = getCurrentSettings()
                updateCharacterDisplay(currentSettings.currentLevel, currentSettings, isListenMode = false)
                updateCharacterDisplay(currentSettings.listenCurrentLevel, currentSettings, isListenMode = true)
            }
        }
        
        // Numbers Switch
        binding.switchUseNumbers.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(useNumbers = isChecked) }
            updateLevelSliderRange()
            // Update character displays for both trainer and listen levels using post()
            binding.sliderCurrentLevel.post {
                val currentSettings = getCurrentSettings()
                updateCharacterDisplay(currentSettings.currentLevel, currentSettings, isListenMode = false)
                updateCharacterDisplay(currentSettings.listenCurrentLevel, currentSettings, isListenMode = true)
            }
        }
        
        // Punctuation Switch
        binding.switchUsePunctuation.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(usePunctuation = isChecked) }
            updateLevelSliderRange()
            // Update character displays for both trainer and listen levels using post()
            binding.sliderCurrentLevel.post {
                val currentSettings = getCurrentSettings()
                updateCharacterDisplay(currentSettings.currentLevel, currentSettings, isListenMode = false)
                updateCharacterDisplay(currentSettings.listenCurrentLevel, currentSettings, isListenMode = true)
            }
        }
        
        // Custom Characters
        binding.editCustomCharacters.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val customChars = binding.editCustomCharacters.text?.toString() ?: ""
                onSettingsUpdate { it.copy(customCharacterSet = customChars.uppercase()) }
                updateLevelSliderRange()
                // Update character displays for both trainer and listen levels using post()
                binding.sliderCurrentLevel.post {
                    val currentSettings = getCurrentSettings()
                    updateCharacterDisplay(currentSettings.currentLevel, currentSettings, isListenMode = false)
                    updateCharacterDisplay(currentSettings.listenCurrentLevel, currentSettings, isListenMode = true)
                }
            }
        }
    }
    
    private fun setupTrainerBehaviorSwitches() {
        // Fail on First Incorrect Switch
        binding.switchFailOnFirstIncorrect.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(failOnFirstIncorrect = isChecked) }
        }
    }

    // Alphabet preset selection is handled in SettingsFragment via dropdown
    
    /**
     * Update the level slider range when character settings change
     */
    private fun updateLevelSliderRange() {
        val currentSettings = getCurrentSettings()
        val maxLevel = TrainingSettings.calculateMaxLevel(currentSettings)
        
        // Ensure current levels don't exceed new maximum
        val constrainedTrainerLevel = currentSettings.currentLevel.coerceIn(1, maxLevel)
        val constrainedListenLevel = currentSettings.listenCurrentLevel.coerceIn(1, maxLevel)
        
        // Safely update both sliders using the safe update method
        safelyUpdateSlider(
            slider = binding.sliderCurrentLevel,
            newValue = constrainedTrainerLevel.toFloat(),
            newValueTo = maxLevel.toFloat(),
            textView = binding.textCurrentLevelValue,
            displayText = binding.root.context.getString(R.string.value_level, constrainedTrainerLevel) + " / $maxLevel"
        )
        
        safelyUpdateSlider(
            slider = binding.sliderListenCurrentLevel,
            newValue = constrainedListenLevel.toFloat(),
            newValueTo = maxLevel.toFloat(),
            textView = binding.textListenCurrentLevelValue,
            displayText = binding.root.context.getString(R.string.value_level, constrainedListenLevel) + " / $maxLevel"
        )
        
        // Update settings if levels were constrained
        if (constrainedTrainerLevel != currentSettings.currentLevel || constrainedListenLevel != currentSettings.listenCurrentLevel) {
            // Use post() to avoid conflicts with ongoing layout operations
            binding.sliderCurrentLevel.post {
                onSettingsUpdate { it.copy(
                    currentLevel = constrainedTrainerLevel,
                    listenCurrentLevel = constrainedListenLevel
                )}
            }
        }
    }
    
    /**
     * Safely update a slider with validation to prevent crashes
     */
    private fun safelyUpdateSlider(
        slider: com.google.android.material.slider.Slider,
        newValue: Float,
        newValueTo: Float,
        textView: TextView,
        displayText: String
    ) {
        // Use post() to ensure this runs after layout pass
        slider.post {
            try {
                // Always set valueTo BEFORE value to prevent validation errors
                slider.valueTo = newValueTo
                
                // Ensure value is within the new range
                val constrainedValue = newValue.coerceIn(slider.valueFrom, newValueTo)
                slider.value = constrainedValue
                
                // Update display text
                textView.text = displayText
            } catch (e: Exception) {
                // Fallback: reset to safe values
                slider.valueTo = newValueTo
                slider.value = 1f
                textView.text = "Level 1 / ${newValueTo.toInt()}"
                android.util.Log.e("LevelComponent", "Slider update failed, reset to safe values", e)
            }
        }
    }
    
    fun updateUI(settings: TrainingSettings) {
        binding.apply {
            // Level settings - Dynamic max level based on available characters
            val maxLevel = TrainingSettings.calculateMaxLevel(settings)
            
            // Ensure levels are within valid range before setting sliders
            val constrainedTrainerLevel = settings.currentLevel.coerceIn(1, maxLevel)
            val constrainedListenLevel = settings.listenCurrentLevel.coerceIn(1, maxLevel)
            
            // Safely update both sliders using post() to avoid timing issues
            safelyUpdateSlider(
                slider = sliderCurrentLevel,
                newValue = constrainedTrainerLevel.toFloat(),
                newValueTo = maxLevel.toFloat(),
                textView = textCurrentLevelValue,
                displayText = root.context.getString(R.string.value_level, constrainedTrainerLevel) + " / $maxLevel"
            )
            
            safelyUpdateSlider(
                slider = sliderListenCurrentLevel,
                newValue = constrainedListenLevel.toFloat(),
                newValueTo = maxLevel.toFloat(),
                textView = textListenCurrentLevelValue,
                displayText = root.context.getString(R.string.value_level, constrainedListenLevel) + " / $maxLevel"
            )
            
            // Update character displays for both modes using post() as well
            sliderCurrentLevel.post {
                updateCharacterDisplay(constrainedTrainerLevel, settings, isListenMode = false)
                updateCharacterDisplay(constrainedListenLevel, settings, isListenMode = true)
            }
            
            // If levels were constrained, update the settings to persist the corrected values
            if (constrainedTrainerLevel != settings.currentLevel || constrainedListenLevel != settings.listenCurrentLevel) {
                // Use post() to avoid interfering with current layout pass
                sliderCurrentLevel.post {
                    onSettingsUpdate { it.copy(
                        currentLevel = constrainedTrainerLevel,
                        listenCurrentLevel = constrainedListenLevel
                    )}
                }
            }
            
            // Other level settings (these don't have validation issues)
            switchLockLevel.isChecked = settings.lockLevel
            sliderCorrectToLevelUp.value = settings.correctAnswersToLevelUp.toFloat()
            sliderIncorrectToDrop.value = settings.incorrectAnswersToDropLevel.toFloat()
            sliderListenSequencesToLevelUp.value = settings.listenSequencesToLevelUp.toFloat()
            
            textCorrectToLevelUpValue.text = settings.correctAnswersToLevelUp.toString()
            textIncorrectToDropValue.text = settings.incorrectAnswersToDropLevel.toString()
            textListenSequencesToLevelUpValue.text = if (settings.listenSequencesToLevelUp == 0) "Manual" else settings.listenSequencesToLevelUp.toString()
            
            // Character settings
            switchUseProsigns.isChecked = settings.useProsigns
            switchUseNumbers.isChecked = settings.useNumbers
            switchUsePunctuation.isChecked = settings.usePunctuation
            editCustomCharacters.setText(settings.customCharacterSet)
            
            // Trainer Behavior settings
            switchFailOnFirstIncorrect.isChecked = settings.failOnFirstIncorrect
        }
    }
} 