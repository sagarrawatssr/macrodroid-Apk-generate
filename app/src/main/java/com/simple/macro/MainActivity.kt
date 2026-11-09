package com.simple.macro

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etHours = findViewById<TextInputEditText>(R.id.etHours)
        val etMinutes = findViewById<TextInputEditText>(R.id.etMinutes)
        val etSeconds = findViewById<TextInputEditText>(R.id.etSeconds)
        val toggleAction = findViewById<MaterialButtonToggleGroup>(R.id.toggleActionGroup)
        val chipSettings = findViewById<ChipGroup>(R.id.chipGroupSettings)
        val btnSchedule = findViewById<MaterialButton>(R.id.btnSchedule)
        val btnAccessibility = findViewById<MaterialButton>(R.id.btnEnableAccessibility)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Macro Automation' in the list", Toast.LENGTH_LONG).show()
        }

        btnSchedule.setOnClickListener {
            val hours = etHours.text.toString().toLongOrNull() ?: 0L
            val minutes = etMinutes.text.toString().toLongOrNull() ?: 0L
            val seconds = etSeconds.text.toString().toLongOrNull() ?: 0L
            val totalDelayMillis = ((hours * 3600) + (minutes * 60) + seconds) * 1000

            if (totalDelayMillis <= 0) {
                Toast.makeText(this, "Please enter a valid time duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isEnable = toggleAction.checkedButtonId == R.id.btnActionEnable
            val actionName = if (isEnable) "ENABLE" else "DISABLE"

            val targetSetting = when (chipSettings.checkedChipId) {
                R.id.chipBluetooth -> "Bluetooth"
                R.id.chipWifi -> "WiFi"
                R.id.chipLocation -> "Location"
                R.id.chipHotspot -> "Hotspot"
                R.id.chipInternet -> "Internet"
                else -> "Bluetooth"
            }

            tvStatus.text = "Status: Will $actionName $targetSetting in ${totalDelayMillis / 1000}s"
            Toast.makeText(this, "Timer set for ${totalDelayMillis / 1000}s", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                executeAutomation(targetSetting, isEnable)
                tvStatus.text = "Status: $actionName $targetSetting triggered"
            }, totalDelayMillis)
        }
    }

    private fun executeAutomation(target: String, enable: Boolean) {
        val stateCmd = if (enable) "enable" else "disable"

        // 1. Primary Attempt: Direct shell (works seamlessly if root/adb is present)
        val directCommand = when (target) {
            "Bluetooth" -> "svc bluetooth $stateCmd"
            "WiFi" -> "svc wifi $stateCmd"
            "Location" -> if (enable) "settings put secure location_mode 3" else "settings put secure location_mode 0"
            "Hotspot" -> if (enable) "cmd connectivity tether start wifi" else "cmd connectivity tether stop wifi"
            "Internet" -> "svc data $stateCmd"
            else -> ""
        }

        val shellSuccess = tryDirectExecution(directCommand)
        if (shellSuccess) return

        // 2. Standalone Fallback: Accessibility Service (No root, no ADB, no PC)
        val accessibility = MacroAccessibilityService.instance
        if (accessibility != null) {
            accessibility.performSettingToggle(target)
        } else {
            // 3. Fallback: Direct Intent to the system Settings panel
            val intent = when (target) {
                "Bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "WiFi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "Location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                "Hotspot" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
                "Internet" -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            startActivity(intent)
        }
    }

    private fun tryDirectExecution(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.waitFor() == 0
            } catch (e2: Exception) {
                false
            }
        }
    }
}
