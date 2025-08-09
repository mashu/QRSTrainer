package com.so5km.qrstrainer.ui.settings.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.getLetterOrder
 

class AlphabetEditorDialog(
    private val initialSettings: TrainingSettings,
    private val onSave: (lettersOrder: String, extraChars: String) -> Unit
) : DialogFragment() {

    private lateinit var recyclerGrid: RecyclerView
    private lateinit var recyclerExtraGrid: RecyclerView
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var cancelButton: Button
    // Preset selection moved to Settings screen

    private val enabledItems = mutableListOf<AlphabetItem>()
    private val availableItems = mutableListOf<AlphabetItem>()

    data class AlphabetItem(val char: Char, var enabled: Boolean)
    private val extraItems = mutableListOf<AlphabetItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.setTitle("Edit alphabet order")
        val view = inflater.inflate(R.layout.dialog_alphabet_editor, container, false)

        recyclerGrid = view.findViewById(R.id.recyclerAlphabetGrid)
        recyclerExtraGrid = view.findViewById(R.id.recyclerExtraCharsGrid)
        saveButton = view.findViewById(R.id.buttonSaveAlphabet)
        clearButton = view.findViewById(R.id.buttonClearAlphabet)
        cancelButton = view.findViewById(R.id.buttonCancelAlphabet)
        setupList()
        setupButtons()

        return view
    }

    override fun onStart() {
        super.onStart()
        // Make the dialog wider on phone screens (~92% width)
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels * 0.92f).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun setupList() {
        val currentOrder = initialSettings.getLetterOrder()
        val sanitizedOverride: List<Char> = initialSettings.alphabetOrderOverride
            .uppercase()
            .asSequence()
            .filter { it in 'A'..'Z' }
            .toList()
        val enabledSet: Set<Char> = if (sanitizedOverride.isNotEmpty()) {
            sanitizedOverride.toSet()
        } else {
            currentOrder.toSet()
        }

        val allLetters = ('A'..'Z').toList()
        val extraChars = mutableListOf<Char>().apply {
            ('0'..'9').forEach { add(it) }
            listOf('.', ',', '?', '/', '=', '+', '-').forEach { add(it) }
        }
        val ordered = if (sanitizedOverride.isNotEmpty()) {
            val overrideList = sanitizedOverride
            overrideList + allLetters.filter { it !in overrideList }
        } else {
            currentOrder + allLetters.filter { it !in currentOrder }
        }

        enabledItems.clear()
        availableItems.clear()
        ordered.forEach { ch -> enabledItems.add(AlphabetItem(ch, ch in enabledSet)) }

        // Initialize extra items enablement based on settings
        // Determine enabled subsets for digits/punctuation respecting custom subsets
        val enabledExtra = mutableSetOf<Char>().apply {
            // Numbers
            if (initialSettings.customNumbers.isNotEmpty()) {
                initialSettings.customNumbers.forEach { add(it) }
            } else if (initialSettings.useNumbers) {
                ('0'..'9').forEach { add(it) }
            }
            // Punctuation
            val punctAll = listOf('.', ',', '?', '/', '=', '+', '-')
            if (initialSettings.customPunctuation.isNotEmpty()) {
                initialSettings.customPunctuation.forEach { add(it) }
            } else if (initialSettings.usePunctuation) {
                punctAll.forEach { add(it) }
            }
            // Also include any chars from customCharacterSet (if used elsewhere)
            initialSettings.customCharacterSet.forEach { c -> add(c) }
        }
        extraItems.clear()
        extraChars.forEach { ch -> extraItems.add(AlphabetItem(ch, ch in enabledExtra)) }

        val gridAdapter = GridChipsAdapter(enabledItems) { index ->
            enabledItems[index].enabled = !enabledItems[index].enabled
            recyclerGrid.adapter?.notifyItemChanged(index)
        }
        val extraAdapter = GridChipsAdapter(extraItems) { index ->
            extraItems[index].enabled = !extraItems[index].enabled
            recyclerExtraGrid.adapter?.notifyItemChanged(index)
        }
        // Use Flexbox for natural wrapping and reflow while dragging; fall back to grid if unavailable
        // Use fixed columns for equal chip widths
        val columns = 5
        recyclerGrid.layoutManager = GridLayoutManager(requireContext(), columns)
        recyclerExtraGrid.layoutManager = GridLayoutManager(requireContext(), columns)
        recyclerGrid.adapter = gridAdapter
        recyclerExtraGrid.adapter = extraAdapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from in enabledItems.indices && to in enabledItems.indices) {
                    val moved = enabledItems.removeAt(from)
                    enabledItems.add(to, moved)
                    recyclerGrid.adapter?.notifyItemMoved(from, to)
                }
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        })
        touchHelper.attachToRecyclerView(recyclerGrid)
    }

    private fun setupButtons() {
        saveButton.setOnClickListener {
            // Persist only enabled characters in the current order
            val enabledOrder = buildString {
                enabledItems.forEach { item -> if (item.enabled) append(item.char) }
            }
            val enabledExtras = buildString {
                extraItems.forEach { item -> if (item.enabled) append(item.char) }
            }
            onSave(enabledOrder, enabledExtras)
            dismiss()
        }
        clearButton.setOnClickListener {
            enabledItems.replaceAll { it.copy(enabled = false) }
            recyclerGrid.adapter?.notifyDataSetChanged()
            extraItems.replaceAll { it.copy(enabled = false) }
            recyclerExtraGrid.adapter?.notifyDataSetChanged()
        }
        cancelButton.setOnClickListener { dismiss() }
    }

    // No preset dropdown here; handled by Settings screen

    private class GridChipsAdapter(
        private val items: List<AlphabetItem>,
        private val onChipClick: (Int) -> Unit
    ) : RecyclerView.Adapter<GridChipsAdapter.VH>() {
        class VH(val chip: com.google.android.material.chip.Chip) : RecyclerView.ViewHolder(chip)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val chip = com.google.android.material.chip.Chip(parent.context)
            // Grid item should fill span width
            val lp = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val density = parent.resources.displayMetrics.density
            val marginPx = (2f * density).toInt()
            lp.setMargins(marginPx, marginPx, marginPx, marginPx)
            chip.layoutParams = lp
            chip.isClickable = true
            chip.isCheckable = false
            chip.setEnsureMinTouchTargetSize(false)
            val d = parent.resources.displayMetrics.density
            chip.minWidth = 0
            chip.minHeight = (36 * d).toInt()
            val padH = (8f * d).toInt()
            val padV = (6f * d).toInt()
            chip.setPadding(padH, padV, padH, padV)
            chip.textAlignment = View.TEXT_ALIGNMENT_CENTER
            chip.gravity = android.view.Gravity.CENTER
            chip.textSize = 12f
            return VH(chip)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.chip.text = item.char.toString()
            // Remove stroke; use clear background for disabled to avoid confusion
            holder.chip.chipStrokeWidth = 0f
            if (item.enabled) {
                holder.chip.setChipBackgroundColorResource(com.so5km.qrstrainer.R.color.md_theme_light_primary)
                holder.chip.setTextColor(android.graphics.Color.WHITE)
            } else {
                holder.chip.setChipBackgroundColorResource(android.R.color.transparent)
                holder.chip.setTextColor(android.graphics.Color.DKGRAY)
            }
            holder.chip.setOnClickListener { onChipClick(position) }
        }
        override fun getItemCount(): Int = items.size
    }
}


