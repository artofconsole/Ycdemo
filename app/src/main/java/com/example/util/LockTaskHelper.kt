package com.example.util

import android.app.Activity
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object LockTaskHelper {

    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isSystemPinned(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun startSystemPinning(activity: Activity): Boolean {
        return try {
            if (!isSystemPinned(activity)) {
                activity.startLockTask()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopSystemPinning(activity: Activity) {
        try {
            if (isSystemPinned(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
            // Safe ignore if already unpinned
        }
    }

    fun setImmersiveMode(activity: Activity, enable: Boolean) {
        val window = activity.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enable) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun setLandscape(activity: Activity, landscape: Boolean) {
        if (isTvDevice(activity)) return
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
