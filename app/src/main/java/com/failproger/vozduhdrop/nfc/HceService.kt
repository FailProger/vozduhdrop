package com.failproger.vozduhdrop.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.failproger.vozduhdrop.utils.toHex
import com.failproger.vozduhdrop.utils.hexToByteArray

class HceService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return byteArrayOf(0x6F, 0x00)

        try {
            if (checkCommandClass(commandApdu)) {
                if (isSelectAidCommand(commandApdu)) {
                    val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
                    val aid = prefs.getString("aid", "")!!
                    Log.d("HcEmulator.deb", "Send AID: $aid")

                    return if (checkAid(commandApdu, aid.hexToByteArray())) {
                        byteArrayOf(0x90.toByte(), 0x00.toByte())
                    } else {
                        byteArrayOf(0x6A.toByte(), 0x82.toByte())
                    }
                }
                else if (isReadBinaryCommand(commandApdu)) {
                    val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
                    val data = prefs.getString("data", "")!!
                    Log.d("HcEmulator.deb", "Data to send: $data")

                    val preparedData = prepareDataToSend(commandApdu, data.toByteArray())
                    Log.i("HcEmulator.inf", "Data send success")

                    return preparedData
                }
                else {
                    Log.w(
                        "HcEmulator.war",
                        "APDU instruction is not supported: ${(commandApdu[1].toInt() and 0xFF).toHex()}"
                    )
                    return byteArrayOf(0x6D.toByte(), 0x00.toByte())
                }
            } else return byteArrayOf(0x6E.toByte(), 0x00.toByte())
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
        return byteArrayOf(0x00.toByte(), 0x00.toByte())
    }

    override fun onDeactivated(reason: Int) {
    }

    private fun checkCommandClass(commandApdu: ByteArray): Boolean {
        // CLA: ISO
        if (commandApdu.size >= 2 && commandApdu[0] == 0x00.toByte()) return true
        else Log.w(
            "HcEmulator.war",
            "APDU command class is not supported: ${(commandApdu[0].toInt() and 0xFF).toHex()}"
        )
        return false
    }

    private fun isSelectAidCommand(commandApdu: ByteArray): Boolean {
        // INS: SELECT, P1: by AID
        return commandApdu.size >= 6 &&
                commandApdu[1] == 0xA4.toByte() && commandApdu[2] == 0x04.toByte()
    }

    private fun checkAid(commandApdu: ByteArray, aid: ByteArray): Boolean {
        val apduAid = commandApdu.sliceArray(5..<commandApdu.size - 1)
        if (apduAid.contentEquals(aid)) return true
        else Log.w("HcEmulator.war", "APDU command AID is not correct: ${apduAid.toHexString()}")
        return false
    }

    private fun isReadBinaryCommand(commandApdu: ByteArray): Boolean {
        // INS: READ BINARY
        return commandApdu.size == 5 && commandApdu[1] == 0xB0.toByte()
    }

    private fun prepareDataToSend(commandApdu: ByteArray, data: ByteArray): ByteArray {
        val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
        var length = commandApdu[4].toInt() and 0xFF
        if (length == 0) length = 256

        val data = if (offset < data.size) {
            data.copyOfRange(offset, minOf(offset + length, data.size))
        } else {
            ByteArray(0)
        }
        val response = ByteArray(data.size + 2)
        System.arraycopy(data, 0, response, 0, data.size)

        response[response.size - 2] = 0x90.toByte()
        response[response.size - 1] = 0x00.toByte()

        return response
    }
}
