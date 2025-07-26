package com.so5km.qrstrainer.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
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
            try {
                val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                textVersion.text = getString(R.string.about_version, packageInfo.versionName)
            } catch (e: Exception) {
                textVersion.text = getString(R.string.about_version, "1.0.0")
            }
            
            textAppName.text = getString(R.string.app_name)
            textDescription.text = getString(R.string.about_description)
        }
    }
    
    private fun setupClickListeners() {
        binding.apply {
            cardGithub.setOnClickListener {
                openUrl("https://github.com/mashu/QRSTrainer")
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
            showMessage("Unable to open link")
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
        val appName = getString(R.string.app_name)
        val shareText = """
            🎧 Check out $appName - Master Morse Code Like a Pro! 📻
            
            ✨ Features:
            • Progressive Koch method training
            • Real-time progress tracking  
            • Realistic HF noise simulation
            • Listen & learn mode
            • Modern, intuitive interface
            
            Perfect for ham radio operators, scouts, and morse code enthusiasts!
            
            📱 Download: https://play.google.com/store/apps/details?id=${requireContext().packageName}
            
            #MorseCode #HamRadio #CW #RadioTraining
        """.trimIndent()
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "$appName - Morse Code Training")
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Share $appName"))
        } catch (e: Exception) {
            showMessage("Unable to share")
        }
    }
    
    private fun getVersionName(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
