package com.so5km.qrstrainer.ui.listen

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.so5km.qrstrainer.databinding.FragmentListenBinding
import com.so5km.qrstrainer.state.ListeningState
import java.util.Locale

/**
 * Simplified ListenFragment 
 */
class ListenFragment : Fragment(), TextToSpeech.OnInitListener {
    
    private var _binding: FragmentListenBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var listenViewModel: ListenViewModel
    private lateinit var textToSpeech: TextToSpeech
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListenBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        listenViewModel = ListenViewModel(requireActivity().application)
        textToSpeech = TextToSpeech(requireContext(), this)
        
        setupComponents()
        observeState()
    }
    
    private fun setupComponents() {
        binding.listenControls.apply {
            onStartClick = { listenViewModel.startListening() }
            onRevealClick = { listenViewModel.revealSequence() }
            onNextClick = { listenViewModel.nextSequence() }
            onReplayClick = { listenViewModel.replaySequence() }
        }
        
        binding.switchAutoReveal.setOnCheckedChangeListener { _, isChecked ->
            listenViewModel.setAutoReveal(isChecked)
        }
        
        binding.switchSpeak.setOnCheckedChangeListener { _, isChecked ->
            listenViewModel.setSpeakEnabled(isChecked)
        }
    }
    
    private fun observeState() {
        // Simplified state observation
        binding.textSequence.text = "Ready to listen..."
        binding.textLevel.text = "Level: 1"
        binding.textStreak.text = "Streak: 0"
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
        }
    }
    
    private fun speakSequence(sequence: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(sequence, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::textToSpeech.isInitialized) {
            textToSpeech.shutdown()
        }
        _binding = null
    }
}

