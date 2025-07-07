package com.so5km.qrstrainer

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import com.so5km.qrstrainer.databinding.ActivityMainBinding
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.ui.about.AboutFragment
import com.so5km.qrstrainer.ui.listen.ListenFragment
import com.so5km.qrstrainer.ui.progress.ProgressFragment
import com.so5km.qrstrainer.ui.settings.SettingsFragment
import com.so5km.qrstrainer.ui.trainer.TrainerFragment

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var storeViewModel: StoreViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        storeViewModel = ViewModelProvider(this)[StoreViewModel::class.java]
        
        setupToolbar()
        setupNavigationDrawer()
        setupBackPressedHandler()
        
        if (savedInstanceState == null) {
            loadFragment(TrainerFragment(), R.id.nav_trainer)
        }
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
    }
    
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_trainer -> {
                loadFragment(TrainerFragment(), item.itemId)
                supportActionBar?.title = "Morse Code Trainer"
            }
            R.id.nav_listen -> {
                loadFragment(ListenFragment(), item.itemId)
                supportActionBar?.title = "Listen Mode"
            }
            R.id.nav_progress -> {
                loadFragment(ProgressFragment(), item.itemId)
                supportActionBar?.title = "Progress Tracking"
            }
            R.id.nav_settings -> {
                loadFragment(SettingsFragment(), item.itemId)
                supportActionBar?.title = "Settings"
            }
            R.id.nav_about -> {
                loadFragment(AboutFragment(), item.itemId)
                supportActionBar?.title = "About"
            }
        }
        
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun loadFragment(fragment: Fragment, menuId: Int) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
        
        navigationView.setCheckedItem(menuId)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerToggle.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
