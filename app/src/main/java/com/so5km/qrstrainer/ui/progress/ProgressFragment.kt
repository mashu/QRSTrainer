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
        updateStatistics()

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        updateStatistics()
    }

    private fun setupRecyclerView() {
        adapter = CharacterStatsAdapter()
        binding.recyclerViewStats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewStats.adapter = adapter
    }

    private fun updateStatistics() {
        // Update character statistics only (removed overall stats from UI)
        updateCharacterStatistics()
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