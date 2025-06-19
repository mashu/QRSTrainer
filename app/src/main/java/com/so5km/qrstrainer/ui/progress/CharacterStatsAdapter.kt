package com.so5km.qrstrainer.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.CharacterStats
import com.so5km.qrstrainer.databinding.ItemCharacterStatBinding

class CharacterStatsAdapter : RecyclerView.Adapter<CharacterStatsAdapter.ViewHolder>() {
    
    private var stats: List<CharacterStats> = emptyList()

    class ViewHolder(private val binding: ItemCharacterStatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stats: CharacterStats) {
            binding.textCharacter.text = stats.character.toString()
            binding.textAccuracy.text = binding.root.context.getString(
                R.string.accuracy_label, 
                stats.accuracy
            )
            binding.textCorrect.text = binding.root.context.getString(
                R.string.correct_label, 
                stats.correctCount
            )
            binding.textIncorrect.text = binding.root.context.getString(
                R.string.incorrect_label, 
                stats.incorrectCount
            )
            binding.textWeight.text = "Weight: ${"%.1f".format(stats.weight)}"
            
            // Set progress bar
            binding.progressAccuracy.progress = stats.accuracy.toInt()
            
            // Color coding based on accuracy
            val backgroundColor = when {
                stats.totalAttempts == 0 -> "#EEEEEE"
                stats.accuracy >= 90 -> "#C8E6C9" // Light green
                stats.accuracy >= 70 -> "#FFF9C4" // Light yellow
                else -> "#FFCDD2" // Light red
            }
            
            binding.textCharacter.setBackgroundColor(
                android.graphics.Color.parseColor(backgroundColor)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacterStatBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(stats[position])
    }

    override fun getItemCount(): Int = stats.size

    fun updateStats(newStats: List<CharacterStats>) {
        stats = newStats
        notifyDataSetChanged()
    }
} 