package com.so5km.qrstrainer.ui.settings.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.getLetterOrder

class AlphabetEditorDialog(
    private val initialSettings: TrainingSettings,
    private val onSave: (String) -> Unit
) : DialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var cancelButton: Button

    private val items = mutableListOf<AlphabetItem>()
    private lateinit var adapter: AlphabetAdapter

    data class AlphabetItem(val char: Char, var enabled: Boolean)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.setTitle("Edit alphabet order")
        val view = inflater.inflate(R.layout.dialog_alphabet_editor, container, false)

        recyclerView = view.findViewById(R.id.recyclerAlphabet)
        saveButton = view.findViewById(R.id.buttonSaveAlphabet)
        clearButton = view.findViewById(R.id.buttonClearAlphabet)
        cancelButton = view.findViewById(R.id.buttonCancelAlphabet)

        setupList()
        setupButtons()

        return view
    }

    private fun setupList() {
        val currentOrder = initialSettings.getLetterOrder()
        val enabledSet = if (initialSettings.alphabetOrderOverride.isNotEmpty()) {
            initialSettings.alphabetOrderOverride.toSet()
        } else {
            currentOrder.toSet()
        }

        // Build items from current order and append any missing letters
        val allLetters = ('A'..'Z').toList()
        val ordered = currentOrder + allLetters.filter { it !in currentOrder }
        items.clear()
        items.addAll(ordered.map { AlphabetItem(it, it in enabledSet) })

        adapter = AlphabetAdapter(items) { position, isChecked ->
            items[position].enabled = isChecked
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from in items.indices && to in items.indices) {
                    val moved = items.removeAt(from)
                    items.add(to, moved)
                    adapter.notifyItemMoved(from, to)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupButtons() {
        saveButton.setOnClickListener {
            val result = buildString {
                items.filter { it.enabled }.forEach { append(it.char) }
            }
            onSave(result)
            dismiss()
        }
        clearButton.setOnClickListener {
            items.forEach { it.enabled = false }
            adapter.notifyItemRangeChanged(0, items.size)
        }
        cancelButton.setOnClickListener { dismiss() }
    }

    private class AlphabetAdapter(
        private val items: List<AlphabetItem>,
        private val onToggle: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<AlphabetAdapter.VH>() {

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val label: TextView = itemView.findViewById(R.id.textChar)
            val check: CheckBox = itemView.findViewById(R.id.checkEnabled)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alphabet_char, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.text = item.char.toString()
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = item.enabled
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                onToggle(holder.bindingAdapterPosition, isChecked)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}


