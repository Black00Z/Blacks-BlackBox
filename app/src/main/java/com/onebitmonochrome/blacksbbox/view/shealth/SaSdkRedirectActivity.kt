package com.onebitmonochrome.blacksbbox.view.shealth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.SamsungHealthCompat

class SaSdkRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }

        try {
            val userId = SamsungHealthCompat.getLastSaSdkRedirectUserId()
            val forward = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage("com.sec.android.app.shealth")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            Log.d("SaSdkRedirect", "Forwarding sasdk redirect to BlackBox userId=$userId uri=$uri")
            BlackBoxCore.get().startActivity(forward, userId)
        } catch (t: Throwable) {
            Log.w("SaSdkRedirect", "Failed to forward sasdk redirect", t)
        } finally {
            finish()
        }
    }
}
