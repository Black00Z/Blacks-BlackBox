package com.onebitmonochrome.blacksbbox.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import top.niunaijun.blackbox.BlackBoxCore

object AppFreezeManager {
    private const val TAG = "AppFreezeManager"
    private const val PREFS_NAME = "AppFreezeRules"
    private const val KEY_GLOBAL_AUTO_FREEZE = "global_auto_freeze_enabled"
    private const val KEY_INSTANT_FREEZE_BUTTON = "instant_freeze_button_enabled"
    private const val AUTO_FREEZE_DELAY_MS = 1200L

    private val handler = Handler(Looper.getMainLooper())
    private val startedActivityCounts = ConcurrentHashMap<String, Int>()
    private val pendingStops = ConcurrentHashMap<String, Runnable>()

    @Suppress("DEPRECATION")
    private fun prefs() =
            App.getContext()
                    .getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
                    )

    fun isGlobalAutoFreezeEnabled(): Boolean {
        return prefs().getBoolean(KEY_GLOBAL_AUTO_FREEZE, false)
    }

    fun setGlobalAutoFreezeEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_GLOBAL_AUTO_FREEZE, enabled).commit()
        if (!enabled) {
            cancelAllPendingFreezes()
        }
    }

    fun isInstantFreezeButtonEnabled(): Boolean {
        return prefs().getBoolean(KEY_INSTANT_FREEZE_BUTTON, false)
    }

    fun setInstantFreezeButtonEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_INSTANT_FREEZE_BUTTON, enabled).commit()
    }

    fun shouldShowInstantFreezeButton(): Boolean {
        return isInstantFreezeButtonEnabled()
    }

    fun isExcludedFromAutoFreeze(packageName: String, userId: Int): Boolean {
        return prefs().getBoolean(exclusionKey(packageName, userId), false)
    }

    fun setExcludedFromAutoFreeze(packageName: String, userId: Int, excluded: Boolean) {
        prefs().edit().putBoolean(exclusionKey(packageName, userId), excluded).commit()
    }

    fun shouldAutoFreeze(packageName: String, userId: Int): Boolean {
        return isGlobalAutoFreezeEnabled() && !isExcludedFromAutoFreeze(packageName, userId)
    }

    fun onActivityStarted(packageName: String?, userId: Int) {
        val key = activityKey(packageName, userId) ?: return
        cancelPendingFreeze(key)
        val updatedCount = (startedActivityCounts[key] ?: 0) + 1
        startedActivityCounts[key] = updatedCount
        Log.d(TAG, "onActivityStarted: $key count=$updatedCount")
    }

    fun onActivityResumed(packageName: String?, userId: Int) {
        val key = activityKey(packageName, userId) ?: return
        cancelPendingFreeze(key)
    }

    fun onActivityStopped(packageName: String?, userId: Int, isChangingConfigurations: Boolean) {
        val key = activityKey(packageName, userId) ?: return
        val currentCount = startedActivityCounts[key] ?: 0
        val updatedCount = (currentCount - 1).coerceAtLeast(0)
        if (updatedCount == 0) {
            startedActivityCounts.remove(key)
        } else {
            startedActivityCounts[key] = updatedCount
        }
        Log.d(
                TAG,
                "onActivityStopped: $key count=$updatedCount changingConfigurations=$isChangingConfigurations"
        )

        if (isChangingConfigurations) {
            return
        }
        if (!shouldAutoFreeze(packageName.orEmpty(), userId)) {
            return
        }
        if (updatedCount == 0) {
            scheduleFreeze(packageName.orEmpty(), userId, key)
        }
    }

    private fun scheduleFreeze(packageName: String, userId: Int, key: String) {
        cancelPendingFreeze(key)
        val runnable = Runnable {
            pendingStops.remove(key)
            if (!shouldAutoFreeze(packageName, userId)) {
                return@Runnable
            }
            if ((startedActivityCounts[key] ?: 0) > 0) {
                return@Runnable
            }
            try {
                Log.i(TAG, "Auto-freezing package after exit: $packageName userId=$userId")
                BlackBoxCore.get().stopPackage(packageName, userId)
            } catch (e: Exception) {
                Log.e(TAG, "Auto-freeze failed for $packageName userId=$userId: ${e.message}")
            }
        }
        pendingStops[key] = runnable
        handler.postDelayed(runnable, AUTO_FREEZE_DELAY_MS)
    }

    private fun cancelPendingFreeze(key: String) {
        pendingStops.remove(key)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelAllPendingFreezes() {
        pendingStops.values.forEach { handler.removeCallbacks(it) }
        pendingStops.clear()
    }

    private fun exclusionKey(packageName: String, userId: Int): String {
        return "exclude_auto_freeze_${userId}_$packageName"
    }

    private fun activityKey(packageName: String?, userId: Int): String? {
        if (packageName.isNullOrBlank()) {
            return null
        }
        return "$userId:$packageName"
    }
}
