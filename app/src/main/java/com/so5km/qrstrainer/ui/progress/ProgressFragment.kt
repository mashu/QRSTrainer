package com.so5km.qrstrainer.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentProgressBinding

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var progressTracker: ProgressTracker
    private lateinit var settings: TrainingSettings
    private lateinit var adapter: CharacterStatsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        val root: View = binding.root

        progressTracker = ProgressTracker(requireContext())
        settings = TrainingSettings.load(requireContext())
        
        setupRecyclerView()
        updateOverallStatistics()
        updateCharacterStatistics()
        
        binding.buttonResetProgress.setOnClickListener {
            resetProgress()
        }

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Reload settings when returning to progress screen
        settings = TrainingSettings.load(requireContext())
        updateOverallStatistics()
        updateCharacterStatistics()
    }

    private fun setupRecyclerView() {
        adapter = CharacterStatsAdapter()
        binding.recyclerViewStats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewStats.adapter = adapter
    }

    private fun resetProgress() {
        progressTracker.resetProgress()
        updateOverallStatistics()
        updateCharacterStatistics()
    }

    private fun updateOverallStatistics() {
        // Overall accuracy across all characters
        val overallAccuracy = progressTracker.getOverallAccuracy()
        binding.textOverallAccuracy.text = String.format("%.1f%%", overallAccuracy)
        
        // Overall average response time
        val overallResponseTime = progressTracker.getOverallAverageResponseTime()
        if (overallResponseTime > 0) {
            binding.textResponseTime.text = String.format("%.1f ms", overallResponseTime)
            binding.textResponseTime.visibility = View.VISIBLE
        } else {
            binding.textResponseTime.visibility = View.GONE
        }
        
        // Session statistics
        val sessionCorrect = progressTracker.sessionCorrect
        val sessionTotal = progressTracker.sessionTotal
        binding.textSessionStats.text = "$sessionCorrect/$sessionTotal"
        
        // Current streak
        val currentStreak = progressTracker.sessionStreak
        binding.textCurrentStreak.text = currentStreak.toString()
    }

    private fun updateCharacterStatistics() {
        val availableChars = MorseCode.getCharactersForLevel(settings.kochLevel)
        val characterStatsList = availableChars.map { char ->
            progressTracker.getCharacterStats(char)
        }.sortedByDescending { it.totalAttempts } // Sort by most practiced first
        
        adapter.updateStats(characterStatsList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 