package com.so5km.qrstrainer.ui.common

import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Extension functions for fragments to eliminate UI redundancy
 */

/**
 * Show a simple info message
 */
fun Fragment.showMessage(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { rootView ->
        Snackbar.make(rootView, message, duration).show()
    }
}

/**
 * Show a success message with green styling
 */
fun Fragment.showSuccessMessage(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { rootView ->
        val snackbar = Snackbar.make(rootView, message, duration)
        snackbar.setBackgroundTint(androidx.core.content.ContextCompat.getColor(
            requireContext(), 
            com.so5km.qrstrainer.R.color.md_theme_light_primary
        ))
        snackbar.setTextColor(androidx.core.content.ContextCompat.getColor(
            requireContext(), 
            com.so5km.qrstrainer.R.color.md_theme_light_onPrimary
        ))
        snackbar.show()
    }
}

/**
 * Show an error message with red styling
 */
fun Fragment.showErrorMessage(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    view?.let { rootView ->
        val snackbar = Snackbar.make(rootView, message, duration)
        snackbar.setBackgroundTint(androidx.core.content.ContextCompat.getColor(
            requireContext(), 
            com.so5km.qrstrainer.R.color.md_theme_light_error
        ))
        snackbar.setTextColor(androidx.core.content.ContextCompat.getColor(
            requireContext(), 
            com.so5km.qrstrainer.R.color.md_theme_light_onError
        ))
        snackbar.show()
    }
}

/**
 * Show a message with an action
 */
fun Fragment.showMessageWithAction(
    message: String,
    actionText: String,
    action: () -> Unit,
    duration: Int = Snackbar.LENGTH_INDEFINITE
) {
    view?.let { rootView ->
        Snackbar.make(rootView, message, duration)
            .setAction(actionText) { action() }
            .show()
    }
} 