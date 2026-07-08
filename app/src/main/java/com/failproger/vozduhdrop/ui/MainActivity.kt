package com.failproger.vozduhdrop.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.util.Log
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
    // WiFi-Direct
    private lateinit var wifiDirectManager: WifiDirectManager
    // States
    private var isReaderMode = false
    private var isHceMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initView()
        setupNfc()
        setupWifiDirect()
    }

    override fun onResume() {
        super.onResume()

        if (isReaderMode) nfcReader.enable()
        wifiDirectManager.enable()
    }

    override fun onPause() {
        super.onPause()

        if (isReaderMode) nfcReader.disable()
        wifiDirectManager.disable()
    }

    override fun onStop() {
        super.onStop()

        disableHce()
        wifiDirectManager.removeGroup()
    }

    override fun onDestroy() {
        super.onDestroy()

        disableHce()
        wifiDirectManager.destroy()
    }

    private fun initView() {
        val btnSwitchMode = findViewById<Button>(R.id.btn_switch_mode)
        btnSwitchMode.setOnClickListener {
            switchMode()
        }
    }

    private fun setupNfc() {
        aid = getString(R.string.aid)
        nfcReader = NfcReader(this, this, aid)
        hceComponent = ComponentName(this, HceService::class.java)

        nfcReader.enable()
        isReaderMode = true
    }

    private fun setupWifiDirect() {
        wifiDirectManager = WifiDirectManager(this)
    }

    fun onNfcGetData(data: String) {
        nfcReader.disable()

        val parts = data.split(":", limit = 2)
        val ssid = parts[0]
        val pass = parts[1]

        wifiDirectManager.connectToDevice(ssid, pass)
    }

    private fun switchMode() {
        if (isReaderMode) {
            nfcReader.disable()
            isReaderMode = false

            val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
            prefs.edit(true) { putString("aid", aid) }

            lifecycleScope.launch {
                val pair = wifiDirectManager.createGroupDeferred().await()
                if (pair != null) {
                    val (ssid, pass) = pair
                    val data = "$ssid:$pass"
                    Log.i("Test.inf", "Data in lifecy: $data")

                    prefs.edit(true) { putString("data", data) }
                    enableHce()
                }
            }
        } else {
            disableHce()
            wifiDirectManager.removeGroup()
            deleteSharedPreferences("hce_data")

            nfcReader.enable()
            isReaderMode = true
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
