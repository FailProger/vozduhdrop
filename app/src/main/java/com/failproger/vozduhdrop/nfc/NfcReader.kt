package com.failproger.vozduhdrop.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.content.Context
import android.util.Log
import com.failproger.vozduhdrop.ui.MainActivity
import com.failproger.vozduhdrop.utils.NfcReaderUtils
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

    fun enable() {
        if (nfcAdapter == null) {
            Log.e("NfcReader.err", "NFC is not supported on this device")
            throw Exception("NFC is not supported on this device")
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

                val selectAid = byteArrayOf(
                    // CLA: ISO, INS: SELECT, P1: by AID, P2: null
                    0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                    0x07.toByte(),          // Lc: 7
                    *aid.hexToByteArray(),  // AID
                    0x02.toByte()           // Le: 2
                )
                val aidResponse = isoDep.transceive(selectAid)

                if (NfcReaderUtils.checkResponseStatus(aidResponse)) {
                    val readBinary = byteArrayOf(
                        // CLA: ISO, INS: READ BINARY, P1: null, P2: null
                        0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(),
                        0x00.toByte()  // Le: 256
                    )
                    val dataResponse = isoDep.transceive(readBinary)

                    if (NfcReaderUtils.checkResponseStatus(dataResponse)) {
                        val data = NfcReaderUtils.getResponseData(dataResponse)
                        Log.d("NfcReader.deb", "Data: $data")

                        activity.onNfcGetData(data)
                    }
                }
            } catch (e: TagLostException) {
                Log.w("NfcReader.err", "Tag was lost")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isoDep.close()
            }
        }
    }
}
