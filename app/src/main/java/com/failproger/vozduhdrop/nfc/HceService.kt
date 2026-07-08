package com.failproger.vozduhdrop.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.failproger.vozduhdrop.utils.HceServiceUtils
import com.failproger.vozduhdrop.utils.hexToByteArray
import com.failproger.vozduhdrop.utils.toHex

class HceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return byteArrayOf(0x6F, 0x00)

        try {
            if (HceServiceUtils.checkCommandClass(commandApdu)) {
                if (HceServiceUtils.isSelectAidCommand(commandApdu)) {
                    val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
                    val aid = prefs.getString("aid", "")!!
                    Log.d("HcEmulator.deb", "AID: $aid")

                    return if (HceServiceUtils.checkAid(commandApdu, aid.hexToByteArray())) {
                        byteArrayOf(0x90.toByte(), 0x00.toByte())
                    } else {
                        byteArrayOf(0x6A.toByte(), 0x82.toByte())
                    }
                }
                else if (HceServiceUtils.isReadBinaryCommand(commandApdu)) {
                    val prefs = getSharedPreferences("hce_data", MODE_PRIVATE)
                    val data = prefs.getString("data", "")!!
                    Log.d("HcEmulator.deb", "Data: $data")

                    val preparedData = HceServiceUtils.prepareDataToSend(commandApdu, data.toByteArray())
                    Log.i("HcEmulator.inf", "Data send success")

                    return preparedData
                }
                else {
                    Log.w(
                        "HcEmulator.err",
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
}
