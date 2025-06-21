package com.so5km.qrstrainer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settings: TrainingSettings
    private lateinit var progressTracker: ProgressTracker
    private lateinit var morseCodeGenerator: MorseCodeGenerator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        progressTracker = ProgressTracker(requireContext())
        settings = TrainingSettings.load(requireContext())
        morseCodeGenerator = MorseCodeGenerator(requireContext())
        
        setupUI()
        setupCollapsibleSections()
        loadCurrentSettings()

        return root
    }

    private fun setupCollapsibleSections() {
        // Load the last expanded tab from settings, default to "signal" for CW experimentation
        val lastExpandedTab = settings.lastExpandedSettingsTab
        
        // Set initial state based on saved preference
        when (lastExpandedTab) {
            "training" -> {
                binding.contentTrainingSettings.visibility = View.VISIBLE
                binding.iconTrainingExpand.rotation = 90f
                binding.contentSignalSettings.visibility = View.GONE
                binding.iconSignalExpand.rotation = 0f
                binding.contentNoiseSettings.visibility = View.GONE
                binding.iconNoiseExpand.rotation = 0f
            }
            "signal" -> {
                binding.contentTrainingSettings.visibility = View.GONE
                binding.iconTrainingExpand.rotation = 0f
                binding.contentSignalSettings.visibility = View.VISIBLE
                binding.iconSignalExpand.rotation = 90f
                binding.contentNoiseSettings.visibility = View.GONE
                binding.iconNoiseExpand.rotation = 0f
            }
            "noise" -> {
                binding.contentTrainingSettings.visibility = View.GONE
                binding.iconTrainingExpand.rotation = 0f
                binding.contentSignalSettings.visibility = View.GONE
                binding.iconSignalExpand.rotation = 0f
                binding.contentNoiseSettings.visibility = View.VISIBLE
                binding.iconNoiseExpand.rotation = 90f
            }
            "audio" -> {
                // Legacy support - map to signal section
                binding.contentTrainingSettings.visibility = View.GONE
                binding.iconTrainingExpand.rotation = 0f
                binding.contentSignalSettings.visibility = View.VISIBLE
                binding.iconSignalExpand.rotation = 90f
                binding.contentNoiseSettings.visibility = View.GONE
                binding.iconNoiseExpand.rotation = 0f
            }
            else -> {
                // Default to signal settings expanded for tone experimentation
                binding.contentTrainingSettings.visibility = View.GONE
                binding.iconTrainingExpand.rotation = 0f
                binding.contentSignalSettings.visibility = View.VISIBLE
                binding.iconSignalExpand.rotation = 90f
                binding.contentNoiseSettings.visibility = View.GONE
                binding.iconNoiseExpand.rotation = 0f
            }
        }

        // Training Settings
        binding.headerTrainingSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentTrainingSettings,
                binding.iconTrainingExpand,
                "training"
            )
        }

        // Signal Settings
        binding.headerSignalSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentSignalSettings,
                binding.iconSignalExpand,
                "signal"
            )
        }

        // Noise Settings
        binding.headerNoiseSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentNoiseSettings,
                binding.iconNoiseExpand,
                "noise"
            )
        }
    }

    private fun toggleSectionAccordion(contentView: View, iconView: View, sectionType: String) {
        // First, collapse all other sections
        collapseAllSectionsExcept(sectionType)
        
        // Then toggle the clicked section
        if (contentView.visibility == View.GONE) {
            // Expand
            contentView.visibility = View.VISIBLE
            iconView.rotation = 90f
            
            // Save the expanded tab state
            settings = settings.copy(lastExpandedSettingsTab = sectionType)
            settings.save(requireContext())
        } else {
            // Collapse - don't save collapsed state, keep the last expanded preference
            contentView.visibility = View.GONE
            iconView.rotation = 0f
        }
    }
    
    private fun collapseAllSectionsExcept(exceptSection: String) {
        if (exceptSection != "training") {
            binding.contentTrainingSettings.visibility = View.GONE
            binding.iconTrainingExpand.rotation = 0f
        }
        if (exceptSection != "signal") {
            binding.contentSignalSettings.visibility = View.GONE
            binding.iconSignalExpand.rotation = 0f
        }
        if (exceptSection != "noise") {
            binding.contentNoiseSettings.visibility = View.GONE
            binding.iconNoiseExpand.rotation = 0f
        }
    }

    private fun setupUI() {
        // === TRAINING BEHAVIOR SETTINGS ===
        
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

        // Mistakes to drop level settings (0-10 mistakes, 0 = disabled)
        binding.seekBarMistakesToDrop.max = 10  // 0-10 mistakes
        binding.seekBarMistakesToDrop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mistakes = progress  // 0-10 mistakes
                val displayText = if (mistakes == 0) "0 mistakes (disabled)" else "$mistakes mistake${if (mistakes == 1) "" else "s"} (enabled)"
                binding.textMistakesToDropDisplay.text = displayText
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

        // Level change delay settings (0-5 seconds)
        binding.seekBarLevelChangeDelay.max = 50  // 0-5 seconds
        binding.seekBarLevelChangeDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delay = progress / 10.0  // 0.0-5.0 seconds
                val displayText = if (delay == 0.0) "No delay (immediate)" else "${String.format("%.1f", delay)} seconds"
                binding.textLevelChangeDelayDisplay.text = displayText
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Farnsworth timing settings (0-35 WPM, 0 = disabled) - MOVED TO TRAINING SECTION
        // This affects character spacing and training difficulty, not basic audio generation
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

        // === AUDIO GENERATION SETTINGS ===
        
        // Tone frequency settings (300-1000 Hz)
        binding.seekBarToneFrequency.max = 70  // 300-1000 Hz
        binding.seekBarToneFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val frequency = progress * 10 + 300  // 300-1000 Hz
                
                // Calculate actual filter frequencies based on current offsets
                val primaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200
                val secondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200
                val primaryFilterFreq = frequency + primaryOffset
                val secondaryFilterFreq = frequency + secondaryOffset
                
                // Show calculated filter frequencies for CW tuning
                val filterInfo = if (primaryOffset == 0 && secondaryOffset == 0) {
                    "Both filters at ${frequency}Hz"
                } else {
                    "Filters: ${primaryFilterFreq}Hz / ${secondaryFilterFreq}Hz"
                }
                
                binding.textToneFrequencyDisplay.text = "$frequency Hz ($filterInfo)"
                
                updateEnvelopeGraph() // Tone frequency affects envelope sharpness
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // App volume settings (0-100%)
        binding.seekBarAppVolume.max = 100  // 0-100%
        binding.seekBarAppVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress  // 0-100%
                binding.textAppVolumeDisplay.text = "$volume%"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Audio envelope settings (1-20ms)
        binding.seekBarAudioEnvelope.max = 19  // 1-20ms
        binding.seekBarAudioEnvelope.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val envelope = progress + 1  // 1-20ms
                binding.textAudioEnvelopeDisplay.text = "$envelope ms"
                updateEnvelopeGraph()
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Keying style settings
        binding.seekBarKeyingStyle.max = 2  // 0-2
        binding.seekBarKeyingStyle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val style = when (progress) {
                    0 -> "Hard Keying"
                    1 -> "Soft Keying" 
                    2 -> "Smooth Keying"
                    else -> "Hard Keying"
                }
                binding.textKeyingStyleDisplay.text = style
                updateEnvelopeGraph()
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

        // Reset button
        binding.buttonResetProgress.setOnClickListener {
            showResetConfirmation()
        }

        // Reset Audio Settings button
        binding.buttonResetAudioSettings.setOnClickListener {
            showAudioResetConfirmation()
        }

        // === CW FILTER SETTINGS ===
        
        // Filter bandwidth (100-2000 Hz) with real-time combined bandwidth constraint
        binding.seekBarFilterBandwidth.max = 190  // 100-2000 Hz
        binding.seekBarFilterBandwidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bandwidth = progress * 10 + 100  // 100-2000 Hz
                
                // Calculate helpful CW parameters
                val toneFreq = binding.seekBarToneFrequency.progress * 10 + 300
                val qFactor = (binding.seekBarFilterQ.progress / 10.0f) + 1.0f
                val cwCharacter = when {
                    bandwidth < 200 -> "Very Sharp CW"
                    bandwidth < 400 -> "Sharp CW"
                    bandwidth < 800 -> "Normal CW"
                    bandwidth < 1200 -> "Wide CW"
                    else -> "Very Wide"
                }
                val modDepth = (qFactor / 5.0f).coerceIn(1.0f, 4.0f) * 15.0f * (0.5f + qFactor/20.0f)
                
                binding.textFilterBandwidthDisplay.text = "$bandwidth Hz ($cwCharacter, ~${modDepth.toInt()}Hz LFO range)"
                
                if (fromUser) {
                    // Trigger offset constraint check by simulating offset change
                    val currentPrimaryProgress = binding.seekBarPrimaryFilterOffset.progress
                    binding.seekBarPrimaryFilterOffset.setOnSeekBarChangeListener(null)
                    binding.seekBarPrimaryFilterOffset.progress = currentPrimaryProgress
                    setupPrimaryOffsetListener()
                    
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Secondary filter bandwidth (100-2000 Hz) with real-time combined bandwidth constraint
        binding.seekBarSecondaryFilterBandwidth.max = 190  // 100-2000 Hz
        binding.seekBarSecondaryFilterBandwidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bandwidth = progress * 10 + 100  // 100-2000 Hz
                binding.textSecondaryFilterBandwidthDisplay.text = "$bandwidth Hz"
                if (fromUser) {
                    // Trigger offset constraint check by simulating offset change
                    val currentSecondaryProgress = binding.seekBarSecondaryFilterOffset.progress
                    binding.seekBarSecondaryFilterOffset.setOnSeekBarChangeListener(null)
                    binding.seekBarSecondaryFilterOffset.progress = currentSecondaryProgress
                    setupSecondaryOffsetListener()
                    
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
                
                // Calculate CW character based on Q factor
                val cwCharacter = when {
                    qFactor < 3.0f -> "Gentle"
                    qFactor < 6.0f -> "Mild"
                    qFactor < 10.0f -> "Sharp"
                    qFactor < 15.0f -> "Very Sharp"
                    else -> "Extreme"
                }
                val atmosphericIntensity = minOf(3.0f, 0.5f + qFactor/20.0f)
                val qBoost = (qFactor / 5.0f).coerceIn(1.0f, 4.0f)
                val totalModDepth = 15.0f * atmosphericIntensity * qBoost
                
                binding.textFilterQDisplay.text = "Q = ${String.format("%.1f", qFactor)} ($cwCharacter, ${totalModDepth.toInt()}Hz mod)"
                
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                    updateContinuousNoiseIfActive()
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
                
                // Provide helpful descriptions for CW noise levels
                val description = when {
                    noiseLevel == 0 -> "Off"
                    noiseLevel < 20 -> "Very Quiet"
                    noiseLevel < 40 -> "Quiet"
                    noiseLevel < 60 -> "Moderate"
                    noiseLevel < 80 -> "Strong"
                    noiseLevel < 95 -> "Very Strong"
                    else -> "Maximum"
                }
                val recommendation = when {
                    noiseLevel in 80..95 -> " (Recommended for CW)"
                    noiseLevel in 60..79 -> " (Good for testing)"
                    noiseLevel < 60 -> " (Light background)"
                    else -> " (May mask signal)"
                }
                
                binding.textBackgroundNoiseDisplay.text = "$noiseLevel% ($description$recommendation)"
                
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Noise Volume (Independent) (0-100%)
        binding.seekBarNoiseVolume.max = 100  // 0-100%
        binding.seekBarNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val noiseVolumeLevel = progress  // 0-100%
                
                val volumeDescription = when {
                    noiseVolumeLevel == 0 -> "Silent"
                    noiseVolumeLevel < 20 -> "Very Quiet"
                    noiseVolumeLevel < 40 -> "Quiet"
                    noiseVolumeLevel < 60 -> "Moderate"
                    noiseVolumeLevel < 80 -> "Loud"
                    else -> "Very Loud"
                }
                
                binding.textNoiseVolumeDisplay.text = "$noiseVolumeLevel% ($volumeDescription)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Primary filter offset (-200 to +200 Hz) with real-time combined bandwidth constraint
        binding.seekBarPrimaryFilterOffset.max = 400  // -200 to +200 Hz
        
        // Secondary filter offset (-200 to +200 Hz) with real-time combined bandwidth constraint
        binding.seekBarSecondaryFilterOffset.max = 400  // -200 to +200 Hz
        
        // Set up the constraint system for filter offsets
        setupFilterOffsetListeners()

        // LFO 1 Frequency (0.05-0.5 Hz)
        binding.seekBarLfo1Frequency.max = 45  // 0.05-0.5 Hz
        binding.seekBarLfo1Frequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val frequency = (progress + 1) / 100.0f  // 0.01-0.46 Hz, then scale to 0.05-0.5
                val scaledFreq = 0.05f + (frequency * 0.45f)  // 0.05-0.5 Hz
                
                val description = when {
                    scaledFreq < 0.1f -> "Very Slow"
                    scaledFreq < 0.2f -> "Slow"
                    scaledFreq < 0.3f -> "Moderate"
                    scaledFreq < 0.4f -> "Fast"
                    else -> "Very Fast"
                }
                
                binding.textLfo1FrequencyDisplay.text = "${String.format("%.2f", scaledFreq)} Hz ($description)"
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // LFO 2 Frequency (0.05-0.5 Hz)
        binding.seekBarLfo2Frequency.max = 45  // 0.05-0.5 Hz
        binding.seekBarLfo2Frequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val frequency = (progress + 1) / 100.0f  // 0.01-0.46 Hz, then scale to 0.05-0.5
                val scaledFreq = 0.05f + (frequency * 0.45f)  // 0.05-0.5 Hz
                
                val description = when {
                    scaledFreq < 0.1f -> "Very Slow"
                    scaledFreq < 0.2f -> "Slow"
                    scaledFreq < 0.3f -> "Moderate"
                    scaledFreq < 0.4f -> "Fast"
                    else -> "Very Fast"
                }
                
                binding.textLfo2FrequencyDisplay.text = "${String.format("%.2f", scaledFreq)} Hz ($description)"
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Atmospheric Intensity (0.5-5.0)
        binding.atmosphericIntensitySeekBar.max = 45  // 0.5-5.0
        binding.atmosphericIntensitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intensity = (progress / 10.0f) + 0.5f  // 0.5-5.0
                
                // Descriptive feedback for atmospheric intensity
                val description = when {
                    intensity < 1.0f -> "Minimal"
                    intensity < 2.0f -> "Light"
                    intensity < 3.0f -> "Moderate"
                    intensity < 4.0f -> "Strong"
                    else -> "Extreme"
                }
                
                binding.atmosphericIntensityValue.text = "${String.format("%.1f", intensity)} ($description)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Warmth (0-15 dB)
        val warmthProgress = (settings.warmth * 10).toInt().coerceIn(0, 150)
        binding.warmthSeekBar.progress = warmthProgress
        val warmthDescription = when {
            settings.warmth < 2.0f -> "Clean"
            settings.warmth < 4.0f -> "Neutral"
            settings.warmth < 6.0f -> "Slightly Warm"
            settings.warmth < 8.0f -> "Warm"
            settings.warmth < 10.0f -> "Rich"
            settings.warmth < 12.0f -> "Tube-like"
            else -> "Heavy Coloration"
        }
        binding.warmthValue.text = "${String.format("%.1f", settings.warmth)}dB ($warmthDescription)"

        // Crackle Intensity (0.01-0.2)
        val crackleProgress = ((settings.crackleIntensity - 0.01f) * 100).toInt().coerceIn(0, 190)
        binding.crackleIntensitySeekBar.progress = crackleProgress
        val crackleDescription = when {
            settings.crackleIntensity < 0.05f -> "Very Light"
            settings.crackleIntensity < 0.1f -> "Light"
            settings.crackleIntensity < 0.15f -> "Moderate"
            settings.crackleIntensity < 0.2f -> "Strong"
            else -> "Heavy"
        }
        binding.crackleIntensityValue.text = "${String.format("%.2f", settings.crackleIntensity)} ($crackleDescription)"

        // Resonance Jump Rate (0.1-2.0)
        val resonanceProgress = ((settings.resonanceJumpRate - 0.1f) * 100).toInt().coerceIn(0, 190)
        binding.resonanceJumpRateSeekBar.progress = resonanceProgress
        val resonanceDescription = when {
            settings.resonanceJumpRate < 0.4f -> "Rare"
            settings.resonanceJumpRate < 0.7f -> "Occasional"
            settings.resonanceJumpRate < 1.0f -> "Frequent"
            settings.resonanceJumpRate < 1.5f -> "Very Frequent"
            else -> "Constant"
        }
        binding.resonanceJumpRateValue.text = "${String.format("%.1f", settings.resonanceJumpRate)} ($resonanceDescription)"

        // Drift Speed (0.1-2.0)
        val driftProgress = ((settings.driftSpeed - 0.1f) * 100).toInt().coerceIn(0, 190)
        binding.driftSpeedSeekBar.progress = driftProgress
        val driftDescription = when {
            settings.driftSpeed < 0.4f -> "Very Slow"
            settings.driftSpeed < 0.7f -> "Slow"
            settings.driftSpeed < 1.0f -> "Moderate"
            settings.driftSpeed < 1.5f -> "Fast"
            else -> "Very Fast"
        }
        binding.driftSpeedValue.text = "${String.format("%.1f", settings.driftSpeed)} ($driftDescription)"

        // Set up missing seekbar listeners for atmospheric parameters
        
        // Warmth (0-15 dB)
        binding.warmthSeekBar.max = 150  // 0-15 dB
        binding.warmthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val warmth = progress / 10.0f  // 0.0-15.0 dB
                
                val description = when {
                    warmth < 2.0f -> "Clean"
                    warmth < 4.0f -> "Neutral"
                    warmth < 6.0f -> "Slightly Warm"
                    warmth < 8.0f -> "Warm"
                    warmth < 10.0f -> "Rich"
                    warmth < 12.0f -> "Tube-like"
                    else -> "Heavy Coloration"
                }
                
                binding.warmthValue.text = "${String.format("%.1f", warmth)}dB ($description)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Crackle Intensity (0.01-0.2)
        binding.crackleIntensitySeekBar.max = 190  // 0.01-0.2
        binding.crackleIntensitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val crackleIntensity = (progress / 1000.0f) + 0.01f  // 0.01-0.2
                
                val description = when {
                    crackleIntensity < 0.05f -> "Very Light"
                    crackleIntensity < 0.1f -> "Light"
                    crackleIntensity < 0.15f -> "Moderate"
                    crackleIntensity < 0.2f -> "Strong"
                    else -> "Heavy"
                }
                
                binding.crackleIntensityValue.text = "${String.format("%.2f", crackleIntensity)} ($description)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Resonance Jump Rate (0.1-2.0)
        binding.resonanceJumpRateSeekBar.max = 190  // 0.1-2.0
        binding.resonanceJumpRateSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val resonanceJumpRate = (progress / 100.0f) + 0.1f  // 0.1-2.0
                
                val description = when {
                    resonanceJumpRate < 0.4f -> "Rare"
                    resonanceJumpRate < 0.7f -> "Occasional"
                    resonanceJumpRate < 1.0f -> "Frequent"
                    resonanceJumpRate < 1.5f -> "Very Frequent"
                    else -> "Constant"
                }
                
                binding.resonanceJumpRateValue.text = "${String.format("%.1f", resonanceJumpRate)} ($description)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Drift Speed (0.1-2.0)
        binding.driftSpeedSeekBar.max = 190  // 0.1-2.0
        binding.driftSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val driftSpeed = (progress / 100.0f) + 0.1f  // 0.1-2.0
                
                val description = when {
                    driftSpeed < 0.4f -> "Very Slow"
                    driftSpeed < 0.7f -> "Slow"
                    driftSpeed < 1.0f -> "Moderate"
                    driftSpeed < 1.5f -> "Fast"
                    else -> "Very Fast"
                }
                
                binding.driftSpeedValue.text = "${String.format("%.1f", driftSpeed)} ($description)"
                
                if (fromUser) {
                    saveSettings()
                    updateContinuousNoiseIfActive()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Filter ringing checkbox
        binding.checkBoxFilterRinging.isChecked = settings.filterRingingEnabled
        binding.checkBoxFilterRinging.setOnCheckedChangeListener { _, _ ->
            saveSettings()
            updateContinuousNoiseIfActive()
        }
        
        // Continuous noise testing checkbox
        binding.checkBoxContinuousNoise.isChecked = settings.continuousNoiseEnabled
        binding.checkBoxContinuousNoise.setOnCheckedChangeListener { _, isChecked ->
            saveSettings()
            
            // Start or stop continuous noise testing based on checkbox
            if (isChecked) {
                morseCodeGenerator?.startTestNoise(getCurrentSettings())
            } else {
                morseCodeGenerator?.stopTestNoise()
            }
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
        
        // Load mistakes to drop level settings
        binding.seekBarMistakesToDrop.progress = settings.mistakesToDropLevel  // Already 0-based
        val mistakeDisplayText = if (settings.mistakesToDropLevel == 0) "0 mistakes (disabled)" else "${settings.mistakesToDropLevel} mistake${if (settings.mistakesToDropLevel == 1) "" else "s"} (enabled)"
        binding.textMistakesToDropDisplay.text = mistakeDisplayText
        
        // Load sequence delay settings
        val delaySeconds = settings.sequenceDelayMs / 1000.0
        binding.seekBarSequenceDelay.progress = (delaySeconds * 10).toInt()  // Convert to 0-based
        val delayDisplayText = if (delaySeconds == 0.0) "No delay (immediate)" else "${String.format("%.1f", delaySeconds)} seconds"
        binding.textSequenceDelayDisplay.text = delayDisplayText
        
        // Load level change delay settings
        val levelChangeDelaySeconds = settings.levelChangeDelayMs / 1000.0
        binding.seekBarLevelChangeDelay.progress = (levelChangeDelaySeconds * 10).toInt()  // Convert to 0-based
        val levelChangeDelayDisplayText = if (levelChangeDelaySeconds == 0.0) "No delay (immediate)" else "${String.format("%.1f", levelChangeDelaySeconds)} seconds"
        binding.textLevelChangeDelayDisplay.text = levelChangeDelayDisplayText
        
        // Load new audio settings
        // Tone frequency (300-1000 Hz)
        binding.seekBarToneFrequency.progress = (settings.toneFrequencyHz - 300) / 10  // Convert to 0-based
        val primaryFilterFreq = settings.toneFrequencyHz + settings.primaryFilterOffset
        val secondaryFilterFreq = settings.toneFrequencyHz + settings.secondaryFilterOffset
        val filterInfo = if (settings.primaryFilterOffset == 0 && settings.secondaryFilterOffset == 0) {
            "Both filters at ${settings.toneFrequencyHz}Hz"
        } else {
            "Filters: ${primaryFilterFreq}Hz / ${secondaryFilterFreq}Hz"
        }
        binding.textToneFrequencyDisplay.text = "${settings.toneFrequencyHz} Hz ($filterInfo)"
        
        // App volume (0-100%)
        binding.seekBarAppVolume.progress = (settings.appVolumeLevel * 100).toInt()  // Convert to 0-based
        binding.textAppVolumeDisplay.text = "${(settings.appVolumeLevel * 100).toInt()}%"
        
        // Audio envelope (1-20ms)
        binding.seekBarAudioEnvelope.progress = settings.audioEnvelopeMs - 1  // Convert to 0-based
        binding.textAudioEnvelopeDisplay.text = "${settings.audioEnvelopeMs} ms"
        
        // Keying style
        binding.seekBarKeyingStyle.progress = settings.keyingStyle  // Already 0-based
        val keyingStyleDisplay = when (settings.keyingStyle) {
            0 -> "Hard Keying"
            1 -> "Soft Keying"
            2 -> "Smooth Keying"
            else -> "Hard Keying"
        }
        binding.textKeyingStyleDisplay.text = keyingStyleDisplay
        
        // Update envelope graph with loaded settings
        updateEnvelopeGraph()
        
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
        val cwCharacter = when {
            settings.filterBandwidthHz < 200 -> "Very Sharp CW"
            settings.filterBandwidthHz < 400 -> "Sharp CW"
            settings.filterBandwidthHz < 800 -> "Normal CW"
            settings.filterBandwidthHz < 1200 -> "Wide CW"
            else -> "Very Wide"
        }
        val modDepth = (settings.filterQFactor / 5.0f).coerceIn(1.0f, 4.0f) * 15.0f * (0.5f + settings.filterQFactor/20.0f)
        binding.textFilterBandwidthDisplay.text = "${settings.filterBandwidthHz} Hz ($cwCharacter, ~${modDepth.toInt()}Hz LFO range)"
        
        // Secondary filter bandwidth (100-2000 Hz)
        binding.seekBarSecondaryFilterBandwidth.progress = (settings.secondaryFilterBandwidthHz - 100) / 10  // Convert to 0-based
        binding.textSecondaryFilterBandwidthDisplay.text = "${settings.secondaryFilterBandwidthHz} Hz"
        
        // Filter Q factor (1.0-20.0)
        binding.seekBarFilterQ.progress = ((settings.filterQFactor - 1.0f) * 10).toInt()  // Convert to 0-based
        val qCwCharacter = when {
            settings.filterQFactor < 3.0f -> "Gentle"
            settings.filterQFactor < 6.0f -> "Mild"
            settings.filterQFactor < 10.0f -> "Sharp"
            settings.filterQFactor < 15.0f -> "Very Sharp"
            else -> "Extreme"
        }
        val atmosphericIntensity = minOf(3.0f, 0.5f + settings.filterQFactor/20.0f)
        val qBoost = (settings.filterQFactor / 5.0f).coerceIn(1.0f, 4.0f)
        val totalModDepth = 15.0f * atmosphericIntensity * qBoost
        binding.textFilterQDisplay.text = "Q = ${String.format("%.1f", settings.filterQFactor)} ($qCwCharacter, ${totalModDepth.toInt()}Hz mod)"
        
        // Background noise level (0-100%)
        binding.seekBarBackgroundNoise.progress = (settings.backgroundNoiseLevel * 100).toInt()  // Convert to 0-based
        val noiseLevel = (settings.backgroundNoiseLevel * 100).toInt()
        val description = when {
            noiseLevel == 0 -> "Off"
            noiseLevel < 20 -> "Very Quiet"
            noiseLevel < 40 -> "Quiet"
            noiseLevel < 60 -> "Moderate"
            noiseLevel < 80 -> "Strong"
            noiseLevel < 95 -> "Very Strong"
            else -> "Maximum"
        }
        val recommendation = when {
            noiseLevel in 80..95 -> " (Recommended for CW)"
            noiseLevel in 60..79 -> " (Good for testing)"
            noiseLevel < 60 -> " (Light background)"
            else -> " (May mask signal)"
        }
        binding.textBackgroundNoiseDisplay.text = "$noiseLevel% ($description$recommendation)"
        
        // Noise Volume (Independent) (0-100%)
        binding.seekBarNoiseVolume.progress = (settings.noiseVolume * 100).toInt()  // Convert to 0-based
        val noiseVolumeLevel = (settings.noiseVolume * 100).toInt()
        val volumeDescription = when {
            noiseVolumeLevel == 0 -> "Silent"
            noiseVolumeLevel < 20 -> "Very Quiet"
            noiseVolumeLevel < 40 -> "Quiet"
            noiseVolumeLevel < 60 -> "Moderate"
            noiseVolumeLevel < 80 -> "Loud"
            else -> "Very Loud"
        }
        binding.textNoiseVolumeDisplay.text = "$noiseVolumeLevel% ($volumeDescription)"
        
        // Filter offset settings
        // Primary filter offset (-200 to +200 Hz)
        binding.seekBarPrimaryFilterOffset.progress = settings.primaryFilterOffset + 200  // Convert to 0-based
        val primarySign = if (settings.primaryFilterOffset >= 0) "+" else ""
        binding.textPrimaryFilterOffsetDisplay.text = "$primarySign${settings.primaryFilterOffset} Hz"
        
        // Secondary filter offset (-200 to +200 Hz)
        binding.seekBarSecondaryFilterOffset.progress = settings.secondaryFilterOffset + 200  // Convert to 0-based
        val secondarySign = if (settings.secondaryFilterOffset >= 0) "+" else ""
        binding.textSecondaryFilterOffsetDisplay.text = "$secondarySign${settings.secondaryFilterOffset} Hz"
        
        // LFO settings
        // LFO 1 Frequency (0.05-0.5 Hz)
        val lfo1Progress = ((settings.lfo1FrequencyHz - 0.05f) / 0.45f * 45).toInt().coerceIn(0, 45)
        binding.seekBarLfo1Frequency.progress = lfo1Progress
        val lfo1Description = when {
            settings.lfo1FrequencyHz < 0.1f -> "Very Slow"
            settings.lfo1FrequencyHz < 0.2f -> "Slow"
            settings.lfo1FrequencyHz < 0.3f -> "Moderate"
            settings.lfo1FrequencyHz < 0.4f -> "Fast"
            else -> "Very Fast"
        }
        binding.textLfo1FrequencyDisplay.text = "${String.format("%.2f", settings.lfo1FrequencyHz)} Hz ($lfo1Description)"
        
        // LFO 2 Frequency (0.05-0.5 Hz)
        val lfo2Progress = ((settings.lfo2FrequencyHz - 0.05f) / 0.45f * 45).toInt().coerceIn(0, 45)
        binding.seekBarLfo2Frequency.progress = lfo2Progress
        val lfo2Description = when {
            settings.lfo2FrequencyHz < 0.1f -> "Very Slow"
            settings.lfo2FrequencyHz < 0.2f -> "Slow"
            settings.lfo2FrequencyHz < 0.3f -> "Moderate"
            settings.lfo2FrequencyHz < 0.4f -> "Fast"
            else -> "Very Fast"
        }
        binding.textLfo2FrequencyDisplay.text = "${String.format("%.2f", settings.lfo2FrequencyHz)} Hz ($lfo2Description)"

        // Atmospheric Intensity (0.5-5.0)
        val atmosphericProgress = ((settings.atmosphericIntensity - 0.5f) * 10).toInt().coerceIn(0, 45)
        binding.atmosphericIntensitySeekBar.progress = atmosphericProgress
        val atmosphericDescription = when {
            settings.atmosphericIntensity < 1.0f -> "Minimal"
            settings.atmosphericIntensity < 2.0f -> "Light"
            settings.atmosphericIntensity < 3.0f -> "Moderate"
            settings.atmosphericIntensity < 4.0f -> "Strong"
            else -> "Extreme"
        }
        binding.atmosphericIntensityValue.text = "${String.format("%.1f", settings.atmosphericIntensity)} ($atmosphericDescription)"

        // Warmth (0-15 dB)
        val warmthProgress = (settings.warmth * 10).toInt().coerceIn(0, 150)
        binding.warmthSeekBar.progress = warmthProgress
        val warmthDescription = when {
            settings.warmth < 2.0f -> "Clean"
            settings.warmth < 4.0f -> "Neutral"
            settings.warmth < 6.0f -> "Slightly Warm"
            settings.warmth < 8.0f -> "Warm"
            settings.warmth < 10.0f -> "Rich"
            settings.warmth < 12.0f -> "Tube-like"
            else -> "Heavy Coloration"
        }
        binding.warmthValue.text = "${String.format("%.1f", settings.warmth)}dB ($warmthDescription)"

        // Filter ringing checkbox
        binding.checkBoxFilterRinging.isChecked = settings.filterRingingEnabled
        
        // Continuous noise testing checkbox
        binding.checkBoxContinuousNoise.isChecked = settings.continuousNoiseEnabled
        
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
        val levelChangeDelayMs = (binding.seekBarLevelChangeDelay.progress * 100)  // 0-5000ms
        
        // CW Filter settings
        val filterBandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val secondaryFilterBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val filterQ = (binding.seekBarFilterQ.progress / 10.0f) + 1.0f  // 1.0-20.0
        val backgroundNoise = binding.seekBarBackgroundNoise.progress / 100.0f  // 0.0-1.0
        val primaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200  // -200 to +200 Hz
        val secondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200  // -200 to +200 Hz
        
        // LFO settings
        val lfo1Freq = 0.05f + (binding.seekBarLfo1Frequency.progress / 45.0f * 0.45f)  // 0.05-0.5 Hz
        val lfo2Freq = 0.05f + (binding.seekBarLfo2Frequency.progress / 45.0f * 0.45f)  // 0.05-0.5 Hz
        
        // Atmospheric settings
        val atmosphericIntensity = (binding.atmosphericIntensitySeekBar.progress / 10.0f) + 0.5f  // 0.5-5.0
        val warmth = binding.warmthSeekBar.progress / 10.0f  // 0.0-15.0 dB
        val crackleIntensity = (binding.crackleIntensitySeekBar.progress / 1000.0f) + 0.01f  // 0.01-0.2
        val resonanceJumpRate = (binding.resonanceJumpRateSeekBar.progress / 100.0f) + 0.1f  // 0.1-2.0
        val driftSpeed = (binding.driftSpeedSeekBar.progress / 100.0f) + 0.1f  // 0.1-2.0
        
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
            levelChangeDelayMs = levelChangeDelayMs,
            mistakesToDropLevel = binding.seekBarMistakesToDrop.progress,
            
            // Audio settings
            toneFrequencyHz = binding.seekBarToneFrequency.progress * 10 + 300,
            farnsworthWpm = binding.seekBarFarnsworth.progress,
            wordSpacingMs = binding.seekBarWordSpacing.progress * 10,
            groupSpacingMs = binding.seekBarGroupSpacing.progress * 10,
            appVolumeLevel = binding.seekBarAppVolume.progress / 100.0f,
            audioEnvelopeMs = binding.seekBarAudioEnvelope.progress + 1,
            keyingStyle = binding.seekBarKeyingStyle.progress,
            
            // CW Filter settings
            filterBandwidthHz = filterBandwidth,
            secondaryFilterBandwidthHz = secondaryFilterBandwidth,
            filterQFactor = filterQ,
            backgroundNoiseLevel = backgroundNoise,
            noiseVolume = binding.seekBarNoiseVolume.progress / 100.0f,
            filterRingingEnabled = binding.checkBoxFilterRinging.isChecked,
            primaryFilterOffset = primaryOffset,
            secondaryFilterOffset = secondaryOffset,
            
            // CW LFO settings
            lfo1FrequencyHz = lfo1Freq,
            lfo2FrequencyHz = lfo2Freq,
            continuousNoiseEnabled = binding.checkBoxContinuousNoise.isChecked,
            
            // CW Atmospheric settings
            atmosphericIntensity = atmosphericIntensity,
            crackleIntensity = crackleIntensity,
            resonanceJumpRate = resonanceJumpRate, 
            driftSpeed = driftSpeed,
            warmth = warmth,
            
            // UI State settings
            lastExpandedSettingsTab = settings.lastExpandedSettingsTab
        )
        
        settings.save(requireContext())
    }
    
    private fun getCurrentSettings(): TrainingSettings {
        val speed = binding.seekBarSpeed.progress + 10  // 10-65 (updated for new range)
        val level = binding.seekBarLevel.progress + 1  // 1-40
        val groupMin = binding.seekBarGroupMin.progress + 1  // 1-9
        val groupMax = binding.seekBarGroupMax.progress + 1  // 1-9
        val repeatCount = binding.seekBarRepeatCount.progress + 1  // 1-10
        val repeatSpacingMs = (binding.seekBarRepeatSpacing.progress * 100)  // 0-10000ms (updated for new range)
        val timeout = binding.seekBarTimeout.progress + 1  // 1-31 (updated for new range)
        val requiredCorrect = binding.seekBarRequiredCorrect.progress + 1  // 1-30 (updated for new range)
        val sequenceDelayMs = (binding.seekBarSequenceDelay.progress * 100)  // 0-5000ms
        val levelChangeDelayMs = (binding.seekBarLevelChangeDelay.progress * 100)  // 0-5000ms
        
        // CW Filter settings
        val filterBandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val secondaryFilterBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100  // 100-2000 Hz
        val filterQ = (binding.seekBarFilterQ.progress / 10.0f) + 1.0f  // 1.0-20.0
        val backgroundNoise = binding.seekBarBackgroundNoise.progress / 100.0f  // 0.0-1.0
        val primaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200  // -200 to +200 Hz
        val secondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200  // -200 to +200 Hz
        
        // LFO settings
        val lfo1Freq = 0.05f + (binding.seekBarLfo1Frequency.progress / 45.0f * 0.45f)  // 0.05-0.5 Hz
        val lfo2Freq = 0.05f + (binding.seekBarLfo2Frequency.progress / 45.0f * 0.45f)  // 0.05-0.5 Hz
        
        // Atmospheric settings
        val atmosphericIntensity = (binding.atmosphericIntensitySeekBar.progress / 10.0f) + 0.5f  // 0.5-5.0
        val warmth = binding.warmthSeekBar.progress / 10.0f  // 0.0-15.0 dB
        val crackleIntensity = (binding.crackleIntensitySeekBar.progress / 1000.0f) + 0.01f  // 0.01-0.2
        val resonanceJumpRate = (binding.resonanceJumpRateSeekBar.progress / 100.0f) + 0.1f  // 0.1-2.0
        val driftSpeed = (binding.driftSpeedSeekBar.progress / 100.0f) + 0.1f  // 0.1-2.0
        
        return TrainingSettings(
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
            levelChangeDelayMs = levelChangeDelayMs,
            mistakesToDropLevel = binding.seekBarMistakesToDrop.progress,
            
            // Audio settings
            toneFrequencyHz = binding.seekBarToneFrequency.progress * 10 + 300,
            farnsworthWpm = binding.seekBarFarnsworth.progress,
            wordSpacingMs = binding.seekBarWordSpacing.progress * 10,
            groupSpacingMs = binding.seekBarGroupSpacing.progress * 10,
            appVolumeLevel = binding.seekBarAppVolume.progress / 100.0f,
            audioEnvelopeMs = binding.seekBarAudioEnvelope.progress + 1,
            keyingStyle = binding.seekBarKeyingStyle.progress,
            
            // CW Filter settings
            filterBandwidthHz = filterBandwidth,
            secondaryFilterBandwidthHz = secondaryFilterBandwidth,
            filterQFactor = filterQ,
            backgroundNoiseLevel = backgroundNoise,
            noiseVolume = binding.seekBarNoiseVolume.progress / 100.0f,
            filterRingingEnabled = binding.checkBoxFilterRinging.isChecked,
            primaryFilterOffset = primaryOffset,
            secondaryFilterOffset = secondaryOffset,
            
            // CW LFO settings
            lfo1FrequencyHz = lfo1Freq,
            lfo2FrequencyHz = lfo2Freq,
            continuousNoiseEnabled = binding.checkBoxContinuousNoise.isChecked,
            
            // CW Atmospheric settings
            atmosphericIntensity = atmosphericIntensity,
            crackleIntensity = crackleIntensity,
            resonanceJumpRate = resonanceJumpRate, 
            driftSpeed = driftSpeed,
            warmth = warmth,
            
            // UI State settings
            lastExpandedSettingsTab = settings.lastExpandedSettingsTab
        )
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
    
    private fun updateContinuousNoiseIfActive() {
        // Update continuous noise settings in real-time if it's currently playing
        if (binding.checkBoxContinuousNoise.isChecked) {
            morseCodeGenerator?.updateNoiseSettings(getCurrentSettings())
        }
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

    private fun showAudioResetConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Audio Settings")
            .setMessage("Are you sure you want to reset all audio settings (tone, volume, CW filtering, noise parameters) to defaults? Training progress and behavior settings will NOT be affected.")
            .setPositiveButton("Yes, Reset Audio") { _, _ ->
                // Reset only audio settings while preserving training progress
                settings = settings.resetAudioSettings()
                settings.save(requireContext())
                
                // Stop continuous noise if playing
                if (binding.checkBoxContinuousNoise.isChecked) {
                    morseCodeGenerator?.stopTestNoise()
                }
                
                // Reload the UI with new settings
                loadCurrentSettings()
                
                // Show success message
                AlertDialog.Builder(requireContext())
                    .setTitle("Audio Settings Reset")
                    .setMessage("All audio settings have been reset to defaults successfully. Training progress has been preserved.")
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

    /**
     * Calculate the overlap between two filters in Hz
     * Returns 0 if filters don't overlap, positive value if they do overlap
     */
    private fun calculateFilterOverlap(
        primaryOffset: Double,
        secondaryOffset: Double,
        primaryBandwidth: Double,
        secondaryBandwidth: Double
    ): Double {
        // Calculate -3dB points for each filter
        val primary3dbLow = primaryOffset - primaryBandwidth / 2.0
        val primary3dbHigh = primaryOffset + primaryBandwidth / 2.0
        val secondary3dbLow = secondaryOffset - secondaryBandwidth / 2.0
        val secondary3dbHigh = secondaryOffset + secondaryBandwidth / 2.0
        
        // Calculate overlap
        val overlapLow = max(primary3dbLow, secondary3dbLow)
        val overlapHigh = min(primary3dbHigh, secondary3dbHigh)
        val overlap = max(0.0, overlapHigh - overlapLow)
        
        android.util.Log.d("FilterConstraint", "Overlap: P=$primaryOffset($primaryBandwidth) S=$secondaryOffset($secondaryBandwidth) → $overlap Hz overlap")
        return overlap
    }

    private fun setupFilterOffsetListeners() {
        setupPrimaryOffsetListener()
        setupSecondaryOffsetListener()
    }
    
    private fun setupPrimaryOffsetListener() {
        binding.seekBarPrimaryFilterOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Calculate where primary wants to go
                    val desiredPrimaryOffset = progress - 200
                    
                    // Get current state
                    val currentSecondaryOffset = binding.seekBarSecondaryFilterOffset.progress - 200
                    val currentPrimaryBandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100
                    val currentSecondaryBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100
                    
                    // Calculate what combined bandwidth would be with desired primary position
                    val wouldBeCombinedBW = calculateFilterOverlap(
                        desiredPrimaryOffset.toDouble(),
                        currentSecondaryOffset.toDouble(),
                        currentPrimaryBandwidth.toDouble(),
                        currentSecondaryBandwidth.toDouble()
                    )
                    
                    val minOverlap = 50.0  // Minimum overlap in Hz
                    
                    android.util.Log.d("FilterConstraint", "Primary moving to $desiredPrimaryOffset, Secondary at $currentSecondaryOffset")
                    android.util.Log.d("FilterConstraint", "Would be overlap: $wouldBeCombinedBW Hz (min: $minOverlap Hz)")
                    if (wouldBeCombinedBW < minOverlap) {
                        android.util.Log.d("FilterConstraint", "CONSTRAINT TRIGGERED - dragging secondary")
                        // Find where secondary needs to be to maintain exactly 50Hz overlap
                        val requiredSecondaryOffset = calculateRequiredOffsetForOverlap(
                            desiredPrimaryOffset.toDouble(),
                            currentPrimaryBandwidth.toDouble(),
                            currentSecondaryBandwidth.toDouble(),
                            minOverlap,
                            currentSecondaryOffset.toDouble()
                        )
                        
                        android.util.Log.d("FilterConstraint", "Moving secondary from $currentSecondaryOffset to $requiredSecondaryOffset")
                        
                        // Update secondary filter position
                        binding.seekBarSecondaryFilterOffset.progress = (requiredSecondaryOffset + 200).toInt()
                    } else {
                        android.util.Log.d("FilterConstraint", "No constraint needed")
                    }
                }
                
                // Update primary display
                val primaryOffset = progress - 200
                val sign = if (primaryOffset >= 0) "+" else ""
                binding.textPrimaryFilterOffsetDisplay.text = "$sign$primaryOffset Hz"
                
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupSecondaryOffsetListener() {
        binding.seekBarSecondaryFilterOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Calculate where secondary wants to go
                    val desiredSecondaryOffset = progress - 200
                    
                    // Get current state
                    val currentPrimaryOffset = binding.seekBarPrimaryFilterOffset.progress - 200
                    val currentPrimaryBandwidth = binding.seekBarFilterBandwidth.progress * 10 + 100
                    val currentSecondaryBandwidth = binding.seekBarSecondaryFilterBandwidth.progress * 10 + 100
                    
                    // Calculate what combined bandwidth would be with desired secondary position
                    val wouldBeCombinedBW = calculateFilterOverlap(
                        currentPrimaryOffset.toDouble(),
                        desiredSecondaryOffset.toDouble(),
                        currentPrimaryBandwidth.toDouble(),
                        currentSecondaryBandwidth.toDouble()
                    )
                    
                    val minOverlap = 50.0  // Minimum overlap in Hz
                    
                    android.util.Log.d("FilterConstraint", "Primary moving to $currentPrimaryOffset, Secondary at $desiredSecondaryOffset")
                    android.util.Log.d("FilterConstraint", "Would be overlap: $wouldBeCombinedBW Hz (min: $minOverlap Hz)")
                    if (wouldBeCombinedBW < minOverlap) {
                        android.util.Log.d("FilterConstraint", "CONSTRAINT TRIGGERED - dragging primary")
                        // Find where primary needs to be to maintain exactly 50Hz overlap
                        val requiredPrimaryOffset = calculateRequiredOffsetForOverlap(
                            desiredSecondaryOffset.toDouble(),
                            currentSecondaryBandwidth.toDouble(),
                            currentPrimaryBandwidth.toDouble(),
                            minOverlap,
                            currentPrimaryOffset.toDouble()
                        )
                        
                        android.util.Log.d("FilterConstraint", "Moving primary from $currentPrimaryOffset to $requiredPrimaryOffset")
                        
                        // Update primary filter position
                        binding.seekBarPrimaryFilterOffset.progress = (requiredPrimaryOffset + 200).toInt()
                    } else {
                        android.util.Log.d("FilterConstraint", "No constraint needed")
                    }
                }
                
                // Update secondary display
                val secondaryOffset = progress - 200
                val sign = if (secondaryOffset >= 0) "+" else ""
                binding.textSecondaryFilterOffsetDisplay.text = "$sign$secondaryOffset Hz"
                
                if (fromUser) {
                    updateFilterGraph()
                    saveSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Calculate where the other filter offset needs to be to maintain minimum overlap
     * This function solves for the exact position needed for minimum overlap
     */
    private fun calculateRequiredOffsetForOverlap(
        fixedOffset: Double,
        fixedBandwidth: Double,
        movingBandwidth: Double,
        minOverlap: Double,
        currentMovingOffset: Double = 0.0
    ): Double {
        // Calculate the edges of the fixed filter
        val fixedLow = fixedOffset - fixedBandwidth / 2.0
        val fixedHigh = fixedOffset + fixedBandwidth / 2.0
        
        // For minimum overlap, we need the overlap region to be exactly minOverlap
        // Overlap = min(high1, high2) - max(low1, low2)
        
        // Option 1: Place moving filter so its high edge creates the overlap (moving filter to the left)
        // fixedHigh - movingLow = minOverlap
        // movingLow = fixedHigh - minOverlap
        // movingOffset = movingLow + movingBandwidth/2
        val leftOption = (fixedHigh - minOverlap) + movingBandwidth / 2.0
        
        // Option 2: Place moving filter so its low edge creates the overlap (moving filter to the right)
        // movingHigh - fixedLow = minOverlap
        // movingHigh = fixedLow + minOverlap
        // movingOffset = movingHigh - movingBandwidth/2
        val rightOption = (fixedLow + minOverlap) - movingBandwidth / 2.0
        
        // Choose the option that requires the smallest movement from current position
        val leftDistance = kotlin.math.abs(leftOption - currentMovingOffset)
        val rightDistance = kotlin.math.abs(rightOption - currentMovingOffset)
        
        val result = if (leftDistance <= rightDistance) leftOption else rightOption
        
        android.util.Log.d("FilterConstraint", "Overlap calc: fixed=$fixedOffset, current=$currentMovingOffset, left=$leftOption, right=$rightOption, chosen=$result")
        
        // Constrain to valid range
        return result.coerceIn(-200.0, 200.0)
    }
    
    private fun updateEnvelopeGraph() {
        val envelopeMs = binding.seekBarAudioEnvelope.progress + 1
        val keyingStyle = binding.seekBarKeyingStyle.progress
        val toneFrequency = binding.seekBarToneFrequency.progress * 10 + 300
        binding.envelopeGraph.updateEnvelope(envelopeMs, keyingStyle, toneFrequency)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any continuous noise when leaving settings
        if (::morseCodeGenerator.isInitialized) {
            morseCodeGenerator.stopTestNoise()
            morseCodeGenerator.release()
        }
    }
} 