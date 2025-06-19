package com.so5km.qrstrainer.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.CharacterStats
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.databinding.ItemCharacterStatBinding

class CharacterStatsAdapter : RecyclerView.Adapter<CharacterStatsAdapter.ViewHolder>() {
    
    private var stats: List<CharacterStats> = emptyList()

    class ViewHolder(private val binding: ItemCharacterStatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stats: CharacterStats) {
            binding.textCharacter.text = stats.character.toString()
            binding.textMorsePattern.text = MorseCode.getPattern(stats.character)
            binding.textAccuracy.text = "Accuracy: ${String.format("%.1f", stats.accuracy)}%"
            binding.textCorrectCount.text = "✓ ${stats.correctCount}"
            binding.textIncorrectCount.text = "✗ ${stats.incorrectCount}"
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