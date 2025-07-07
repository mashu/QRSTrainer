package com.so5km.qrstrainer.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.so5km.qrstrainer.databinding.FragmentProgressBinding

class ProgressFragment : Fragment() {
    
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupOverviewCards()
        setupCharacterStatsList()
        setupAnimations()
        loadSampleData()
    }
    
    private fun setupOverviewCards() {
        // Display current level, streak, accuracy etc.
        // This will be implemented with your existing ProgressTracker
        binding.apply {
            textCurrentLevel?.text = "Level 1"
            textCurrentStreak?.text = "5"
            textAccuracy?.text = "85%"
            textBestStreak?.text = "15"
            textTotalAttempts?.text = "Total: 150"
            textCorrectAnswers?.text = "Correct: 128"
            progressBar?.progress = 75
        }
    }
    
    private fun setupCharacterStatsList() {
        // Show individual character statistics
        // RecyclerView with character performance data
        binding.recyclerCharacterStats?.apply {
            layoutManager = LinearLayoutManager(context)
            // adapter = CharacterStatsAdapter() // You'll implement this later
        }
    }
    
    private fun setupAnimations() {
        // Staggered entrance animations for cards
        val cards = listOf(
            binding.cardLevel,
            binding.cardStreak,
            binding.cardAccuracy,
            binding.cardBestStreak,
            binding.cardOverview
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
    
    private fun loadSampleData() {
        // Load sample progress data for demonstration
        // This will integrate with your existing ProgressTracker
        binding.apply {
            textCurrentLevel?.text = "Level 2"
            textCurrentStreak?.text = "8"
            textAccuracy?.text = "87.5%"
            textBestStreak?.text = "23"
            textTotalAttempts?.text = "Total: 245"
            textCorrectAnswers?.text = "Correct: 214"
            progressBar?.progress = 68
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

