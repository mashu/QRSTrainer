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
        }
        addView(chipGroup)
    }

    /**
     * Set the characters available for selection
     */
    fun setAvailableCharacters(characters: Set<Char>) {
        enabledCharacters = characters
        populateKeyboard()
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
                view.setChipBackgroundColorResource(R.color.success)
                view.setTextColor(ContextCompat.getColor(context, R.color.on_success_container))
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
                        view.setChipBackgroundColorResource(R.color.success)
                        view.setTextColor(ContextCompat.getColor(context, R.color.on_success_container))
                    }
                }
            }
        }
    }

    /**
     * Reset all chips to default state
     */
    fun resetState() {
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
        chipGroup.removeAllViews()
        
        // Get all morse code characters in order - using a simple character set for now
        val allCharacters = ('A'..'Z').toList().filter { it in enabledCharacters }
        
        // Group characters by rows for better layout
        val characterRows = groupCharactersIntoRows(allCharacters)
        
        characterRows.forEach { rowCharacters ->
            createRowOfChips(rowCharacters)
        }
    }

    private fun groupCharactersIntoRows(characters: List<Char>): List<List<Char>> {
        // Group characters in logical rows
        val commonLetters = listOf('E', 'T', 'A', 'I', 'N', 'O', 'S', 'H', 'R')
        val numbers = ('0'..'9').toList()
        val otherLetters = ('A'..'Z').filter { it !in commonLetters }
        
        val rows = mutableListOf<List<Char>>()
        
        // Row 1: Most common letters
        val row1 = characters.filter { it in commonLetters }.take(5)
        if (row1.isNotEmpty()) rows.add(row1)
        
        // Row 2: Remaining common letters
        val row2 = characters.filter { it in commonLetters }.drop(5)
        if (row2.isNotEmpty()) rows.add(row2)
        
        // Row 3: Other letters
        val row3 = characters.filter { it in otherLetters }.take(5)
        if (row3.isNotEmpty()) rows.add(row3)
        
        // Row 4: More letters
        val row4 = characters.filter { it in otherLetters }.drop(5)
        if (row4.isNotEmpty()) rows.add(row4)
        
        // Row 5: Numbers
        val numbersInSet = characters.filter { it in numbers }
        if (numbersInSet.isNotEmpty()) rows.add(numbersInSet)
        
        return rows
    }

    private fun createRowOfChips(characters: List<Char>) {
        val rowGroup = ChipGroup(context).apply {
            chipSpacingHorizontal = context.resources.getDimensionPixelSize(R.dimen.chip_spacing_horizontal)
            chipSpacingVertical = context.resources.getDimensionPixelSize(R.dimen.chip_spacing_vertical)
            isSingleSelection = false
        }

        characters.forEach { char ->
            val chip = createChip(char)
            rowGroup.addView(chip)
        }

        chipGroup.addView(rowGroup)
    }

    private fun createChip(character: Char): Chip {
        return Chip(context).apply {
            text = character.toString()
            textSize = 16f
            isCheckable = false
            isClickable = true
            isFocusable = true
            
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
        // Reset all chips to default state first
        resetState()
        
        // Highlight selected chip
        chipGroup.children.forEach { rowGroup ->
            if (rowGroup is ChipGroup) {
                rowGroup.children.forEach { view ->
                    if (view is Chip && view.text == character.toString()) {
                        view.setChipBackgroundColorResource(R.color.md_theme_light_primaryContainer)
                        view.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onPrimaryContainer))
                        selectedCharacter = character
                    }
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