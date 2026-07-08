package com.failproger.vozduhdrop.wifi

import android.net.wifi.p2p.WifiP2pManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: WifiDirectManager
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
//                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                manager.requestConnectionInfo(channel, activity)
            }
        }
    }

}
