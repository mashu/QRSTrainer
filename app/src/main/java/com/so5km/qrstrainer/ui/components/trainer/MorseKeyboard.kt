package com.so5km.qrstrainer.ui.components.trainer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.MorseCode

/**
 * Modern Material 3 Morse Keyboard component using chips
 */
class MorseKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var chipGroup: ChipGroup
    private var onCharacterClickListener: ((Char) -> Unit)? = null
    private var enabledCharacters = setOf<Char>()
    private var selectedCharacter: Char? = null

    init {
        orientation = VERTICAL
        chipGroup = ChipGroup(context).apply {
            chipSpacingHorizontal = context.resources.getDimensionPixelSize(R.dimen.chip_spacing_horizontal)
            chipSpacingVertical = context.resources.getDimensionPixelSize(R.dimen.chip_spacing_vertical)
            isSingleSelection = false
            isSelectionRequired = false
            // Improved layout for larger alphabets
            setSingleLine(false)
            // ChipGroup automatically handles multi-line wrapping when setSingleLine(false)
        }
        addView(chipGroup)
    }

    /**
     * Set the characters available for selection
     */
    fun setAvailableCharacters(characters: Set<Char>) {
        android.util.Log.d("MorseKeyboard", "setAvailableCharacters: new=$characters, current=$enabledCharacters")
        // Only repopulate if characters actually changed
        if (characters != enabledCharacters) {
            android.util.Log.d("MorseKeyboard", "REBUILDING KEYBOARD: characters changed")
            enabledCharacters = characters
            populateKeyboard()
        } else {
            android.util.Log.d("MorseKeyboard", "Characters unchanged, keeping keyboard")
        }
    }
    
    /**
     * Check if the keyboard has the given characters available
     */
    fun hasCharacters(characters: Set<Char>): Boolean {
        return enabledCharacters == characters
    }

    /**
     * Set the listener for character clicks
     */
    fun setOnCharacterClickListener(listener: (Char) -> Unit) {
        onCharacterClickListener = listener
    }

    /**
     * Highlight the correct answer
     */
    fun showCorrectAnswer(character: Char) {
        chipGroup.children.forEach { view ->
            if (view is Chip && view.text == character.toString()) {
                view.setChipBackgroundColorResource(R.color.md_theme_light_secondaryContainer)
                view.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSecondaryContainer))
            }
        }
    }

    /**
     * Show incorrect selection
     */
    fun showIncorrectAnswer(selectedChar: Char, correctChar: Char) {
        chipGroup.children.forEach { view ->
            if (view is Chip) {
                when (view.text.toString().firstOrNull()) {
                    selectedChar -> {
                        view.setChipBackgroundColorResource(R.color.md_theme_light_error)
                        view.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onError))
                    }
                    correctChar -> {
                        view.setChipBackgroundColorResource(R.color.md_theme_light_secondaryContainer)
                        view.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSecondaryContainer))
                    }
                }
            }
        }
    }

    /**
     * Reset all chips to default state
     */
    fun resetState() {
        android.util.Log.d("MorseKeyboard", "resetState() called")
        selectedCharacter = null
        chipGroup.children.forEach { view ->
            if (view is Chip) {
                resetChipToDefault(view)
            }
        }
    }



    /**
     * Enable or disable the keyboard
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        chipGroup.children.forEach { view ->
            view.isEnabled = enabled
        }
    }

    private fun populateKeyboard() {
        android.util.Log.d("MorseKeyboard", "populateKeyboard() called - KEYBOARD WILL DISAPPEAR/REAPPEAR")
        chipGroup.removeAllViews()
        
        // Organize characters by type for better layout
        val letters = enabledCharacters.filter { it.isLetter() }.sorted()
        val numbers = enabledCharacters.filter { it.isDigit() }.sorted()
        val punctuation = enabledCharacters.filter { it in listOf('.', ',', '?', '/', '=', '+', '-') }.sorted()
        val prosigns = enabledCharacters.filter { it in listOf('<', '>', '@') }.sorted()
        val custom = enabledCharacters.filter { 
            !it.isLetter() && !it.isDigit() && it !in listOf('.', ',', '?', '/', '=', '+', '-', '<', '>', '@') 
        }.sorted()
        
        // Create chips in order: letters, numbers, punctuation, prosigns, custom
        val allCharsInOrder = letters + numbers + punctuation + prosigns + custom
        
        allCharsInOrder.forEach { char ->
            val chip = createChip(char)
            chipGroup.addView(chip)
        }
        
        android.util.Log.d("MorseKeyboard", "populateKeyboard() completed with ${allCharsInOrder.size} characters")
    }



    private fun createChip(character: Char): Chip {
        return Chip(context).apply {
            text = character.toString()
            textSize = 14f  // Reduced from 16f for better space utilization
            isCheckable = false
            isClickable = true
            isFocusable = true
            
            // Make chips more compact
            minHeight = context.resources.getDimensionPixelSize(R.dimen.chip_min_height)
            
            // Apply Material 3 styling
            setChipBackgroundColorResource(R.color.md_theme_light_surface)
            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
            chipStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.chip_stroke_width).toFloat()
            chipStrokeColor = ContextCompat.getColorStateList(context, R.color.md_theme_light_outline)
            
            setOnClickListener {
                handleChipClick(character)
            }
        }
    }

    private fun handleChipClick(character: Char) {
        // Reset all chips to default state first, then highlight selected chip
        chipGroup.children.forEach { view ->
            if (view is Chip) {
                if (view.text == character.toString()) {
                    // Highlight selected chip
                    view.setChipBackgroundColorResource(R.color.md_theme_light_primaryContainer)
                    view.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onPrimaryContainer))
                    selectedCharacter = character
                } else {
                    // Reset other chips to default
                    resetChipToDefault(view)
                }
            }
        }
        
        onCharacterClickListener?.invoke(character)
    }

    private fun resetChipToDefault(chip: Chip) {
        chip.setChipBackgroundColorResource(R.color.md_theme_light_surface)
        chip.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
    }
}