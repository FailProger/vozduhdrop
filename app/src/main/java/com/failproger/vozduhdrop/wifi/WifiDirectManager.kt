package com.failproger.vozduhdrop.wifi

import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class WifiDirectManager(
    private var context: Context
) : WifiP2pManager.ConnectionInfoListener {

    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, context.mainLooper, null)
    private val receiver = WifiDirectBroadcastReceiver(manager, channel, this)
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    private var isServerRunning = false
    private var isReading = false


    fun enable() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    fun disable() {
        context.unregisterReceiver(receiver)
    }

    fun destroy() {
        removeGroup()
        clientSocket?.close()
        clientSocket = null
        isReading = false
    }

    fun createGroupDeferred(): Deferred<Pair<String, String>?> {
        val deferred = CompletableDeferred<Pair<String, String>?>()

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirect.deb", "Group create success")

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
                Log.e("WifiDirect", "Error to create group: $reason")
                deferred.complete(null)
            }
        })
        return deferred
    }

    fun removeGroup() {
        serverSocket?.close()
        serverSocket = null
        isServerRunning = false
        manager.removeGroup(channel, null)
    }

    fun connectToDevice(ssid: String, pass: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
            return
        val config = WifiP2pConfig.Builder()
            .setNetworkName(ssid)
            .setPassphrase(pass)
            .build()

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("Client.deb", "Connect to WiFi-Direct")
            }
            override fun onFailure(reason: Int) {
                Log.d("Client.err", "Connect error: $reason")
            }
        })
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null) {
            if (info.isGroupOwner && !isServerRunning) {
                Log.d("ConnChange.deb", "You are owner")
                startServer()
            }
            else if (!info.isGroupOwner && info.groupOwnerAddress != null) {
                val host = info.groupOwnerAddress.hostAddress
                Log.d("ConnChange.deb", "HostIP: $host")
                startClient(host)
            }
        }
    }

    private fun startServer() {
        Thread {
            serverSocket = ServerSocket(8888)
            isServerRunning = true
            Log.d("Server.deb", "Server started")

            val clientSocket = serverSocket?.accept()
            clientSocket?.let {
                Thread.sleep(500)
                Log.d("Server.deb", "Client connected")

                val output = it.getOutputStream()
                val writer = PrintWriter(output, true)
                writer.println("Hello by WiFi-Direct")
                writer.flush()

                Log.d("Server.deb", "Message send")
                it.close()
            }

            serverSocket?.close()
            isServerRunning = false
        }.start()
    }

    private fun startClient(host: String) {
        if (isReading) return
        Thread {
            Thread.sleep(1000)
            try {
                clientSocket = Socket(host, 8888)
                isReading = true
                val input = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
                var line: String?
                while (isReading && clientSocket?.isConnected == true) {
                    line = input.readLine()
                    if (line != null) {
                        Log.i("Client.inf", "Get message: $line")
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isReading = false
                clientSocket?.close()
                clientSocket = null
            }
        }.start()
    }

}
