package com.so5km.qrstrainer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settings: TrainingSettings
    private lateinit var progressTracker: ProgressTracker

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        progressTracker = ProgressTracker(requireContext())
        settings = TrainingSettings.load(requireContext())
        
        setupUI()
        loadCurrentSettings()

        return root
    }

    private fun setupUI() {
        // Speed settings
        binding.seekBarSpeed.max = 35  // 5-40 WPM
        binding.seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 5  // 5-40 WPM
                binding.textSpeedDisplay.text = "$speed WPM"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Level settings
        binding.seekBarLevel.max = MorseCode.getMaxLevel() - 1  // 0-based
        binding.seekBarLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress + 1  // 1-based display
                binding.textLevelDisplay.text = "Level $level"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.checkBoxLockLevel.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }

        // Group size settings
        binding.seekBarGroupMin.max = 8  // 1-9 characters
        binding.seekBarGroupMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val min = progress + 1  // 1-9
                binding.textGroupMinDisplay.text = min.toString()
                
                // Ensure max is at least equal to min
                if (binding.seekBarGroupMax.progress < progress) {
                    binding.seekBarGroupMax.progress = progress
                    binding.textGroupMaxDisplay.text = min.toString()
                }
                
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarGroupMax.max = 8  // 1-9 characters
        binding.seekBarGroupMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val max = progress + 1  // 1-9
                binding.textGroupMaxDisplay.text = max.toString()
                
                // Ensure min is not greater than max
                if (binding.seekBarGroupMin.progress > progress) {
                    binding.seekBarGroupMin.progress = progress
                    binding.textGroupMinDisplay.text = max.toString()
                }
                
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Repeat count settings
        binding.seekBarRepeatCount.max = 9  // 1-10 times
        binding.seekBarRepeatCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val repeat = progress + 1  // 1-10
                val timesText = if (repeat == 1) "time" else "times"
                binding.textRepeatCountDisplay.text = "$repeat $timesText"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Repeat spacing settings
        binding.seekBarRepeatSpacing.max = 95  // 0.5-10 seconds
        binding.seekBarRepeatSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = (progress + 5) / 10.0  // 0.5-10.0 seconds
                binding.textRepeatSpacingDisplay.text = "${String.format("%.1f", spacing)} seconds"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Answer timeout settings
        binding.seekBarTimeout.max = 25  // 5-30 seconds
        binding.seekBarTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val timeout = progress + 5  // 5-30 seconds
                binding.textTimeoutDisplay.text = "$timeout seconds"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Required correct answers settings
        binding.seekBarRequiredCorrect.max = 25  // 5-30 correct
        binding.seekBarRequiredCorrect.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val required = progress + 5  // 5-30
                binding.textRequiredCorrectDisplay.text = "$required correct per character"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Reset button
        binding.buttonResetProgress.setOnClickListener {
            showResetConfirmation()
        }
    }

    private fun loadCurrentSettings() {
        // Load speed settings
        binding.seekBarSpeed.progress = settings.speedWpm - 5  // Convert to 0-based
        binding.textSpeedDisplay.text = "${settings.speedWpm} WPM"
        
        // Load level settings
        binding.seekBarLevel.progress = settings.kochLevel - 1  // Convert to 0-based
        binding.textLevelDisplay.text = "Level ${settings.kochLevel}"
        
        binding.checkBoxLockLevel.isChecked = settings.isLevelLocked
        
        // Load group size settings
        binding.seekBarGroupMin.progress = settings.groupSizeMin - 1  // Convert to 0-based
        binding.textGroupMinDisplay.text = settings.groupSizeMin.toString()
        
        binding.seekBarGroupMax.progress = settings.groupSizeMax - 1  // Convert to 0-based
        binding.textGroupMaxDisplay.text = settings.groupSizeMax.toString()
        
        // Load repeat count settings
        binding.seekBarRepeatCount.progress = settings.repeatCount - 1  // Convert to 0-based
        val timesText = if (settings.repeatCount == 1) "time" else "times"
        binding.textRepeatCountDisplay.text = "${settings.repeatCount} $timesText"
        
        // Load repeat spacing settings
        val spacingSeconds = settings.repeatSpacingMs / 1000.0
        binding.seekBarRepeatSpacing.progress = ((spacingSeconds * 10) - 5).toInt()  // Convert to 0-based
        binding.textRepeatSpacingDisplay.text = "${String.format("%.1f", spacingSeconds)} seconds"
        
        // Load timeout settings
        binding.seekBarTimeout.progress = settings.answerTimeoutSeconds - 5  // Convert to 0-based
        binding.textTimeoutDisplay.text = "${settings.answerTimeoutSeconds} seconds"
        
        // Load required correct settings
        binding.seekBarRequiredCorrect.progress = settings.requiredCorrectToAdvance - 5  // Convert to 0-based
        binding.textRequiredCorrectDisplay.text = "${settings.requiredCorrectToAdvance} correct per character"
    }

    private fun saveSettings() {
        val speed = binding.seekBarSpeed.progress + 5  // 5-40
        val level = binding.seekBarLevel.progress + 1  // 1-40
        val groupMin = binding.seekBarGroupMin.progress + 1  // 1-9
        val groupMax = binding.seekBarGroupMax.progress + 1  // 1-9
        val repeatCount = binding.seekBarRepeatCount.progress + 1  // 1-10
        val repeatSpacingMs = ((binding.seekBarRepeatSpacing.progress + 5) * 100)  // 500-10000ms
        val timeout = binding.seekBarTimeout.progress + 5  // 5-30
        val requiredCorrect = binding.seekBarRequiredCorrect.progress + 5  // 5-30
        
        settings = TrainingSettings(
            speedWpm = speed,
            kochLevel = level,
            isLevelLocked = binding.checkBoxLockLevel.isChecked,
            groupSizeMin = groupMin,
            groupSizeMax = groupMax,
            answerTimeoutSeconds = timeout,
            repeatCount = repeatCount,
            repeatSpacingMs = repeatSpacingMs,
            requiredCorrectToAdvance = requiredCorrect
        )
        
        settings.save(requireContext())
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset All Progress")
            .setMessage("Are you sure you want to reset all training progress? This cannot be undone.")
            .setPositiveButton("Yes, Reset") { _, _ ->
                progressTracker.resetProgress()
                // Show success message
                AlertDialog.Builder(requireContext())
                    .setTitle("Progress Reset")
                    .setMessage("All training progress has been reset successfully.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 