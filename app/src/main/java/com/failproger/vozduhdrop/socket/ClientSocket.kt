package com.failproger.vozduhdrop.socket

import android.view.View
import android.util.Log
import com.failproger.vozduhdrop.ui.MainActivity
import com.google.android.material.snackbar.Snackbar
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class ClientSocket(
    private val activity: MainActivity
) {
    private var socket: Socket? = null
    private var isRunning = false

    fun start(host: String, port: Int) {
        if (isRunning) return
        Thread {
            try {
                socket = Socket(host, port)
                isRunning = true

                val input = BufferedReader(InputStreamReader(socket?.getInputStream()))
                var line: String?
                while (isRunning && socket?.isConnected == true) {
                    line = input.readLine()
                    if (line != null) {
                        Log.i("Client.inf", "Get message: $line")
                        val view = activity.findViewById<View>(android.R.id.content)
                        Snackbar.make(view, "Get massage: $line", Snackbar.LENGTH_LONG).show()
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket?.close()
                socket = null
                isRunning = false
            }
        }.start()
    }

    fun stop() {
        socket?.close()
        socket = null
        isRunning = false
    }
}