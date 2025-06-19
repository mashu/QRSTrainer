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
        // Speed settings (increased range for faster training)
        binding.seekBarSpeed.max = 55  // 10-65 WPM
        binding.seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 10  // 10-65 WPM (much faster minimum)
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

        // Repeat spacing settings (reduced minimum from 0.5s to 0.0s)
        binding.seekBarRepeatSpacing.max = 100  // 0-10 seconds
        binding.seekBarRepeatSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = progress / 10.0  // 0.0-10.0 seconds
                binding.textRepeatSpacingDisplay.text = "${String.format("%.1f", spacing)} seconds"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Answer timeout settings (much faster for rapid training)
        binding.seekBarTimeout.max = 30  // 1-31 seconds
        binding.seekBarTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val timeout = progress + 1  // 1-31 seconds (minimum 1s for rapid training!)
                binding.textTimeoutDisplay.text = "$timeout seconds"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Required correct answers settings (allow much lower for faster progression)
        binding.seekBarRequiredCorrect.max = 29  // 1-30 correct
        binding.seekBarRequiredCorrect.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val required = progress + 1  // 1-30 (minimum 1 for fastest progression!)
                binding.textRequiredCorrectDisplay.text = "$required correct per character"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Sequence delay settings (0-5 seconds)
        binding.seekBarSequenceDelay.max = 50  // 0-5 seconds
        binding.seekBarSequenceDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delay = progress / 10.0  // 0.0-5.0 seconds
                val displayText = if (delay == 0.0) "No delay (immediate)" else "${String.format("%.1f", delay)} seconds"
                binding.textSequenceDelayDisplay.text = displayText
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Reset button
        binding.buttonResetProgress.setOnClickListener {
            showResetConfirmation()
        }
        
        // New audio settings
        // Tone frequency settings (300-1000 Hz)
        binding.seekBarToneFrequency.max = 70  // 300-1000 Hz
        binding.seekBarToneFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val frequency = progress * 10 + 300  // 300-1000 Hz
                binding.textToneFrequencyDisplay.text = "$frequency Hz"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Farnsworth timing settings (0-35 WPM, 0 = disabled)
        binding.seekBarFarnsworth.max = 35  // 0-35 WPM
        binding.seekBarFarnsworth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val farnsworth = progress  // 0-35 WPM
                val display = if (farnsworth == 0) "0 WPM (disabled)" else "$farnsworth WPM"
                binding.textFarnsworthDisplay.text = display
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Word spacing settings (0-1000 ms)
        binding.seekBarWordSpacing.max = 100  // 0-1000 ms
        binding.seekBarWordSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = progress * 10  // 0-1000 ms
                binding.textWordSpacingDisplay.text = "+$spacing ms"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Group spacing settings (0-1000 ms)
        binding.seekBarGroupSpacing.max = 100  // 0-1000 ms
        binding.seekBarGroupSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = progress * 10  // 0-1000 ms
                binding.textGroupSpacingDisplay.text = "+$spacing ms"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // CW Filter settings
        // Filter bandwidth (100-2000 Hz)
        binding.seekBarFilterBandwidth.max = 190  // 100-2000 Hz
        binding.seekBarFilterBandwidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bandwidth = progress * 10 + 100  // 100-2000 Hz
                binding.textFilterBandwidthDisplay.text = "$bandwidth Hz"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Secondary filter bandwidth (100-2000 Hz)
        binding.seekBarSecondaryFilterBandwidth.max = 190  // 100-2000 Hz
        binding.seekBarSecondaryFilterBandwidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bandwidth = progress * 10 + 100  // 100-2000 Hz
                binding.textSecondaryFilterBandwidthDisplay.text = "$bandwidth Hz"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Filter Q factor (1.0-20.0)
        binding.seekBarFilterQ.max = 190  // 1.0-20.0
        binding.seekBarFilterQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val qFactor = (progress / 10.0f) + 1.0f  // 1.0-20.0
                binding.textFilterQDisplay.text = "Q = ${String.format("%.1f", qFactor)}"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Background noise level (0-100%)
        binding.seekBarBackgroundNoise.max = 100  // 0-100%
        binding.seekBarBackgroundNoise.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val noiseLevel = progress  // 0-100%
                binding.textBackgroundNoiseDisplay.text = "$noiseLevel%"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Primary filter offset (-200 to +200 Hz)
        binding.seekBarPrimaryFilterOffset.max = 400  // -200 to +200 Hz
        binding.seekBarPrimaryFilterOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 200  // -200 to +200 Hz
                val sign = if (offset >= 0) "+" else ""
                binding.textPrimaryFilterOffsetDisplay.text = "$sign$offset Hz"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Secondary filter offset (-200 to +200 Hz)
        binding.seekBarSecondaryFilterOffset.max = 400  // -200 to +200 Hz
        binding.seekBarSecondaryFilterOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 200  // -200 to +200 Hz
                val sign = if (offset >= 0) "+" else ""
                binding.textSecondaryFilterOffsetDisplay.text = "$sign$offset Hz"
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Filter ringing checkbox
        binding.checkBoxFilterRinging.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }
    }

    private fun loadCurrentSettings() {
        // Load speed settings
        binding.seekBarSpeed.progress = settings.speedWpm - 10  // Convert to 0-based (updated for new range)
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
        binding.seekBarRepeatSpacing.progress = (spacingSeconds * 10).toInt()  // Convert to 0-based (updated for new range)
        binding.textRepeatSpacingDisplay.text = "${String.format("%.1f", spacingSeconds)} seconds"
        
        // Load timeout settings
        binding.seekBarTimeout.progress = settings.answerTimeoutSeconds - 1  // Convert to 0-based (updated for new range)
        binding.textTimeoutDisplay.text = "${settings.answerTimeoutSeconds} seconds"
        
        // Load required correct settings
        binding.seekBarRequiredCorrect.progress = settings.requiredCorrectToAdvance - 1  // Convert to 0-based (updated for new range)
        binding.textRequiredCorrectDisplay.text = "${settings.requiredCorrectToAdvance} correct per character"
        
        // Load sequence delay settings
        val delaySeconds = settings.sequenceDelayMs / 1000.0
        binding.seekBarSequenceDelay.progress = (delaySeconds * 10).toInt()  // Convert to 0-based
        val delayDisplayText = if (delaySeconds == 0.0) "No delay (immediate)" else "${String.format("%.1f", delaySeconds)} seconds"
        binding.textSequenceDelayDisplay.text = delayDisplayText
        
        // Load new audio settings
        // Tone frequency (300-1000 Hz)
        binding.seekBarToneFrequency.progress = (settings.toneFrequencyHz - 300) / 10  // Convert to 0-based
        binding.textToneFrequencyDisplay.text = "${settings.toneFrequencyHz} Hz"
        
        // Farnsworth timing (0-35 WPM)
        binding.seekBarFarnsworth.progress = settings.farnsworthWpm  // Already 0-based
        val farnsworthDisplay = if (settings.farnsworthWpm == 0) "0 WPM (disabled)" else "${settings.farnsworthWpm} WPM"
        binding.textFarnsworthDisplay.text = farnsworthDisplay
        
        // Word spacing (0-1000 ms)
        binding.seekBarWordSpacing.progress = settings.wordSpacingMs / 10  // Convert to 0-based
        binding.textWordSpacingDisplay.text = "+${settings.wordSpacingMs} ms"
        
        // Group spacing (0-1000 ms)
        binding.seekBarGroupSpacing.progress = settings.groupSpacingMs / 10  // Convert to 0-based
        binding.textGroupSpacingDisplay.text = "+${settings.groupSpacingMs} ms"
        
        // CW Filter settings
        // Filter bandwidth (100-2000 Hz)
        binding.seekBarFilterBandwidth.progress = (settings.filterBandwidthHz - 100) / 10  // Convert to 0-based
        binding.textFilterBandwidthDisplay.text = "${settings.filterBandwidthHz} Hz"
        
        // Secondary filter bandwidth (100-2000 Hz)
        binding.seekBarSecondaryFilterBandwidth.progress = (settings.secondaryFilterBandwidthHz - 100) / 10  // Convert to 0-based
        binding.textSecondaryFilterBandwidthDisplay.text = "${settings.secondaryFilterBandwidthHz} Hz"
        
        // Filter Q factor (1.0-20.0)
        binding.seekBarFilterQ.progress = ((settings.filterQFactor - 1.0f) * 10).toInt()  // Convert to 0-based
        binding.textFilterQDisplay.text = "Q = ${String.format("%.1f", settings.filterQFactor)}"
        
        // Background noise level (0-100%)
        binding.seekBarBackgroundNoise.progress = (settings.backgroundNoiseLevel * 100).toInt()  // Convert to 0-based
        binding.textBackgroundNoiseDisplay.text = "${(settings.backgroundNoiseLevel * 100).toInt()}%"
        
        // Filter offset settings
        // Primary filter offset (-200 to +200 Hz)
        binding.seekBarPrimaryFilterOffset.progress = settings.primaryFilterOffset + 200  // Convert to 0-based
        val primarySign = if (settings.primaryFilterOffset >= 0) "+" else ""
        binding.textPrimaryFilterOffsetDisplay.text = "$primarySign${settings.primaryFilterOffset} Hz"
        
        // Secondary filter offset (-200 to +200 Hz)
        binding.seekBarSecondaryFilterOffset.progress = settings.secondaryFilterOffset + 200  // Convert to 0-based
        val secondarySign = if (settings.secondaryFilterOffset >= 0) "+" else ""
        binding.textSecondaryFilterOffsetDisplay.text = "$secondarySign${settings.secondaryFilterOffset} Hz"
        
        // Filter ringing checkbox
        binding.checkBoxFilterRinging.isChecked = settings.filterRingingEnabled
        
        // Update the filter graph with initial values
        updateFilterGraph()
    }

    private fun saveSettings() {
        val speed = binding.seekBarSpeed.progress + 10  // 10-65 (updated for new range)
        val level = binding.seekBarLevel.progress + 1  // 1-40
        val groupMin = binding.seekBarGroupMin.progress + 1  // 1-9
        val groupMax = binding.seekBarGroupMax.progress + 1  // 1-9
        val repeatCount = binding.seekBarRepeatCount.progress + 1  // 1-10
        val repeatSpacingMs = (binding.seekBarRepeatSpacing.progress * 100)  // 0-10000ms (updated for new range)
        val timeout = binding.seekBarTimeout.progress + 1  // 1-31 (updated for new range)
        val requiredCorrect = binding.seekBarRequiredCorrect.progress + 1  // 1-30 (updated for new range)
        val sequenceDelayMs = (binding.seekBarSequenceDelay.progress * 100)  // 0-5000ms
        
        // CW Filter settings
        val filterBandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val secondaryFilterBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val filterQ = (binding.seekBarFilterQ.progress / 10.0f) + 1.0f  // 1.0-20.0
        val backgroundNoise = binding.seekBarBackgroundNoise.progress / 100.0f  // 0.0-1.0
        val primaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200  // -200 to +200 Hz
        val secondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200  // -200 to +200 Hz
        
        settings = TrainingSettings(
            speedWpm = speed,
            kochLevel = level,
            isLevelLocked = binding.checkBoxLockLevel.isChecked,
            groupSizeMin = groupMin,
            groupSizeMax = groupMax,
            answerTimeoutSeconds = timeout,
            repeatCount = repeatCount,
            repeatSpacingMs = repeatSpacingMs,
            requiredCorrectToAdvance = requiredCorrect,
            sequenceDelayMs = sequenceDelayMs,
            
            // Audio settings
            toneFrequencyHz = binding.seekBarToneFrequency.progress * 10 + 300,
            farnsworthWpm = binding.seekBarFarnsworth.progress,
            wordSpacingMs = binding.seekBarWordSpacing.progress * 10,
            groupSpacingMs = binding.seekBarGroupSpacing.progress * 10,
            
            // CW Filter settings
            filterBandwidthHz = filterBandwidth,
            secondaryFilterBandwidthHz = secondaryFilterBandwidth,
            filterQFactor = filterQ,
            backgroundNoiseLevel = backgroundNoise,
            filterRingingEnabled = binding.checkBoxFilterRinging.isChecked,
            primaryFilterOffset = primaryOffset,
            secondaryFilterOffset = secondaryOffset
        )
        
        settings.save(requireContext())
    }
    
    private fun updateFilterGraph() {
        val bandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val secondaryBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val qFactor = (binding.seekBarFilterQ.progress / 10.0f) + 1.0f  // 1.0-20.0
        val noiseLevel = binding.seekBarBackgroundNoise.progress / 100.0f  // 0.0-1.0
        val primaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200  // -200 to +200 Hz
        val secondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200  // -200 to +200 Hz
        
        binding.filterGraphView.updateFilter(bandwidth, secondaryBandwidth, qFactor, noiseLevel, primaryOffset, secondaryOffset)
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