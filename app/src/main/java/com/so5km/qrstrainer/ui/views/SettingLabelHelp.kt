package com.so5km.qrstrainer.ui.views

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.so5km.qrstrainer.R

/**
 * Simple utility class for adding help to setting labels
 */
object SettingLabelHelp {

    /**
     * Add help to a setting by finding and enhancing its label
     *
     * @param container The container view that holds the setting (usually a LinearLayout)
     * @param title The title for the help dialog
     * @param message The message to show in the help dialog
     */
    fun addHelpToSetting(container: ViewGroup, title: String, message: String) {
        // Find the label TextView in the container
        val label = findSettingLabel(container)
        
        if (label != null) {
            // Make the label visually indicate it has help
            enhanceLabelWithHelp(label, title, message)
        }
    }
    
    /**
     * Find the label TextView in a setting container
     *
     * @param container The container view
     * @return The label TextView or null if not found
     */
    private fun findSettingLabel(container: ViewGroup): TextView? {
        // Look for a TextView that's likely to be a label (usually the first TextView)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {
                return child
            } else if (child is ViewGroup) {
                // Look one level deeper
                val deepLabel = findSettingLabel(child)
                if (deepLabel != null) {
                    return deepLabel
                }
            }
        }
        return null
    }
    
    /**
     * Enhance a label TextView to indicate it has help and make it clickable
     *
     * @param label The label TextView
     * @param title The title for the help dialog
     * @param message The message to show in the help dialog
     */
    private fun enhanceLabelWithHelp(label: TextView, title: String, message: String) {
        // Add a subtle indicator to the end of the text
        val originalText = label.text.toString()
        if (!originalText.endsWith(" ℹ")) {
            val newText = "$originalText ℹ"
            val spannableString = SpannableString(newText)
            
            // Make just the info icon a subtle blue color
            val infoIconColor = ContextCompat.getColor(label.context, R.color.primary_blue)
            spannableString.setSpan(
                ForegroundColorSpan(infoIconColor),
                newText.length - 1, 
                newText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            label.text = spannableString
        }
        
        // Make it clickable
        label.isClickable = true
        label.isFocusable = true
        
        // Add a ripple effect when clicked
        val outValue = android.util.TypedValue()
        label.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        label.setBackgroundResource(outValue.resourceId)
        
        // Show help dialog when clicked
        label.setOnClickListener {
            showHelpDialog(label.context, title, message)
        }
    }
    
    /**
     * Show a help dialog
     *
     * @param context The context
     * @param title The dialog title
     * @param message The dialog message
     */
    private fun showHelpDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it") { dialog, _ -> dialog.dismiss() }
            .show()
    }
} 