package com.simple.macro

import android.content.Context
import org.json.JSONArray

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

    fun removeTask(context: Context, taskId: String) {
        val tasks = getTasks(context).filter { it.id != taskId }
        saveTasks(context, tasks)
    }
}
