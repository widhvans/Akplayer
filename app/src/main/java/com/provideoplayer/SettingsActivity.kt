package com.provideoplayer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.provideoplayer.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "pro_video_player_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_DEFAULT_SPEED = "default_speed"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        const val KEY_GESTURE_CONTROLS = "gesture_controls"
        const val KEY_DOUBLE_TAP_SEEK = "double_tap_seek"
        const val KEY_AUTO_PIP = "auto_pip"
        const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before calling super.onCreate
        applyAppTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupToolbar()
        loadSettings()
        setupListeners()
    }
    
    private fun applyAppTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val themeMode = prefs.getInt(KEY_THEME, 0)
        
        when (themeMode) {
            0 -> { /* System Default - no override */ }
            1 -> setTheme(R.style.Theme_ProVideoPlayer_Light)
            2 -> setTheme(R.style.Theme_ProVideoPlayer)  // Dark
            3 -> setTheme(R.style.Theme_ProVideoPlayer_Amoled)
            4 -> setTheme(R.style.Theme_ProVideoPlayer_Blue)
            5 -> setTheme(R.style.Theme_ProVideoPlayer_Pink)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
        
        // Tint navigation icon and title based on theme
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorControlNormal, typedValue, true)
        val iconColor = typedValue.data
        binding.toolbar.navigationIcon?.setTint(iconColor)
        binding.toolbar.setTitleTextColor(iconColor)
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadSettings() {
        // Load saved preferences
        binding.switchResumePlayback.isChecked = prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
        binding.switchGestureControls.isChecked = prefs.getBoolean(KEY_GESTURE_CONTROLS, true)
        binding.switchDoubleTapSeek.isChecked = prefs.getBoolean(KEY_DOUBLE_TAP_SEEK, true)
        binding.switchAutoPip.isChecked = prefs.getBoolean(KEY_AUTO_PIP, true)
        binding.switchHardwareAcceleration.isChecked = prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true)

        // Update All Files Access switch state
        updateAllFilesAccessState()

        // Update speed text
        updateSpeedText()
        
        // Update theme text
        updateThemeText()
    }
    
    private fun updateAllFilesAccessState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasAccess = Environment.isExternalStorageManager()
            binding.switchAllFilesAccess.isChecked = hasAccess
            binding.textAllFilesStatus.text = if (hasAccess) "Granted" else "Tap to grant access"
        } else {
            // Hide for older Android versions
            binding.layoutAllFilesAccess.visibility = android.view.View.GONE
        }
    }

    private fun setupListeners() {
        // Theme selection
        binding.layoutTheme.setOnClickListener {
            showThemeDialog()
        }
        
        // Default speed selection
        binding.layoutDefaultSpeed.setOnClickListener {
            showSpeedDialog()
        }

        // Resume playback
        binding.switchResumePlayback.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, isChecked).apply()
        }

        // Gesture controls
        binding.switchGestureControls.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_GESTURE_CONTROLS, isChecked).apply()
        }

        // Double tap seek
        binding.switchDoubleTapSeek.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DOUBLE_TAP_SEEK, isChecked).apply()
        }

        // Auto PiP
        binding.switchAutoPip.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_PIP, isChecked).apply()
        }

        // Hardware acceleration
        binding.switchHardwareAcceleration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HARDWARE_ACCELERATION, isChecked).apply()
        }

        // All Files Access
        binding.layoutAllFilesAccess.setOnClickListener {
            requestAllFilesAccess()
        }
        binding.switchAllFilesAccess.setOnClickListener {
            requestAllFilesAccess()
        }

        // Clear cache
        binding.layoutClearCache.setOnClickListener {
            clearCache()
        }

        // About
        binding.layoutAbout.setOnClickListener {
            showAboutDialog()
        }
    }
    
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update All Files Access state when returning from settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            updateAllFilesAccessState()
        }
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentSpeed = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        val currentIndex = speedValues.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 } ?: 2

        MaterialAlertDialogBuilder(this)
            .setTitle("Default Playback Speed")
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                prefs.edit().putFloat(KEY_DEFAULT_SPEED, speedValues[which]).apply()
                updateSpeedText()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSpeedText() {
        val currentSpeed = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        binding.textSpeedValue.text = "${currentSpeed}x"
    }
    
    private fun updateThemeText() {
        val themeMode = prefs.getInt(KEY_THEME, 0)
        val themeNames = arrayOf("System Default", "Light", "Dark", "AMOLED Black", "Blue", "Pink")
        binding.textThemeValue.text = themeNames.getOrNull(themeMode) ?: "System Default"
    }
    
    private fun showThemeDialog() {
        val themes = arrayOf("System Default", "Light", "Dark", "AMOLED Black", "Blue", "Pink")
        val currentTheme = prefs.getInt(KEY_THEME, 0)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                prefs.edit().putInt(KEY_THEME, which).apply()
                updateThemeText()
                dialog.dismiss()
                
                // Restart app completely to apply theme everywhere
                Toast.makeText(this, "Applying theme...", Toast.LENGTH_SHORT).show()
                
                // Start MainActivity with clear flags to restart the app
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun clearCache() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached thumbnails and temporary files. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                try {
                    cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                    Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Akplayer")
            .setMessage("""
                Version: 1.0.0
                
                A professional video player with:
                • High quality video playback
                • Gesture controls
                • Subtitle support
                • Multiple audio tracks
                • Network streaming
                • Picture-in-Picture mode
                • Multiple Themes
                
                Powered by ExoPlayer
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}
