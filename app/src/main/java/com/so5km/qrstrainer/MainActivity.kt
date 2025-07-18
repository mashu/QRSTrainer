package com.so5km.qrstrainer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
    private lateinit var preferences: SharedPreferences
    
    private var currentFragmentTag: String = ""
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "app_preferences"
        private const val PREF_LAST_FRAGMENT = "last_fragment"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate()
        applySelectedTheme()
        
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupToolbar()
        setupNavigationDrawer()
        setupBackPressedHandler()
        // Remove or hide the FAB completely
        binding.fabQuickPlay.hide()
        observeAppState()
        
        if (savedInstanceState == null) {
            loadDefaultFragment()
        }
    }
    
    private fun applySelectedTheme() {
        // Load theme preference from SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = prefs.getString("theme_mode", "light") ?: "light"
        
        val themeResId = when (themeMode) {
            "dark" -> R.style.AppTheme_Dark
            "system" -> R.style.AppTheme // DayNight theme
            else -> R.style.AppTheme_Light // default to light
        }
        
        setTheme(themeResId)
        Log.d(TAG, "Applied theme: $themeMode (resId: $themeResId)")
    }
    
    private fun initializeComponents() {
        // Initialize the AppStore with context for persistence
        com.so5km.qrstrainer.state.AppStore.getInstance().initialize(this)
        
        storeViewModel = ViewModelProvider(this)[StoreViewModel::class.java]
        audioManager = AudioManager(this)
        progressTracker = ProgressTracker(this)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize app state (settings will be loaded from persistence)
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
    
    private fun observeAppState() {
        lifecycleScope.launch {
            storeViewModel.trainingState.collect { trainingState ->
                updateNavigationHeaderWithTrainingState(trainingState.state)
            }
        }
        
        // Observe theme changes and recreate activity when theme changes
        lifecycleScope.launch {
            storeViewModel.settings.collect { settings ->
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentTheme = prefs.getString("theme_mode", "light") ?: "light"
                
                if (settings.themeMode != currentTheme) {
                    // Save new theme preference
                    prefs.edit().putString("theme_mode", settings.themeMode).apply()
                    
                    // Recreate activity to apply new theme
                    recreate()
                }
            }
        }
    }
    
    /**
     * Update theme when called from settings
     */
    fun updateTheme(newThemeMode: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("theme_mode", "light") ?: "light"
        
        if (newThemeMode != currentTheme) {
            prefs.edit().putString("theme_mode", newThemeMode).apply()
            recreate() // Recreate activity to apply new theme
        }
    }
    
    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val subtitleText = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        
        // Update with current progress
        val level = storeViewModel.settings.value.currentLevel
        val streak = maxOf(0, progressTracker.getCurrentStreak()) // Show only positive streaks
        subtitleText?.text = "Level $level • Streak $streak"
    }
    
    private fun updateNavigationHeaderWithTrainingState(trainingState: TrainingState) {
        val headerView = navigationView.getHeaderView(0)
        val subtitleText = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        
        val level = storeViewModel.settings.value.currentLevel
        val streak = maxOf(0, progressTracker.getCurrentStreak()) // Show only positive streaks
        
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
    
    private fun loadDefaultFragment() {
        // Get the last used fragment from preferences, default to trainer
        val lastFragment = preferences.getString(PREF_LAST_FRAGMENT, "trainer") ?: "trainer"
        Log.d(TAG, "Loading default fragment: $lastFragment")
        
        when (lastFragment) {
            "trainer" -> {
                loadFragment(TrainerFragment(), R.id.nav_trainer, "trainer")
                supportActionBar?.title = "Morse Code Trainer"
            }
            "listen" -> {
                loadFragment(ListenFragment(), R.id.nav_listen, "listen")
                supportActionBar?.title = "Listen & Learn"
            }
            "progress" -> {
                loadFragment(ProgressFragment(), R.id.nav_progress, "progress")
                supportActionBar?.title = "Progress Tracking"
            }
            "settings" -> {
                loadFragment(SettingsFragment(), R.id.nav_settings, "settings")
                supportActionBar?.title = "Settings"
            }
            "about" -> {
                loadFragment(AboutFragment(), R.id.nav_about, "about")
                supportActionBar?.title = "About"
            }
            else -> {
                // Fallback to trainer if unknown fragment
                loadFragment(TrainerFragment(), R.id.nav_trainer, "trainer")
                supportActionBar?.title = "Morse Code Trainer"
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment, menuId: Int, tag: String) {
        Log.d(TAG, "Loading fragment: $tag")
        
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
            
            // Save the current fragment as the last used
            preferences.edit().putString(PREF_LAST_FRAGMENT, tag).apply()
            Log.d(TAG, "Saved last fragment: $tag")
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
        Log.d(TAG, "Restored fragment tag: $currentFragmentTag")
        
        // Update navigation to reflect the current fragment
        val menuId = when (currentFragmentTag) {
            "trainer" -> R.id.nav_trainer
            "listen" -> R.id.nav_listen
            "progress" -> R.id.nav_progress
            "settings" -> R.id.nav_settings
            "about" -> R.id.nav_about
            else -> R.id.nav_trainer
        }
        navigationView.setCheckedItem(menuId)
    }
}
