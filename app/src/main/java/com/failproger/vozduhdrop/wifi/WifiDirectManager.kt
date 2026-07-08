package com.failproger.vozduhdrop.wifi

import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import com.failproger.vozduhdrop.socket.ServerSocket
import com.failproger.vozduhdrop.socket.ClientSocket
import com.failproger.vozduhdrop.ui.MainActivity

class WifiDirectManager(
    private var context: Context,
    private var activity: MainActivity
) : WifiP2pManager.ConnectionInfoListener {

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)
    private val receiver = WifiDirectBroadcastReceiver(manager, channel, this)
    private var isReceiverRegistered = false
    private val serverSocket = ServerSocket()
    private val clientSocket = ClientSocket(activity)

    fun enableUpdate() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        isReceiverRegistered = true
    }

    fun disableUpdate() {
        context.unregisterReceiver(receiver)
        isReceiverRegistered = false
    }

    fun stop() {
        if (isReceiverRegistered) {
            disableUpdate()
            isReceiverRegistered = false
        }

        serverSocket.stop()
        clientSocket.stop()

        manager.removeGroup(channel, null)

    }

    @RequiresPermission(allOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    fun createGroupDeferred(): Deferred<Pair<String, String>?> {
        if (!hasWifiDirectPermission()) {
            Log.e("WifiDirectOwner.err", "Nearby wifi devices permission not granted")
            throw IllegalStateException("Nearby wifi devices permission not granted")
        }

        val deferred = CompletableDeferred<Pair<String, String>?>()

        manager.removeGroup(channel, null)
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i("WifiDirectOwner.inf", "Group create success")

                var attempts = 0
                val maxAttempts = 5
                val delayBetweenAttempts: Long = 200

                fun requestGroupInfo() {
                    manager.requestGroupInfo(channel) { group ->
                        if (group != null) {
                            val ssid = group.networkName
                            val pass = group.passphrase

                            deferred.complete(Pair(ssid, pass))
                        }
                        else {
                            attempts++
                            if (attempts < maxAttempts) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    requestGroupInfo()
                                }, delayBetweenAttempts)
                            } else deferred.complete(null)
                        }
                    }
                }
                requestGroupInfo()
            }
            override fun onFailure(reason: Int) {
                Log.w("WifiDirectOwner.war", "Error to create group: $reason")
                deferred.complete(null)
            }
        })
        return deferred
    }

    fun connectToDevice(ssid: String, pass: String) {
        if (!hasWifiDirectPermission()) {
            Log.e("WifiDirectClient.err", "Nearby wifi devices permission not granted")
            throw IllegalStateException("Nearby wifi devices permission not granted")
        }

        val config = WifiP2pConfig.Builder()
            .setNetworkName(ssid)
            .setPassphrase(pass)
            .build()

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i("WifiDirectClient.inf", "Success connect to $ssid")
            }
            override fun onFailure(reason: Int) {
                Log.w("WifiDirectClient.war", "Connect error: $reason")
            }
        })
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null) {
            if (info.isGroupOwner) {
                Log.d("ConnactionInfo.deb", "You are owner")
                serverSocket.start(8888)
            }
            else if (info.groupOwnerAddress != null) {
                val host = info.groupOwnerAddress.hostAddress
                Log.d("ConnactionInfo.deb", "You clinet of $host")
                clientSocket.start(host, 8888)
            }
        }
    }

    private fun hasWifiDirectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        else {
            context.checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

}
