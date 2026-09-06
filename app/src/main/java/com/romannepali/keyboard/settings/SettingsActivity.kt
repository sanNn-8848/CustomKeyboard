package com.romannepali.keyboard.settings

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.romannepali.keyboard.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        
        // Enable Keyboard button
        findViewById<android.widget.Button>(R.id.btn_enable_keyboard).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
        
        // Check if keyboard is enabled
        if (!isKeyboardEnabled()) {
            showEnableKeyboardDialog()
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledInputMethods = imm.enabledInputMethodList
        
        return enabledInputMethods.any {
            it.packageName == packageName
        }
    }

    private fun showEnableKeyboardDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enable Roman Nepali Keyboard")
            .setMessage("To use this keyboard, you need to enable it in your device settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
