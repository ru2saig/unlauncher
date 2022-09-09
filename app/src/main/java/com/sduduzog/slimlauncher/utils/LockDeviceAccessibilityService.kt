package com.sduduzog.slimlauncher.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent


var ACTION_LOCK = "com.jkuester.unlauncher.LOCK"

class LockDeviceAccessibilityService : AccessibilityService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if ((intent != null) && (ACTION_LOCK == intent.action.toString())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        }


        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

    }

    override fun onInterrupt() {

    }
}