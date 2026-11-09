package com.simple.macro

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MacroAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /**
     * Finds and clicks a quick setting or panel item by label
     */
    fun performSettingToggle(targetName: String): Boolean {
        // Pull down Quick Settings
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        
        android.os.Handler(mainLooper).postDelayed({
            val rootNode = rootInActiveWindow ?: return@postDelayed
            val targetNodes = rootNode.findAccessibilityNodeInfosByText(targetName)
            if (targetNodes.isNotEmpty()) {
                val node = targetNodes[0]
                val clickableNode = findClickableParent(node) ?: node
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            // Close Quick Settings shade
            android.os.Handler(mainLooper).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 600)
        }, 800)

        return true
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }
}
