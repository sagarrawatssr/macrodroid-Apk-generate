package com.simple.macro

import org.json.JSONObject

data class AutomationTask(
    val id: String,
    val target: String,      // "Bluetooth", "WiFi", "Location", "Hotspot", "Internet"
    val isEnable: Boolean,   // true = Enable, false = Disable
    val triggerAtMillis: Long,
    var status: String = "SCHEDULED", // "SCHEDULED", "COMPLETED", "CANCELLED"
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
