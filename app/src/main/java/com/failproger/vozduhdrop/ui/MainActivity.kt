package com.failproger.vozduhdrop.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.failproger.vozduhdrop.R
import com.failproger.vozduhdrop.nfc.HceService
import com.failproger.vozduhdrop.nfc.NfcReader
import com.failproger.vozduhdrop.wifi.WifiDirectManager

class MainActivity : AppCompatActivity() {
    // NFC
    private lateinit var aid: String
    private lateinit var nfcReader: NfcReader
    private lateinit var hceComponent: ComponentName
    private var nfcData: String? = null
    // WiFi-Direct
    private lateinit var wifiDirectManager: WifiDirectManager
    // View
    private lateinit var txtModeStatus: TextView
    // States
    private var isNfcReaderMode = false
    private var isHceMode = false
    private var isWifiDirectOn = false
    // Permissions
    private lateinit var wifiDirectPermission: String
    private val switchModeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) switchMode() }
    private val connectToDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val parts = nfcData!!.split(":", limit = 2)
            val ssid = parts[0]
            val pass = parts[1]

            wifiDirectManager.connectToDevice(ssid, pass)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNfc()
        setupWifiDirect()

        initView()
    }

    override fun onResume() {
        super.onResume()

        if (isNfcReaderMode) nfcReader.enable()
        else if (isHceMode) enableHce()

        if (isWifiDirectOn) wifiDirectManager.enableUpdate()
    }

    override fun onPause() {
        super.onPause()

        if (isNfcReaderMode) nfcReader.disable()
        else if (isHceMode) disableHce()

        if (isWifiDirectOn) wifiDirectManager.disableUpdate()
    }

    override fun onStop() {
        super.onStop()

        if (isWifiDirectOn) wifiDirectManager.stop()
    }

    private fun initView() {
        txtModeStatus = findViewById<TextView>(R.id.txt_mode_status)
        findViewById<Button>(R.id.btn_switch_mode).setOnClickListener {
            switchModeLauncher.launch(wifiDirectPermission)
        }
    }

    private fun setupNfc() {
        aid = getString(R.string.aid)
        nfcReader = NfcReader(this, this, aid)
        hceComponent = ComponentName(this, HceService::class.java)

        isNfcReaderMode = true
    }

    private fun setupWifiDirect() {
        wifiDirectManager = WifiDirectManager(this, this)
        wifiDirectPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.NEARBY_WIFI_DEVICES
        }
    }

    fun onNfcGetData(data: String) {
        nfcReader.disable()
        isNfcReaderMode = false

        nfcData = data

        wifiDirectManager.enableUpdate()
        isWifiDirectOn = true

        connectToDeviceLauncher.launch(wifiDirectPermission)

        Thread {
            Thread.sleep(500)

            wifiDirectManager.disableUpdate()
            isWifiDirectOn = false

            nfcReader.enable()
            isNfcReaderMode = true
        }
    }

    private fun switchMode() {
        if (isNfcReaderMode) {
            nfcReader.disable()
            isNfcReaderMode = false

            val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
            prefs.edit(true) { putString("aid", aid) }

            lifecycleScope.launch {
                wifiDirectManager.enableUpdate()
                isWifiDirectOn = true

                val pair = wifiDirectManager.createGroupDeferred().await()
                if (pair != null) {
                    val (ssid, pass) = pair
                    val data = "$ssid:$pass"

                    prefs.edit(true) { putString("data", data) }

                    enableHce()
                    isHceMode = true

                    txtModeStatus.text = "Emulator / Host"
                }
            }
        } else {
            disableHce()
            isHceMode = false

            wifiDirectManager.stop()
            deleteSharedPreferences("hce_data")
            isWifiDirectOn = false

            nfcReader.enable()
            isNfcReaderMode = true

            txtModeStatus.text = "Reader / Client"
        }
    }

    private fun enableHce() {
        packageManager.setComponentEnabledSetting(
            hceComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        isHceMode = true
    }

    private fun disableHce() {
        packageManager.setComponentEnabledSetting(
            hceComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        isHceMode = false
    }
}
