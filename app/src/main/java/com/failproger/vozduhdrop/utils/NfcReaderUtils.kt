package com.failproger.vozduhdrop.utils

import android.util.Log

object NfcReaderUtils {

    fun checkResponseStatus(response: ByteArray): Boolean {
        if (response.size >= 2) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF

            if (sw1 == 0x90 && sw2 == 0x00) {
                return true
            } else {
                Log.w("NfcReader.err", "Emulator respond error: ${sw1.toHex()}${sw2.toHex()}")
                return false
            }
        } else {
            Log.w("NfcReader.err", "Unsupported response format: ${response.toHexString()}")
            return false
        }
    }

    fun getResponseData(response: ByteArray): String {
        val dataLength = response.size - 2
        val payload = response.copyOfRange(0, dataLength)

        return String(payload, Charsets.UTF_8)
    }
}
