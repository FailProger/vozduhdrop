package com.failproger.vozduhdrop.socket

import android.util.Log
import java.io.PrintWriter
import java.net.ServerSocket

class ServerSocket {
    private var socket: ServerSocket? = null
    private var isRunning = false

    fun start(port: Int) {
        if (isRunning) return
        Thread {
            try {
                socket = ServerSocket(port)
                isRunning = true
                Log.d("Server.deb", "Server started")

                val clientSocket = socket?.accept()
                clientSocket?.let {
                    Thread.sleep(500)
                    Log.d("Server.deb", "Client connected")

                    val output = it.getOutputStream()
                    val writer = PrintWriter(output, true)
                    writer.println("Hello, World! It send by WiFi-Direct!")
                    writer.flush()
                    Log.d("Server.deb", "Message send")
                }
                clientSocket?.close()
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