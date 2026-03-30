package com.onebitmonochrome.blacksbbox.app

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.BActivityThread
import com.onebitmonochrome.blacksbbox.R

object AppFreezeButtonManager {
    private const val TAG = "AppFreezeButtonManager"
    private const val CONTAINER_TAG = "bbb_instant_freeze_container"
    private const val INSTANT_FREEZE_DELAY_MS = 180L
    private val handler = Handler(Looper.getMainLooper())

    fun sync(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        decor.post { attachOrRemove(activity, decor) }
        decor.postDelayed({ attachOrRemove(activity, decor) }, 300)
    }

    fun remove(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        decor.findViewWithTag<View>(CONTAINER_TAG)?.let { decor.removeView(it) }
    }

    private fun attachOrRemove(activity: Activity, decor: ViewGroup) {
        val hostContext = App.getContext()
        if (activity.isFinishing || activity.isDestroyed) {
            remove(activity)
            return
        }
        if (!AppFreezeManager.shouldShowInstantFreezeButton()) {
            remove(activity)
            return
        }

        val existing = decor.findViewWithTag<View>(CONTAINER_TAG)
        if (existing != null) {
            existing.visibility = View.VISIBLE
            existing.bringToFront()
            existing.translationZ = dp(activity, 24).toFloat()
            existing.requestLayout()
            existing.invalidate()
            return
        }

        val container =
                FrameLayout(activity).apply {
                    tag = CONTAINER_TAG
                    clipChildren = false
                    clipToPadding = false
                    isClickable = false
                    isFocusable = false
                }

        val button =
                AppCompatImageButton(activity).apply {
                    setImageDrawable(
                            AppCompatResources.getDrawable(hostContext, R.drawable.ic_snowflake)
                    )
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#C71F3B"))
                                setStroke(dp(activity, 1), Color.parseColor("#66FFFFFF"))
                            }
                    contentDescription =
                            hostContext.getString(R.string.freeze_app_instantly_title)
                    elevation = dp(activity, 16).toFloat()
                    translationZ = dp(activity, 24).toFloat()
                    setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10))
                    setOnClickListener { freezeCurrentApp(activity) }
                }

        val buttonParams =
                FrameLayout.LayoutParams(dp(activity, 52), dp(activity, 52)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(activity, 18)
                    marginEnd = dp(activity, 18)
                }
        container.addView(button, buttonParams)
        activity.addContentView(
                container,
                FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        .apply { gravity = Gravity.TOP or Gravity.START }
        )
        container.bringToFront()
        container.translationZ = dp(activity, 24).toFloat()
        container.requestLayout()
        container.invalidate()
        Log.d(
                TAG,
                "Attached instant freeze button: pkg=${BActivityThread.getAppPackageName()} userId=${BActivityThread.getUserId()} activity=${activity.javaClass.name}"
        )
    }

    private fun freezeCurrentApp(activity: Activity) {
        val packageName = BActivityThread.getAppPackageName()
        if (packageName.isNullOrBlank()) {
            return
        }
        val userId = BActivityThread.getUserId()
        try {
            Log.i(TAG, "Freezing running app from instant button: $packageName userId=$userId")
            remove(activity)
            closeGuestTask(activity)
            handler.postDelayed(
                    {
                        try {
                            BlackBoxCore.get().stopPackage(packageName, userId)
                        } catch (e: Exception) {
                            Log.e(
                                    TAG,
                                    "Instant freeze failed for $packageName userId=$userId: ${e.message}"
                            )
                        }
                    },
                    INSTANT_FREEZE_DELAY_MS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Instant freeze failed for $packageName userId=$userId: ${e.message}")
        }
    }

    private fun closeGuestTask(activity: Activity) {
        try {
            activity.moveTaskToBack(true)
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToBack failed: ${e.message}")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.finishAndRemoveTask()
            } else {
                activity.finish()
            }
        } catch (e: Exception) {
            Log.w(TAG, "finishAndRemoveTask failed: ${e.message}")
            try {
                activity.finishAffinity()
            } catch (inner: Exception) {
                Log.w(TAG, "finishAffinity failed: ${inner.message}")
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        value.toFloat(),
                        activity.resources.displayMetrics
                )
                .toInt()
    }
}
