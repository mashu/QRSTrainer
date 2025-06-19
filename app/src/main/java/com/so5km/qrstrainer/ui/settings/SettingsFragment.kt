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
        binding.seekbarSpeed.min = 5
        binding.seekbarSpeed.max = 50
        binding.seekbarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = maxOf(5, progress)
                binding.textSpeedValue.text = "$speed WPM"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Level settings
        binding.seekbarLevel.min = 1
        binding.seekbarLevel.max = MorseCode.getMaxLevel()
        binding.seekbarLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = maxOf(1, progress)
                binding.textLevelValue.text = "Level $level"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.checkboxLockLevel.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }

        // Group size settings
        binding.seekbarGroupMin.min = 1
        binding.seekbarGroupMin.max = 10
        binding.seekbarGroupMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val min = maxOf(1, progress)
                binding.textGroupMinValue.text = min.toString()
                
                // Ensure max is at least equal to min
                if (binding.seekbarGroupMax.progress < min) {
                    binding.seekbarGroupMax.progress = min
                }
                
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarGroupMax.min = 1
        binding.seekbarGroupMax.max = 15
        binding.seekbarGroupMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val max = maxOf(1, progress)
                binding.textGroupMaxValue.text = max.toString()
                
                // Ensure min is not greater than max
                if (binding.seekbarGroupMin.progress > max) {
                    binding.seekbarGroupMin.progress = max
                }
                
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Timing settings
        binding.seekbarTimeout.min = 3
        binding.seekbarTimeout.max = 30
        binding.seekbarTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val timeout = maxOf(3, progress)
                binding.textTimeoutValue.text = "${timeout}s"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarRepeat.min = 1
        binding.seekbarRepeat.max = 5
        binding.seekbarRepeat.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val repeat = maxOf(1, progress)
                binding.textRepeatValue.text = "${repeat}x"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Repeat spacing settings
        binding.seekbarRepeatSpacing.min = 5  // 0.5 seconds minimum
        binding.seekbarRepeatSpacing.max = 100  // 10 seconds maximum
        binding.seekbarRepeatSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val spacing = maxOf(5, progress) / 10.0  // Convert to seconds with 0.1s precision
                binding.textRepeatSpacingValue.text = "${String.format("%.1f", spacing)}s"
                if (fromUser) saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Progression settings
        binding.seekbarRequiredCorrect.min = 1
        binding.seekbarRequiredCorrect.max = 50
        binding.seekbarRequiredCorrect.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val required = maxOf(1, progress)
                binding.textRequiredCorrectValue.text = required.toString()
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
        binding.seekbarSpeed.progress = settings.speedWpm
        binding.textSpeedValue.text = "${settings.speedWpm} WPM"
        
        binding.seekbarLevel.progress = settings.kochLevel
        binding.textLevelValue.text = "Level ${settings.kochLevel}"
        
        binding.checkboxLockLevel.isChecked = settings.isLevelLocked
        
        binding.seekbarGroupMin.progress = settings.groupSizeMin
        binding.textGroupMinValue.text = settings.groupSizeMin.toString()
        
        binding.seekbarGroupMax.progress = settings.groupSizeMax
        binding.textGroupMaxValue.text = settings.groupSizeMax.toString()
        
        binding.seekbarTimeout.progress = settings.answerTimeoutSeconds
        binding.textTimeoutValue.text = "${settings.answerTimeoutSeconds}s"
        
        binding.seekbarRepeat.progress = settings.repeatCount
        binding.textRepeatValue.text = "${settings.repeatCount}x"
        
        binding.seekbarRepeatSpacing.progress = (settings.repeatSpacingMs / 100)  // Convert from ms to deciseconds
        binding.textRepeatSpacingValue.text = "${String.format("%.1f", settings.repeatSpacingMs / 1000.0)}s"
        
        binding.seekbarRequiredCorrect.progress = settings.requiredCorrectToAdvance
        binding.textRequiredCorrectValue.text = settings.requiredCorrectToAdvance.toString()
    }

    private fun saveSettings() {
        val repeatSpacingMs = maxOf(5, binding.seekbarRepeatSpacing.progress) * 100  // Convert deciseconds to ms
        
        settings = TrainingSettings(
            speedWpm = maxOf(5, binding.seekbarSpeed.progress),
            kochLevel = maxOf(1, binding.seekbarLevel.progress),
            isLevelLocked = binding.checkboxLockLevel.isChecked,
            groupSizeMin = maxOf(1, binding.seekbarGroupMin.progress),
            groupSizeMax = maxOf(1, binding.seekbarGroupMax.progress),
            answerTimeoutSeconds = maxOf(3, binding.seekbarTimeout.progress),
            repeatCount = maxOf(1, binding.seekbarRepeat.progress),
            repeatSpacingMs = repeatSpacingMs,
            requiredCorrectToAdvance = maxOf(1, binding.seekbarRequiredCorrect.progress)
        )
        
        settings.save(requireContext())
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_reset_progress))
            .setMessage(getString(R.string.settings_reset_confirmation))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                progressTracker.resetProgress()
                // Optionally show a confirmation message
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 