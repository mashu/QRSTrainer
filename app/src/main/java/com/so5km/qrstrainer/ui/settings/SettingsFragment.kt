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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
        setupCollapsibleSections()
        loadCurrentSettings()

        return root
    }

    private fun setupCollapsibleSections() {
        // Set initial state - Training Settings expanded, others collapsed
        binding.contentTrainingSettings.visibility = View.VISIBLE
        binding.iconTrainingExpand.rotation = 90f
        
        binding.contentAudioSettings.visibility = View.GONE
        binding.iconAudioExpand.rotation = 0f
        
        binding.contentCWFilterSettings.visibility = View.GONE
        binding.iconCWFilterExpand.rotation = 0f
        
        binding.contentProgressSettings.visibility = View.GONE
        binding.iconProgressExpand.rotation = 0f

        // Training Settings
        binding.headerTrainingSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentTrainingSettings,
                binding.iconTrainingExpand,
                "training"
            )
        }

        // Audio Settings
        binding.headerAudioSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentAudioSettings,
                binding.iconAudioExpand,
                "audio"
            )
        }

        // CW Filter Settings
        binding.headerCWFilterSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentCWFilterSettings,
                binding.iconCWFilterExpand,
                "filter"
            )
        }

        // Progress Settings
        binding.headerProgressSettings.setOnClickListener {
            toggleSectionAccordion(
                binding.contentProgressSettings,
                binding.iconProgressExpand,
                "progress"
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
        } else {
            // Collapse
            contentView.visibility = View.GONE
            iconView.rotation = 0f
        }
    }
    
    private fun collapseAllSectionsExcept(exceptSection: String) {
        if (exceptSection != "training") {
            binding.contentTrainingSettings.visibility = View.GONE
            binding.iconTrainingExpand.rotation = 0f
        }
        if (exceptSection != "audio") {
            binding.contentAudioSettings.visibility = View.GONE
            binding.iconAudioExpand.rotation = 0f
        }
        if (exceptSection != "filter") {
            binding.contentCWFilterSettings.visibility = View.GONE
            binding.iconCWFilterExpand.rotation = 0f
        }
        if (exceptSection != "progress") {
            binding.contentProgressSettings.visibility = View.GONE
            binding.iconProgressExpand.rotation = 0f
        }
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
        // Filter bandwidth (100-2000 Hz) with real-time combined bandwidth constraint
        binding.seekBarFilterBandwidth.max = 190  // 100-2000 Hz
        binding.seekBarFilterBandwidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bandwidth = progress * 10 + 100  // 100-2000 Hz
                binding.textFilterBandwidthDisplay.text = "$bandwidth Hz"
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

        // Primary filter offset (-200 to +200 Hz) with real-time combined bandwidth constraint
        binding.seekBarPrimaryFilterOffset.max = 400  // -200 to +200 Hz
        
        // Secondary filter offset (-200 to +200 Hz) with real-time combined bandwidth constraint
        binding.seekBarSecondaryFilterOffset.max = 400  // -200 to +200 Hz
        
        // Set up the constraint system for filter offsets
        setupFilterOffsetListeners()

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
        
        // Load mistakes to drop level settings
        binding.seekBarMistakesToDrop.progress = settings.mistakesToDropLevel  // Already 0-based
        val mistakeDisplayText = if (settings.mistakesToDropLevel == 0) "0 mistakes (disabled)" else "${settings.mistakesToDropLevel} mistake${if (settings.mistakesToDropLevel == 1) "" else "s"} (enabled)"
        binding.textMistakesToDropDisplay.text = mistakeDisplayText
        
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
            mistakesToDropLevel = binding.seekBarMistakesToDrop.progress,
            
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
} 