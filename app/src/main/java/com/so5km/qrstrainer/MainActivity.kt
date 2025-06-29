package com.so5km.qrstrainer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.so5km.qrstrainer.audio.AudioForegroundService
import com.so5km.qrstrainer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    
    // Track if app is in foreground
    private var isAppInForeground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        // Remove the FAB since we don't need it for the Morse trainer
        binding.appBarMain.fab.hide()
        
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_trainer, R.id.nav_progress, R.id.nav_settings, R.id.nav_listen
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: App in foreground")
        AppState.isAppInForeground = true
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: App paused")
        // We don't set isAppInForeground to false here because
        // this could be called when the screen is turned off
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: App minimized")
        AppState.isAppInForeground = false
        
        // Stop foreground service when app is minimized
        // This is important to distinguish between screen off (keep playing) 
        // and app minimized (stop playing)
        if (AudioForegroundService.isRunning()) {
            val intent = Intent(this, AudioForegroundService::class.java)
            intent.action = AudioForegroundService.ACTION_STOP
            startService(intent)
        }
    }
}