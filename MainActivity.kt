package com.simple.macro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.util.UUID

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
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        TaskManager.updateTask(this, task.id) {
            it.status = "CANCELLED"
            it.log = "Task cancelled by user"
        }
        renderTaskLists()
    }

    private fun renderTaskLists() {
        val allTasks = TaskManager.getTasks(this)
        val now = System.currentTimeMillis()

        layoutOngoing.removeAllViews()
        layoutHistory.removeAllViews()

        val ongoing = allTasks.filter { it.status == "SCHEDULED" && it.triggerAtMillis > now }
        val history = allTasks.filter { it.status != "SCHEDULED" || it.triggerAtMillis <= now }

        val inflater = LayoutInflater.from(this)

        // Render Ongoing
        if (ongoing.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No active scheduled tasks"
                setTextColor(0xFF64748B.toInt())
            }
            layoutOngoing.addView(empty)
        } else {
            ongoing.forEach { task ->
                val view = inflater.inflate(R.layout.item_task, layoutOngoing, false)
                val tvTitle = view.findViewById<TextView>(R.id.tvTaskTitle)
                val tvCountdown = view.findViewById<TextView>(R.id.tvCountdown)
                val tvLog = view.findViewById<TextView>(R.id.tvTaskLog)
                val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelTask)

                val diffSeconds = (task.triggerAtMillis - now) / 1000
                val h = diffSeconds / 3600
                val m = (diffSeconds % 3600) / 60
                val s = diffSeconds % 60

                val actionName = if (task.isEnable) "ENABLE" else "DISABLE"
                tvTitle.text = "${task.target} → $actionName"
                tvCountdown.text = String.format("%02d:%02d:%02d", h, m, s)
                tvLog.text = "Executing in ${diffSeconds}s"

                btnCancel.visibility = View.VISIBLE
                btnCancel.setOnClickListener { cancelAlarm(task) }

                layoutOngoing.addView(view)
            }
        }

        // Render History & Logs
        if (history.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No task history"
                setTextColor(0xFF64748B.toInt())
            }
            layoutHistory.addView(empty)
        } else {
            history.forEach { task ->
                val view = inflater.inflate(R.layout.item_task, layoutHistory, false)
                val tvTitle = view.findViewById<TextView>(R.id.tvTaskTitle)
                val tvCountdown = view.findViewById<TextView>(R.id.tvCountdown)
                val tvLog = view.findViewById<TextView>(R.id.tvTaskLog)
                val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelTask)

                val actionName = if (task.isEnable) "ENABLE" else "DISABLE"
                tvTitle.text = "${task.target} → $actionName"
                tvCountdown.text = task.status
                tvCountdown.setTextColor(if (task.status == "COMPLETED") 0xFF10B981.toInt() else 0xFFEF4444.toInt())
                tvLog.text = task.log
                btnCancel.visibility = View.GONE

                layoutHistory.addView(view)
            }
        }
    }
}
