package com.so5km.qrstrainer.ui.settings.components

import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding

/**
 * Component that handles level progression and character set settings
 */
class LevelAndCharacterSettingsComponent(
    private val binding: FragmentSettingsBinding,
    private val onSettingsUpdate: ((TrainingSettings) -> TrainingSettings) -> Unit
) {
    
    fun setup() {
        setupLevelSliders()
        setupCharacterSwitches()
        setupTrainerBehaviorSwitches()
    }
    
    private fun setupLevelSliders() {
        // Current Level
        binding.sliderCurrentLevel.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                
                // Calculate max level based on current character settings
                val currentSettings = getCurrentSettings()
                val maxLevel = TrainingSettings.calculateMaxLevel(
                    useNumbers = currentSettings.useNumbers,
                    usePunctuation = currentSettings.usePunctuation,
                    useProsigns = currentSettings.useProsigns,
                    customCharacterSet = currentSettings.customCharacterSet
                )
                
                binding.textCurrentLevelValue.text = binding.root.context.getString(R.string.value_level, level) + " / $maxLevel"
                onSettingsUpdate { it.copy(currentLevel = level) }
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
    }
    
    private fun setupCharacterSwitches() {
        // Prosigns Switch
        binding.switchUseProsigns.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(useProsigns = isChecked) }
            updateLevelSliderRange()
        }
        
        // Numbers Switch
        binding.switchUseNumbers.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(useNumbers = isChecked) }
            updateLevelSliderRange()
        }
        
        // Punctuation Switch
        binding.switchUsePunctuation.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(usePunctuation = isChecked) }
            updateLevelSliderRange()
        }
        
        // Custom Characters
        binding.editCustomCharacters.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val customChars = binding.editCustomCharacters.text?.toString() ?: ""
                onSettingsUpdate { it.copy(customCharacterSet = customChars.uppercase()) }
                updateLevelSliderRange()
            }
        }
    }
    
    private fun setupTrainerBehaviorSwitches() {
        // Fail on First Incorrect Switch
        binding.switchFailOnFirstIncorrect.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(failOnFirstIncorrect = isChecked) }
        }
    }
    
    /**
     * Update the level slider range when character settings change
     */
    private fun updateLevelSliderRange() {
        val currentSettings = getCurrentSettings()
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
            onSettingsUpdate { it.copy(currentLevel = currentLevel) }
        }
        
        // Update the display text to show current range
        binding.textCurrentLevelValue.text = binding.root.context.getString(R.string.value_level, currentLevel) + " / $maxLevel"
    }
    
    // Helper method to get current settings - would be passed from parent
    private fun getCurrentSettings(): TrainingSettings {
        // This would be provided by the parent fragment
        // For now, return a default - this should be passed as a parameter
        return TrainingSettings.default()
    }
    
    fun updateUI(settings: TrainingSettings) {
        binding.apply {
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
            
            textCurrentLevelValue.text = root.context.getString(R.string.value_level, settings.currentLevel) + " / $maxLevel"
            textCorrectToLevelUpValue.text = settings.correctAnswersToLevelUp.toString()
            textIncorrectToDropValue.text = settings.incorrectAnswersToDropLevel.toString()
            
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