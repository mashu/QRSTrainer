package com.so5km.qrstrainer.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.so5km.qrstrainer.databinding.FragmentProgressBinding
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.AppState
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.CharacterStats
import com.so5km.qrstrainer.ui.common.AnimationUtils
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {
    
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var progressTracker: ProgressTracker
    private lateinit var characterStatsAdapter: CharacterStatsAdapter
    
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
        
        initializeComponents()
        setupRecyclerView()
        observeState()
        setupAnimations()
        loadProgressData()
    }
    
    private fun initializeComponents() {
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        progressTracker = ProgressTracker(requireContext())
        characterStatsAdapter = CharacterStatsAdapter()
    }
    
    private fun setupRecyclerView() {
        binding.recyclerCharacterStats.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = characterStatsAdapter
        }
        
        // Setup reset button
        binding.buttonResetProgress.setOnClickListener {
            showResetConfirmationDialog()
        }
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.settings.collect { settings ->
                updateLevelDisplay(settings.currentLevel)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.progressState.collect { progressState ->
                updateStreakDisplay(progressState.currentStreak)
                loadProgressData()
            }
        }
    }
    
    private fun loadProgressData() {
        updateProgressData(storeViewModel.state.value)
    }
    
    private fun updateProgressData(appState: AppState) {
        // Load all progress data from AppStore
        val currentLevel = appState.settings.currentLevel
        val currentStreak = appState.progressState.currentStreak
        val bestStreak = appState.progressState.bestStreak
        val requiredForNext = appState.settings.correctAnswersToLevelUp
        val allStats = appState.progressState.characterStats
        
        // Calculate level progress
        val levelProgress = if (requiredForNext > 0 && currentStreak > 0) {
            minOf(1.0f, currentStreak.toFloat() / requiredForNext)
        } else {
            0.0f
        }
        
        // Calculate overall statistics
        val totalAttempts = allStats.values.sumOf { it.attempts }
        val totalCorrect = allStats.values.sumOf { it.correct }
        val overallAccuracy = if (totalAttempts > 0) {
            (totalCorrect.toFloat() / totalAttempts * 100)
        } else 0f
        
        // Update UI - now using requiredForNext
        updateOverviewCards(currentLevel, currentStreak, bestStreak, overallAccuracy)
        updateProgressBar(levelProgress, totalAttempts, totalCorrect, requiredForNext)
        updateCharacterStats(allStats)
    }
    
    private fun updateLevelDisplay(level: Int) {
        binding.textCurrentLevel.text = "Level $level"
        val progress = progressTracker.getCurrentLevelProgress()
        binding.progressBar.progress = (progress * 100).toInt()
    }
    
    private fun updateStreakDisplay(streak: Int) {
        binding.textCurrentStreak.text = if (streak >= 0) {
            streak.toString()
        } else {
            "0" // Show 0 for negative streaks in UI, but track them internally
        }
    }
    
    private fun updateOverviewCards(level: Int, streak: Int, bestStreak: Int, accuracy: Float) {
        binding.apply {
            textCurrentLevel.text = "Level $level"
            textCurrentStreak.text = streak.toString()
            textBestStreak.text = bestStreak.toString()
            textAccuracy.text = String.format("%.1f%%", accuracy)
        }
    }
    
    private fun updateProgressBar(progress: Float, totalAttempts: Int, totalCorrect: Int, requiredForNext: Int) {
        binding.apply {
            progressBar.progress = (progress * 100).toInt()
            textTotalAttempts.text = "Total: $totalAttempts (Need: $requiredForNext)"
            textCorrectAnswers.text = "Correct: $totalCorrect"
        }
    }
    
    private fun updateCharacterStats(stats: Map<Char, CharacterStats>) {
        // Convert to list and sort by accuracy (worst first for focus)
        val statsList = stats.map { (char, stat) ->
            CharacterStatsItem(
                character = char,
                attempts = stat.attempts,
                correct = stat.correct,
                accuracy = stat.accuracy,
                averageTime = stat.averageResponseTime
            )
        }.sortedBy { it.accuracy }
        
        characterStatsAdapter.updateStats(statsList)
    }
    
    private fun setupAnimations() {
        // Staggered entrance animations for cards using centralized utility
        val cards = listOf(
            binding.cardLevel,
            binding.cardStreak,
            binding.cardAccuracy,
            binding.cardBestStreak,
            binding.cardOverview
        )
        
        AnimationUtils.animateEntranceStaggered(cards)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadProgressData()
    }
    
    private fun showResetConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset Progress")
            .setMessage("Are you sure you want to reset all progress? This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                storeViewModel.dispatch(AppAction.ResetProgress)
                androidx.core.content.ContextCompat.getMainExecutor(requireContext()).execute {
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "Progress has been reset",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Data class for character statistics display
data class CharacterStatsItem(
    val character: Char,
    val attempts: Int,
    val correct: Int,
    val accuracy: Float,
    val averageTime: Long
)

// RecyclerView adapter for character statistics
class CharacterStatsAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<CharacterStatsAdapter.ViewHolder>() {
    
    private var stats = listOf<CharacterStatsItem>()
    
    fun updateStats(newStats: List<CharacterStatsItem>) {
        stats = newStats
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.so5km.qrstrainer.R.layout.item_character_stat, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stats[position])
    }
    
    override fun getItemCount(): Int = stats.size
    
    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val characterText: android.widget.TextView = itemView.findViewById(com.so5km.qrstrainer.R.id.text_character)
        private val accuracyText: android.widget.TextView = itemView.findViewById(com.so5km.qrstrainer.R.id.text_accuracy)
        private val attemptsText: android.widget.TextView = itemView.findViewById(com.so5km.qrstrainer.R.id.text_attempts)
        private val correctText: android.widget.TextView = itemView.findViewById(com.so5km.qrstrainer.R.id.text_correct)
        private val avgTimeText: android.widget.TextView = itemView.findViewById(com.so5km.qrstrainer.R.id.text_avg_time)
        private val accuracyIndicator: View = itemView.findViewById(com.so5km.qrstrainer.R.id.accuracy_indicator)
        
        fun bind(item: CharacterStatsItem) {
            characterText.text = item.character.toString()
            accuracyText.text = String.format("%.1f%%", item.accuracy * 100)
            attemptsText.text = "Attempts: ${item.attempts}"
            correctText.text = "Correct: ${item.correct}"
            avgTimeText.text = "${item.averageTime / 1000.0}s"
            
            // Color code accuracy indicator
            val context = itemView.context
            val color = when {
                item.accuracy >= 0.9f -> androidx.core.content.ContextCompat.getColor(context, com.so5km.qrstrainer.R.color.success)
                item.accuracy >= 0.7f -> androidx.core.content.ContextCompat.getColor(context, com.so5km.qrstrainer.R.color.warning)
                else -> androidx.core.content.ContextCompat.getColor(context, com.so5km.qrstrainer.R.color.md_theme_light_error)
            }
            accuracyIndicator.setBackgroundColor(color)
        }
    }
}
