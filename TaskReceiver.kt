package com.simple.macro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("TASK_ID") ?: return
        val target = intent.getStringExtra("TARGET") ?: "Bluetooth"
        val isEnable = intent.getBooleanExtra("IS_ENABLE", true)
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val success = when (target) {
            "Bluetooth" -> {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter != null) {
                    if (isEnable) adapter.enable() else adapter.disable()
                } else false
            }
            "WiFi" -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.setWifiEnabled(isEnable)
                true
            }
            "Location" -> {
                val mode = if (isEnable) "3" else "0"
                runShellCommand("settings put secure location_mode $mode")
            }
            "Hotspot" -> {
                val action = if (isEnable) "start" else "stop"
                runShellCommand("cmd connectivity tether $action wifi")
            }
            "Internet" -> {
                val state = if (isEnable) "enable" else "disable"
                runShellCommand("svc data $state")
            }
            else -> false
        }

        val actionName = if (isEnable) "ENABLED" else "DISABLED"
        val resultLog = if (success) {
            "[$timeStamp] $target $actionName executed successfully"
        } else {
            "[$timeStamp] Failed to $actionName $target"
        }

        TaskManager.updateTask(context, taskId) {
            it.status = "COMPLETED"
            it.log = resultLog
        }
    }

    private fun runShellCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
