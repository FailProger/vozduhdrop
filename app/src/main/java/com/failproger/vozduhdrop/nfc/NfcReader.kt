package com.failproger.vozduhdrop.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.content.Context
import android.util.Log
import com.failproger.vozduhdrop.ui.MainActivity
import com.failproger.vozduhdrop.utils.toHex
import com.failproger.vozduhdrop.utils.hexToByteArray

class NfcReader(
    private val context: Context,
    private val activity: MainActivity,
    private val aid: String
) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val nfcReaderFlags =
        NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    private val selectAidApduCommand = byteArrayOf(
        // CLA: ISO, INS: SELECT, P1: by AID, P2: null
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
        0x07.toByte(),          // Lc: 7
        *aid.hexToByteArray(),  // AID
        0x02.toByte()           // Le: 2
    )
    private val readBinaryApduCommand = byteArrayOf(
        // CLA: ISO, INS: READ BINARY, P1: null, P2: null
        0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte()  // Le: 256
    )

    fun enable() {
        if (nfcAdapter == null) {
            Log.e("NfcReader.err", "NFC is not supported")
            throw Exception("NFC is not supported")
        }
        nfcAdapter.enableReaderMode(activity, this, nfcReaderFlags, null)
    }

    fun disable() {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag != null) {
            val isoDep = IsoDep.get(tag)

            try {
                isoDep.connect()
                val aidResponse = isoDep.transceive(selectAidApduCommand)

                if (checkResponseStatus(aidResponse)) {
                    val dataResponse = isoDep.transceive(readBinaryApduCommand)

                    if (checkResponseStatus(dataResponse)) {
                        val data = getResponseData(dataResponse)
                        Log.d("NfcReader.deb", "Get data: $data")

                        activity.onNfcGetData(data)
                    }
                }
            }
            catch (e: TagLostException) {
                Log.w("NfcReader.war", "Tag was lost")
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
            finally {
                isoDep.close()
            }
        }
    }

    private fun checkResponseStatus(response: ByteArray): Boolean {
        if (response.size >= 2) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF

            if (sw1 == 0x90 && sw2 == 0x00) {
                return true
            } else {
                Log.w("NfcReader.war", "Card respond error: ${sw1.toHex()}${sw2.toHex()}")
                return false
            }
        } else {
            Log.w("NfcReader.war", "Unsupported response format: ${response.toHexString()}")
            return false
        }
    }

    private fun getResponseData(response: ByteArray): String {
        val dataLength = response.size - 2
        val payload = response.copyOfRange(0, dataLength)

        return String(payload, Charsets.UTF_8)
    }

}
