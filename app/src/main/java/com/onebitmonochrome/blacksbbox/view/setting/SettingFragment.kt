package com.onebitmonochrome.blacksbbox.view.setting

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Build
import androidx.core.content.FileProvider
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import top.niunaijun.blackbox.BlackBoxCore
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.app.AppManager
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.gms.GmsManagerActivity
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingFragment : PreferenceFragmentCompat() {

    private val vpnPermissionResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val vpnPref =
                        findPreference<SwitchPreferenceCompat>("use_vpn_network")
                                ?: return@registerForActivityResult
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(true)
                    vpnPref.isChecked = true
                    toast(R.string.restart_module)
                } else {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(false)
                    vpnPref.isChecked = false
                    toast("VPN permission denied")
                }
            }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initGms()

        initDarkMode()

        initHostSigningFallback()

        if (Build.VERSION.SDK_INT >= 34) {
            val vpnPref = findPreference<SwitchPreferenceCompat>("use_vpn_network")
            if (vpnPref != null) {
                try {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(false)
                } catch (_: Exception) {
                }
                vpnPref.isChecked = false
                vpnPref.isEnabled = false
                vpnPref.summary = getString(R.string.vpn_not_supported_android14)
            }
        }

        invalidHideState {
            val rootHidePreference: Preference = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

        invalidHideState {
            val disableFlagSecurePreference: Preference = (findPreference("disable_flag_secure")!!)
            val mDisableFlagSecure = AppManager.mBlackBoxLoader.disableFlagSecure()
            disableFlagSecurePreference.setDefaultValue(mDisableFlagSecure)
            disableFlagSecurePreference
        }

        initSendLogs()
        initAboutLinks()
    }

    private fun initHostSigningFallback() {
        val pref = findPreference<SwitchPreferenceCompat>("host_signing_fallback") ?: return

        // Ensure UI reflects persisted value.
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
            pref.isChecked = sp.getBoolean(pref.key, false)
        } catch (_: Exception) {
        }

        var programmaticChange = false
        pref.setOnPreferenceChangeListener { _, newValue ->
            if (programmaticChange) {
                return@setOnPreferenceChangeListener true
            }

            val enable = (newValue == true)
            if (!enable) {
                toast(R.string.restart_module)
                return@setOnPreferenceChangeListener true
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.host_signing_fallback_dialog_title)
                .setMessage(R.string.host_signing_fallback_dialog_message)
                .setPositiveButton(R.string.enable) { _, _ ->
                    programmaticChange = true
                    try {
                        pref.isChecked = true
                    } finally {
                        programmaticChange = false
                    }
                    toast(R.string.restart_module)
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    programmaticChange = true
                    try {
                        pref.isChecked = false
                    } finally {
                        programmaticChange = false
                    }
                }
                .show()

            // Cancel immediate enabling; we'll apply it if the user confirms.
            false
        }
    }

    private fun initDarkMode() {
        val darkModePref = findPreference<SwitchPreferenceCompat>("dark_mode") ?: return

        // Ensure UI reflects persisted preference (in case delegate was changed elsewhere).
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
            darkModePref.isChecked = sp.getBoolean("dark_mode", false)
        } catch (_: Exception) {
        }

        darkModePref.setOnPreferenceChangeListener { _, newValue ->
            val enabled = (newValue == true)
            AppCompatDelegate.setDefaultNightMode(
                if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Apply immediately.
            activity?.recreate()
            true
        }
    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (BlackBoxCore.get().isSupportGms) {

            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "root_hide" -> {

                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }
                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
                "use_vpn_network" -> {
                    if (Build.VERSION.SDK_INT >= 34) {
                        try {
                            AppManager.mBlackBoxLoader.invalidUseVpnNetwork(false)
                        } catch (_: Exception) {
                        }
                        toast(R.string.vpn_not_supported_android14)
                        return@setOnPreferenceChangeListener false
                    }

                    val enableVpn = tmpHide
                    if (!enableVpn) {
                        AppManager.mBlackBoxLoader.invalidUseVpnNetwork(false)
                        toast(R.string.restart_module)
                        return@setOnPreferenceChangeListener true
                    }

                    val vpnIntent = VpnService.prepare(requireContext())
                    if (vpnIntent == null) {
                        AppManager.mBlackBoxLoader.invalidUseVpnNetwork(true)
                        toast(R.string.restart_module)
                        return@setOnPreferenceChangeListener true
                    }

                    vpnPermissionResult.launch(vpnIntent)
                    return@setOnPreferenceChangeListener false
                }
                "disable_flag_secure" -> {
                    AppManager.mBlackBoxLoader.invalidDisableFlagSecure(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
    private fun initSendLogs() {
        val sendLogsPreference: Preference? = findPreference("send_logs")
        sendLogsPreference?.setOnPreferenceClickListener {
            it.isEnabled = false
            toast(R.string.share_logs_preparing)
            Thread {
                try {
                    val logFile = buildShareableLogFile()
                    activity?.runOnUiThread {
                        sendLogsPreference.isEnabled = true
                        shareLogFile(logFile)
                    }
                } catch (_: Exception) {
                    activity?.runOnUiThread {
                        sendLogsPreference.isEnabled = true
                        toast(R.string.share_logs_failed)
                    }
                }
            }.start()
            true
        }
    }

    private fun initAboutLinks() {
        val linkMap = mapOf(
            "about_owner" to "https://github.com/Black00Z",
            "about_newblackbox" to "https://github.com/ALEX5402/NewBlackbox",
            "about_virtualapp" to "https://github.com/asLody/VirtualApp",
            "about_virtualapk" to "https://github.com/didi/VirtualAPK",
            "about_dobby" to "https://github.com/jmpews/Dobby",
            "about_xdl" to "https://github.com/hexhacking/xDL",
            "about_blackreflection" to "https://github.com/CodingGay/BlackReflection",
            "about_freereflection" to "https://github.com/tiann/FreeReflection"
        )

        for ((key, url) in linkMap) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                openLink(url)
                true
            }
        }

        findPreference<Preference>("about_ai_note")?.isSelectable = false
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast(R.string.share_logs_failed)
        }
    }

    private fun buildShareableLogFile(): File {
        val context = requireContext()
        val outDir = File(context.cacheDir, "shared_logs")
        if (!outDir.exists()) {
            outDir.mkdirs()
        }

        val fileName = "blackbox_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { fos ->
            val header = buildString {
                append("Caption: Manual Log Share from Settings\n\n")
                append(getDeviceInfoString())
                append("\n\n--- LOGCAT ---\n")
            }
            fos.write(header.toByteArray(StandardCharsets.UTF_8))

            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            process.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val len = input.read(buffer)
                    if (len <= 0) break
                    fos.write(buffer, 0, len)
                }
            }
        }
        return outFile
    }

    private fun shareLogFile(file: File) {
        val context = requireContext()
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Blacks BlackBox debug logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_logs_chooser_title)))
    }

    private fun getDeviceInfoString(): String {
        return buildString {
            append("DEVICE INFORMATION\n")
            append("------------------\n")
            append("Android Version: ").append(Build.VERSION.RELEASE).append("\n")
            append("SDK Level: ").append(Build.VERSION.SDK_INT).append("\n")
            append("Build ID: ").append(Build.ID).append("\n")
            append("Build Display: ").append(Build.DISPLAY).append("\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                append("Security Patch: ").append(Build.VERSION.SECURITY_PATCH).append("\n")
            }
            append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
            append("Brand: ").append(Build.BRAND).append("\n")
            append("Model: ").append(Build.MODEL).append("\n")
            append("Device: ").append(Build.DEVICE).append("\n")
            append("Product: ").append(Build.PRODUCT).append("\n")
            append("Board: ").append(Build.BOARD).append("\n")
            append("Hardware: ").append(Build.HARDWARE).append("\n")
            append("Supported ABIs: ").append(Build.SUPPORTED_ABIS.joinToString()).append("\n")
        }
    }
}
