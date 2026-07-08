package com.failproger.vozduhdrop.utils

import android.util.Log

object HceServiceUtils {

    fun checkCommandClass(commandApdu: ByteArray): Boolean {
        // CLA: ISO
        if (commandApdu.size >= 2 && commandApdu[0] == 0x00.toByte()) return true
        else Log.w(
            "HcEmulator.err",
            "APDU command class is not supported: ${(commandApdu[0].toInt() and 0xFF).toHex()}"
        )
        return false
    }

    fun isSelectAidCommand(commandApdu: ByteArray): Boolean {
        // INS: SELECT, P1: by AID
        return commandApdu.size >= 6 &&
                commandApdu[1] == 0xA4.toByte() && commandApdu[2] == 0x04.toByte()
    }

    fun checkAid(commandApdu: ByteArray, aid: ByteArray): Boolean {
        val apduAid = commandApdu.sliceArray(5..<commandApdu.size - 1)
        if (apduAid.contentEquals(aid)) return true
        else Log.w("HcEmulator", "APDU command AID is not correct: ${apduAid.toHexString()}")
        return false
    }

    fun isReadBinaryCommand(commandApdu: ByteArray): Boolean {
        // INS: READ BINARY
        return commandApdu.size == 5 && commandApdu[1] == 0xB0.toByte()
    }

    fun prepareDataToSend(commandApdu: ByteArray, data: ByteArray): ByteArray {
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
