package com.so5km.qrstrainer

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.so5km.qrstrainer.databinding.ActivityMainBinding
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.TrainingState
import com.so5km.qrstrainer.ui.about.AboutFragment
import com.so5km.qrstrainer.ui.listen.ListenFragment
import com.so5km.qrstrainer.ui.progress.ProgressFragment
import com.so5km.qrstrainer.ui.settings.SettingsFragment
import com.so5km.qrstrainer.ui.trainer.TrainerFragment
import com.so5km.qrstrainer.audio.AudioManager
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.ProgressTracker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var audioManager: AudioManager
    private lateinit var progressTracker: ProgressTracker
    
    private var currentFragmentTag: String = "trainer"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupToolbar()
        setupNavigationDrawer()
        setupBackPressedHandler()
        setupFAB()
        observeAppState()
        
        if (savedInstanceState == null) {
            loadFragment(TrainerFragment(), R.id.nav_trainer, "trainer")
        }
    }
    
    private fun initializeComponents() {
        storeViewModel = ViewModelProvider(this)[StoreViewModel::class.java]
        audioManager = AudioManager(this)
        progressTracker = ProgressTracker(this)
        
        // Initialize app state
        val defaultSettings = TrainingSettings.default()
        storeViewModel.dispatch(AppAction.UpdateSettings(defaultSettings))
        storeViewModel.dispatch(AppAction.SetAppInForeground(true))
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.app_name)
        }
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView
        
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.setCheckedItem(R.id.nav_trainer)
        
        // Update navigation header with app state
        updateNavigationHeader()
    }
    
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // If audio is playing, stop it before exiting
                    if (storeViewModel.audioState.value.isPlaying) {
                        audioManager.stopPlayback()
                    }
                    finish()
                }
            }
        })
    }
    
    private fun setupFAB() {
        binding.fabQuickPlay.setOnClickListener {
            // Quick play functionality - start a quick training session
            quickPlay()
        }
    }
    
    private fun observeAppState() {
        lifecycleScope.launch {
            storeViewModel.audioState.collect { audioState ->
                updateFABForAudioState(audioState.isPlaying)
            }
        }
        
        // Now actually using trainingState to update navigation header
        lifecycleScope.launch {
            storeViewModel.trainingState.collect { trainingState ->
                updateNavigationHeaderWithTrainingState(trainingState.state)
                
                // Update FAB based on training state
                when (trainingState.state) {
                    TrainingState.PLAYING -> {
                        binding.fabQuickPlay.hide()
                    }
                    TrainingState.READY, TrainingState.FINISHED -> {
                        binding.fabQuickPlay.show()
                    }
                    else -> { /* Keep current state */ }
                }
            }
        }
    }
    
    private fun updateFABForAudioState(isPlaying: Boolean) {
        binding.fabQuickPlay.apply {
            if (isPlaying) {
                setImageResource(R.drawable.ic_stop)
                contentDescription = "Stop Audio"
            } else {
                setImageResource(R.drawable.ic_play_arrow)
                contentDescription = "Quick Play"
            }
        }
    }
    
    private fun quickPlay() {
        val audioState = storeViewModel.audioState.value
        
        if (audioState.isPlaying) {
            // Stop current audio
            audioManager.stopPlayback()
            showMessage("Audio stopped")
        } else {
            // Start quick training
            val settings = storeViewModel.settings.value
            lifecycleScope.launch {
                try {
                    audioManager.playSequence("HELLO", settings)
                    showMessage("Playing quick test sequence")
                } catch (e: Exception) {
                    showMessage("Audio error: ${e.message}")
                }
            }
        }
    }
    
    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val subtitleText = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        
        // Update with current progress
        val level = progressTracker.getCurrentLevel()
        val streak = progressTracker.getCurrentStreak()
        subtitleText?.text = "Level $level • Streak $streak"
    }
    
    private fun updateNavigationHeaderWithTrainingState(trainingState: TrainingState) {
        val headerView = navigationView.getHeaderView(0)
        val subtitleText = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        
        val level = progressTracker.getCurrentLevel()
        val streak = progressTracker.getCurrentStreak()
        
        val stateText = when (trainingState) {
            TrainingState.PLAYING -> "Training Active"
            TrainingState.READY -> "Ready to Train"
            TrainingState.WAITING -> "Waiting for Input"
            TrainingState.FINISHED -> "Sequence Complete"
            TrainingState.PAUSED -> "Paused"
        }
        
        subtitleText?.text = "$stateText • Level $level • Streak $streak"
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_trainer -> {
                loadFragment(TrainerFragment(), item.itemId, "trainer")
                supportActionBar?.title = "Morse Code Trainer"
            }
            R.id.nav_listen -> {
                loadFragment(ListenFragment(), item.itemId, "listen")
                supportActionBar?.title = "Listen & Learn"
            }
            R.id.nav_progress -> {
                loadFragment(ProgressFragment(), item.itemId, "progress")
                supportActionBar?.title = "Progress Tracking"
            }
            R.id.nav_settings -> {
                loadFragment(SettingsFragment(), item.itemId, "settings")
                supportActionBar?.title = "Settings"
            }
            R.id.nav_about -> {
                loadFragment(AboutFragment(), item.itemId, "about")
                supportActionBar?.title = "About"
            }
        }
        
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun loadFragment(fragment: Fragment, menuId: Int, tag: String) {
        // Stop any current audio when switching fragments
        if (storeViewModel.audioState.value.isPlaying) {
            audioManager.stopPlayback()
        }
        
        // Only replace if it's a different fragment
        if (currentFragmentTag != tag) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, fragment, tag)
                .commit()
            
            currentFragmentTag = tag
        }
        
        navigationView.setCheckedItem(menuId)
        
        // Update navigation header when switching fragments
        updateNavigationHeader()
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerToggle.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        storeViewModel.dispatch(AppAction.SetAppInForeground(true))
        updateNavigationHeader()
    }
    
    override fun onPause() {
        super.onPause()
        storeViewModel.dispatch(AppAction.SetAppInForeground(false))
        
        // Pause audio when app goes to background
        if (storeViewModel.audioState.value.isPlaying) {
            audioManager.pause()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up audio resources
        audioManager.release()
        
        // Clean up any other resources
        storeViewModel.dispatch(AppAction.SetAppInForeground(false))
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("current_fragment_tag", currentFragmentTag)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentFragmentTag = savedInstanceState.getString("current_fragment_tag", "trainer")
    }
}
