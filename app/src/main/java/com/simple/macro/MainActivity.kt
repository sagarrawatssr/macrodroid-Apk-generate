package com.simple.macro

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etDelay = findViewById<EditText>(R.id.etDelaySeconds)
        val btnOn = findViewById<Button>(R.id.btnTurnOn)
        val btnOff = findViewById<Button>(R.id.btnTurnOff)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnOn.setOnClickListener {
            val seconds = etDelay.text.toString().toLongOrNull() ?: 5L
            scheduleBluetooth(enable = true, delaySeconds = seconds, tvStatus)
        }

        btnOff.setOnClickListener {
            val seconds = etDelay.text.toString().toLongOrNull() ?: 5L
            scheduleBluetooth(enable = false, delaySeconds = seconds, tvStatus)
        }
    }

    private fun scheduleBluetooth(enable: Boolean, delaySeconds: Long, statusView: TextView) {
        val actionText = if (enable) "ENABLE" else "DISABLE"
        statusView.text = "Status: Will $actionText Bluetooth in $delaySeconds sec..."
        Toast.makeText(this, "Timer set for $delaySeconds seconds", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val command = if (enable) "svc bluetooth enable" else "svc bluetooth disable"
            val success = runCommand(command)

            statusView.text = if (success) {
                "Status: $actionText executed successfully"
            } else {
                "Status: Execution attempted (Check Root/ADB permission)"
            }
        }, delaySeconds * 1000)
    }

    private fun runCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            try {
                // Fallback for non-root ADB-granted environments
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                process.waitFor() == 0
            } catch (e2: Exception) {
                false
            }
        }
    }
}
