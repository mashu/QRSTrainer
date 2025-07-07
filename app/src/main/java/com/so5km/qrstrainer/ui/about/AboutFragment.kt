package com.so5km.qrstrainer.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupVersionInfo()
        setupClickListeners()
        setupAnimations()
    }
    
    private fun setupVersionInfo() {
        binding.apply {
            // Use hardcoded version for now since BuildConfig might not be available
            textVersion.text = getString(R.string.about_version, "1.0.0")
            textAppName.text = getString(R.string.app_name)
            textDescription.text = getString(R.string.about_description)
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            cardGithub.setOnClickListener {
                openUrl("https://github.com/so5km/qrstrainer")
            }
            
            cardContact.setOnClickListener {
                sendEmail()
            }
            
            cardLicense.setOnClickListener {
                openUrl("https://opensource.org/licenses/MIT")
            }
            
            buttonRateApp.setOnClickListener {
                openPlayStore()
            }
            
            buttonShareApp.setOnClickListener {
                shareApp()
            }
        }
    }
    
    private fun setupAnimations() {
        // Staggered entrance animations for cards
        val cards = listOf(
            binding.cardAppInfo,
            binding.cardFeatures,
            binding.cardDeveloper,
            binding.cardActions
        )
        
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 100f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error - could show toast or snackbar
        }
    }
    
    private fun sendEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:so5km@example.com")
                putExtra(Intent.EXTRA_SUBJECT, "QRS Trainer Feedback")
                putExtra(Intent.EXTRA_TEXT, "App Version: 1.0.0\n\n")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    private fun openPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${requireContext().packageName}")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web version
            openUrl("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }
    }
    
    private fun shareApp() {
        val shareText = """
            Check out QRS Trainer - an amazing Morse code training app!
            
            🎧 Progressive training with Koch method
            📊 Comprehensive progress tracking  
            🎵 Realistic audio simulation
            📱 Modern, intuitive interface
            
            Download: https://play.google.com/store/apps/details?id=${requireContext().packageName}
        """.trimIndent()
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "QRS Trainer - Morse Code Training")
        }
        
        startActivity(Intent.createChooser(intent, "Share QRS Trainer"))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
