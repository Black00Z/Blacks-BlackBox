package com.onebitmonochrome.blacksbbox.view.spoof

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.databinding.ActivityDeviceSpoofingBinding
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.BaseActivity
import top.niunaijun.blackbox.fake.device.DeviceSpoofManager
import top.niunaijun.blackbox.fake.device.DeviceSpoofProfile

class DeviceSpoofingActivity : BaseActivity() {

    private val viewBinding: ActivityDeviceSpoofingBinding by inflate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.device_spoofing, true)
        bindCurrentProfile()
        initActions()
    }

    private fun bindCurrentProfile() {
        fillFields(DeviceSpoofManager.getProfile(currentUserID()))
    }

    private fun initActions() {
        viewBinding.acceptChangeButton.setOnClickListener {
            val manufacturer = viewBinding.deviceManufacturerEdit.text?.toString().orEmpty().trim()
            val model = viewBinding.deviceModelEdit.text?.toString().orEmpty().trim()
            val androidId = viewBinding.androidIdEdit.text?.toString().orEmpty().trim()

            if (manufacturer.isBlank() || model.isBlank()) {
                toast(R.string.device_spoofing_fill_required)
                return@setOnClickListener
            }
            if (!DeviceSpoofManager.isValidAndroidId(androidId)) {
                toast(R.string.device_spoofing_invalid_android_id)
                return@setOnClickListener
            }

            DeviceSpoofManager.saveUserProfile(currentUserID(), manufacturer, model, androidId)
            fillFields(DeviceSpoofManager.getProfile(currentUserID()))
            toast(R.string.device_spoofing_saved)
        }

        viewBinding.resetDefaultsButton.setOnClickListener {
            DeviceSpoofManager.resetToDefaults(currentUserID())
            bindCurrentProfile()
            toast(R.string.device_spoofing_reset)
        }

        viewBinding.samsungPresetButton.setOnClickListener {
            fillFields(DeviceSpoofManager.getSamsungPreset(currentUserID()))
            toast(R.string.device_spoofing_samsung_loaded)
        }
    }

    private fun fillFields(profile: DeviceSpoofProfile) {
        viewBinding.deviceManufacturerEdit.setText(profile.manufacturer)
        viewBinding.deviceModelEdit.setText(profile.model)
        viewBinding.androidIdEdit.setText(profile.androidId)
    }

    companion object {
        fun start(context: Context, userId: Int) {
            val intent = Intent(context, DeviceSpoofingActivity::class.java)
            intent.putExtra("userID", userId)
            context.startActivity(intent)
        }
    }
}
