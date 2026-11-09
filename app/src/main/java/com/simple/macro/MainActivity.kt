package com.simple.macro

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    enum class TargetSetting(val idName: String, val displayName: String, val isRestricted: Boolean) {
        INTERNET("internet", "Internet", true),
        HOTSPOT("hotspot", "Hotspot", true),
        LOCATION("location", "Location", true),
        BLUETOOTH("bluetooth", "Bluetooth", false),
        SOUND("sound", "Sound", false)
    }

    enum class ActionType {
        ENABLE, DISABLE
    }

    // Current State
    private var selectedTarget: TargetSetting = TargetSetting.INTERNET
    private var selectedAction: ActionType? = null

    // Target UI Buttons
    private lateinit var btnTargetInternet: MaterialButton
    private lateinit var btnTargetHotspot: MaterialButton
    private lateinit var btnTargetLocation: MaterialButton
    private lateinit var btnTargetBluetooth: MaterialButton
    private lateinit var btnTargetSound: MaterialButton
    private lateinit var targetButtons: List<Pair<TargetSetting, MaterialButton>>

    // Action UI Buttons (Enable / Disable)
    private lateinit var btnActionEnable: MaterialButton
    private lateinit var btnActionDisable: MaterialButton
    private lateinit var tvActionRestrictionNotice: TextView

    // Time Inputs & Schedule
    private lateinit var etHours: EditText
    private lateinit var etMinutes: EditText
    private lateinit var etSeconds: EditText
    private lateinit var btnScheduleTask: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTargetListeners()
        setupActionListeners()

        // Apply default selection and highlight on initial load
        selectTarget(TargetSetting.INTERNET)
    }

    private fun initViews() {
        btnTargetInternet = findViewById(R.id.btnTargetInternet)
        btnTargetHotspot = findViewById(R.id.btnTargetHotspot)
        btnTargetLocation = findViewById(R.id.btnTargetLocation)
        btnTargetBluetooth = findViewById(R.id.btnTargetBluetooth)
        btnTargetSound = findViewById(R.id.btnTargetSound)

        targetButtons = listOf(
            TargetSetting.INTERNET to btnTargetInternet,
            TargetSetting.HOTSPOT to btnTargetHotspot,
            TargetSetting.LOCATION to btnTargetLocation,
            TargetSetting.BLUETOOTH to btnTargetBluetooth,
            TargetSetting.SOUND to btnTargetSound
        )

        btnActionEnable = findViewById(R.id.btnActionEnable)
        btnActionDisable = findViewById(R.id.btnActionDisable)
        tvActionRestrictionNotice = findViewById(R.id.tvActionRestrictionNotice)

        etHours = findViewById(R.id.etHours)
        etMinutes = findViewById(R.id.etMinutes)
        etSeconds = findViewById(R.id.etSeconds)
        btnScheduleTask = findViewById(R.id.btnScheduleTask)

        btnScheduleTask.setOnClickListener {
            scheduleMacro()
        }
    }

    private fun setupTargetListeners() {
        for ((target, button) in targetButtons) {
            button.setOnClickListener {
                selectTarget(target)
            }
        }
    }

    private fun setupActionListeners() {
        btnActionEnable.setOnClickListener {
            if (!selectedTarget.isRestricted) {
                selectedAction = ActionType.ENABLE
                updateActionButtonsUi()
            }
        }

        btnActionDisable.setOnClickListener {
            if (!selectedTarget.isRestricted) {
                selectedAction = ActionType.DISABLE
                updateActionButtonsUi()
            }
        }
    }

    /**
     * Requirement 2: Highlights the currently selected target and removes highlight from others.
     * Requirement 1: Blocks Enable/Disable if target is Internet, Hotspot, or Location.
     */
    private fun selectTarget(target: TargetSetting) {
        selectedTarget = target

        // 1. Highlight the selected target button
        for ((setting, button) in targetButtons) {
            val isCurrent = setting == selectedTarget
            if (isCurrent) {
                // Active Highlight State (Bright accent outline + elevated fill)
                button.strokeWidth = dpToPx(2)
                button.strokeColor = ColorStateList.valueOf(Color.parseColor("#3B82F6")) // Accent Blue
                button.setBackgroundColor(Color.parseColor("#1D283A"))                   // Highlight Dark Slate
                button.setTextColor(Color.WHITE)
            } else {
                // Inactive State
                button.strokeWidth = dpToPx(1)
                button.strokeColor = ColorStateList.valueOf(Color.parseColor("#334155")) // Muted Slate
                button.setBackgroundColor(Color.parseColor("#0F172A"))                   // Base Dark
                button.setTextColor(Color.parseColor("#94A3B8"))                         // Muted Text
            }
        }

        // 2. Enforce Enable/Disable restriction for Hotspot, Internet, and Location
        if (target.isRestricted) {
            selectedAction = null
            btnActionEnable.isEnabled = false
            btnActionDisable.isEnabled = false

            btnActionEnable.alpha = 0.35f
            btnActionDisable.alpha = 0.35f

            tvActionRestrictionNotice.visibility = View.VISIBLE
            tvActionRestrictionNotice.text = "${target.displayName} cannot be enabled or disabled."
        } else {
            btnActionEnable.isEnabled = true
            btnActionDisable.isEnabled = true

            btnActionEnable.alpha = 1.0f
            btnActionDisable.alpha = 1.0f

            tvActionRestrictionNotice.visibility = View.GONE
            if (selectedAction == null) {
                selectedAction = ActionType.ENABLE
            }
        }

        updateActionButtonsUi()
    }

    private fun updateActionButtonsUi() {
        if (selectedTarget.isRestricted) {
            btnActionEnable.strokeWidth = 0
            btnActionDisable.strokeWidth = 0
            btnActionEnable.setBackgroundColor(Color.parseColor("#1E293B"))
            btnActionDisable.setBackgroundColor(Color.parseColor("#1E293B"))
            return
        }

        // Action highlight for unrestricted targets
        if (selectedAction == ActionType.ENABLE) {
            btnActionEnable.strokeWidth = dpToPx(2)
            btnActionEnable.strokeColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            btnActionEnable.setBackgroundColor(Color.parseColor("#064E3B"))

            btnActionDisable.strokeWidth = 0
            btnActionDisable.setBackgroundColor(Color.parseColor("#1E293B"))
        } else if (selectedAction == ActionType.DISABLE) {
            btnActionDisable.strokeWidth = dpToPx(2)
            btnActionDisable.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            btnActionDisable.setBackgroundColor(Color.parseColor("#7F1D1D"))

            btnActionEnable.strokeWidth = 0
            btnActionEnable.setBackgroundColor(Color.parseColor("#1E293B"))
        }
    }

    private fun scheduleMacro() {
        if (selectedTarget.isRestricted) {
            Toast.makeText(
                this,
                "${selectedTarget.displayName} is restricted and cannot be toggled.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val action = selectedAction ?: return
        Toast.makeText(
            this,
            "Macro scheduled: ${selectedTarget.displayName} -> $action",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
