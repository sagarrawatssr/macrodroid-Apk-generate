package com.simple.macro

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ==========================================
// 1. DATA MODEL
// ==========================================
data class AutomationTask(
    val id: String,
    val target: String,
    val isEnable: Boolean,
    val triggerAtMillis: Long,
    var status: String = "SCHEDULED",
    var log: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("target", target)
        put("isEnable", isEnable)
        put("triggerAtMillis", triggerAtMillis)
        put("status", status)
        put("log", log)
    }

    companion object {
        fun fromJson(json: JSONObject): AutomationTask = AutomationTask(
            id = json.getString("id"),
            target = json.getString("target"),
            isEnable = json.getBoolean("isEnable"),
            triggerAtMillis = json.getLong("triggerAtMillis"),
            status = json.optString("status", "SCHEDULED"),
            log = json.optString("log", "")
        )
    }
}

// ==========================================
// 2. PERSISTENCE STORAGE
// ==========================================
object TaskManager {
    private const val PREF_NAME = "macro_tasks_pref"
    private const val KEY_TASKS = "tasks_list"

    fun getTasks(context: Context): MutableList<AutomationTask> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val array = JSONArray(rawJson)
        val list = mutableListOf<AutomationTask>()
        for (i in 0 until array.length()) {
            list.add(AutomationTask.fromJson(array.getJSONObject(i)))
        }
        return list
    }

    fun saveTasks(context: Context, tasks: List<AutomationTask>) {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .apply()
    }

    fun addTask(context: Context, task: AutomationTask) {
        val tasks = getTasks(context)
        tasks.add(0, task)
        saveTasks(context, tasks)
    }

    fun updateTask(context: Context, taskId: String, update: (AutomationTask) -> Unit) {
        val tasks = getTasks(context)
        tasks.find { it.id == taskId }?.let {
            update(it)
            saveTasks(context, tasks)
        }
    }
}

// ==========================================
// 3. BACKGROUND RECEIVER
// ==========================================
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
            "[$timeStamp] $target$actionName executed successfully"
        } else {
            "[$timeStamp] Failed to $actionName$target"
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

// ==========================================
// 4. MAIN CONTROLLER
// ==========================================
class MainActivity : AppCompatActivity() {

    private lateinit var layoutOngoing: LinearLayout
    private lateinit var layoutHistory: LinearLayout
    private val uiHandler = Handler(Looper.getMainLooper())

    private val countdownRunnable = object : Runnable {
        override fun run() {
            renderTaskLists()
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etHours = findViewById<EditText>(R.id.etHours)
        val etMinutes = findViewById<EditText>(R.id.etMinutes)
        val etSeconds = findViewById<EditText>(R.id.etSeconds)
        val toggleAction = findViewById<MaterialButtonToggleGroup>(R.id.toggleActionGroup)
        val chipSettings = findViewById<ChipGroup>(R.id.chipGroupSettings)
        val btnSchedule = findViewById<MaterialButton>(R.id.btnSchedule)

        layoutOngoing = findViewById(R.id.layoutOngoingTasks)
        layoutHistory = findViewById(R.id.layoutHistoryTasks)

        btnSchedule.setOnClickListener {
            val hours = etHours.text.toString().toLongOrNull() ?: 0L
            val minutes = etMinutes.text.toString().toLongOrNull() ?: 0L
            val seconds = etSeconds.text.toString().toLongOrNull() ?: 0L
            val totalDelayMillis = ((hours * 3600) + (minutes * 60) + seconds) * 1000

            if (totalDelayMillis <= 0) {
                Toast.makeText(this, "Enter a valid time delay", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isEnable = toggleAction.checkedButtonId == R.id.btnActionEnable
            val target = when (chipSettings.checkedChipId) {
                R.id.chipBluetooth -> "Bluetooth"
                R.id.chipWifi -> "WiFi"
                R.id.chipLocation -> "Location"
                R.id.chipHotspot -> "Hotspot"
                R.id.chipInternet -> "Internet"
                else -> "Bluetooth"
            }

            val triggerTime = System.currentTimeMillis() + totalDelayMillis
            val taskId = UUID.randomUUID().toString()

            val task = AutomationTask(
                id = taskId,
                target = target,
                isEnable = isEnable,
                triggerAtMillis = triggerTime,
                status = "SCHEDULED",
                log = "Scheduled to run in ${totalDelayMillis / 1000}s"
            )

            scheduleAlarm(task)
            TaskManager.addTask(this, task)
            renderTaskLists()
            Toast.makeText(this, "Task Added!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(countdownRunnable)
    }

    private fun scheduleAlarm(task: AutomationTask) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskReceiver::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TARGET", task.target)
            putExtra("IS_ENABLE", task.isEnable)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            task.triggerAtMillis,
            pendingIntent
        )
    }

    private fun cancelAlarm(task: AutomationTask) {
        val alarmManager = getSystemService(Context.ALARM_
