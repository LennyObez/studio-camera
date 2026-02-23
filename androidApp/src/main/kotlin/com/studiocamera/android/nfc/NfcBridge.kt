package com.studiocamera.android.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import co.touchlab.kermit.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NfcBridge(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    var onTagRead: ((String) -> Unit)? = null

    val isNfcAvailable: Boolean
        get() = nfcAdapter != null

    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    fun enableDispatch() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Accept all NFC tags — foreground dispatch takes priority over AAR
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addDataType("application/vnd.wfa.wsc")
            },
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addDataType("text/plain")
            },
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addDataType("application/json")
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )
        val techLists = arrayOf(arrayOf(Ndef::class.java.name))
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    fun disableDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (_: Exception) {}
    }

    fun handleIntent(intent: Intent) {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return

        val tag = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return

        // Try reading from NDEF message in the intent first (faster, no I/O)
        val ndefMessages = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        val message = (ndefMessages?.firstOrNull() as? NdefMessage) ?: run {
            // Fall back to reading from tag
            val ndef = Ndef.get(tag) ?: return
            try {
                ndef.connect()
                ndef.ndefMessage
            } catch (e: Exception) {
                Logger.w("NfcBridge") { "Tag reading failed: ${e.message}" }
                null
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        } ?: return

        processNdefMessage(message)
    }

    private fun processNdefMessage(message: NdefMessage) {
        val records = message.records

        Logger.d("NfcBridge") { "NDEF message with ${records.size} records" }
        records.forEachIndexed { i, r ->
            Logger.d("NfcBridge") {
                "Record[$i]: tnf=${r.tnf}, type=${String(r.type, Charsets.UTF_8)}, " +
                    "id=${r.id?.let { String(it, Charsets.UTF_8) } ?: "(none)"}, " +
                    "payload=${r.payload.size} bytes"
            }
        }

        // Strategy 1: Look for WSC MIME record (standard Wi-Fi Handover)
        for (record in records) {
            if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                val type = String(record.type, Charsets.UTF_8)
                if (type == "application/vnd.wfa.wsc") {
                    val wscResult = parseWscPayload(record.payload)
                    if (wscResult != null) {
                        onTagRead?.invoke(wscResult)
                        return
                    }
                    Logger.d("NfcBridge") { "WSC MIME record found but TLV parsing returned no SSID" }
                }
            }
        }

        // Strategy 2: Look inside Handover Select record for embedded WSC
        // Sony cameras use NFC Forum Connection Handover with "Hs" record
        for (record in records) {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN) {
                val type = String(record.type, Charsets.UTF_8)
                if (type == "Hs" || type == "Hr") {
                    val wscResult = parseHandoverSelect(record.payload, records)
                    if (wscResult != null) {
                        onTagRead?.invoke(wscResult)
                        return
                    }
                }
            }
        }

        // Strategy 3: Brute-force scan all record payloads for WSC TLV data
        // Some tags embed WSC data without proper MIME typing
        for (record in records) {
            if (isAarRecord(record)) continue
            if (record.payload.size >= 8) {
                val wscResult = parseWscPayload(record.payload)
                if (wscResult != null) {
                    onTagRead?.invoke(wscResult)
                    return
                }
            }
        }

        // Strategy 4: Byte-scan all non-AAR record payloads for WSC attribute markers
        for (record in records) {
            if (isAarRecord(record)) continue
            if (record.payload.size >= 8) {
                val wscResult = scanForWscAttributes(record.payload)
                if (wscResult != null) {
                    onTagRead?.invoke(wscResult)
                    return
                }
            }
        }

        // Strategy 5: Fallback — extract text/JSON payload
        for (record in records) {
            if (isAarRecord(record)) continue
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN) {
                val type = String(record.type, Charsets.UTF_8)
                if (type == "T") {
                    val payload = record.payload
                    val languageCodeLength = payload[0].toInt() and 0x3F
                    if (1 + languageCodeLength < payload.size) {
                        val text = String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, Charsets.UTF_8)
                        onTagRead?.invoke(text)
                        return
                    }
                } else if (type == "U") {
                    continue // Skip URI records (likely Play Store link)
                }
            } else if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                val type = String(record.type, Charsets.UTF_8)
                // Skip WSC MIME records — already handled by Strategy 1
                if (type == "application/vnd.wfa.wsc") continue
                val text = String(record.payload, Charsets.UTF_8)
                onTagRead?.invoke(text)
                return
            }
        }

        Logger.w("NfcBridge") { "No usable data found in NFC tag" }
    }

    /** Returns true for Android Application Records (AAR) which should be skipped. */
    private fun isAarRecord(record: NdefRecord): Boolean {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE) return false
        val type = String(record.type, Charsets.UTF_8)
        return type == "android.com:pkg"
    }

    /**
     * Parse Handover Select/Request record to find referenced WSC carrier data.
     * The Hs/Hr payload contains: version (1 byte) + embedded NDEF message with AC records.
     * Each AC record references a carrier data record by its NDEF Record ID.
     */
    private fun parseHandoverSelect(hsPayload: ByteArray, allRecords: Array<NdefRecord>): String? {
        if (hsPayload.size < 2) return null

        val version = hsPayload[0].toInt() and 0xFF
        Logger.d("NfcBridge") { "Handover Select version: ${version shr 4}.${version and 0xF}" }

        // Parse the embedded NDEF message to find Alternative Carrier (ac) records
        val embeddedBytes = hsPayload.copyOfRange(1, hsPayload.size)
        val carrierDataIds = mutableListOf<ByteArray>()
        try {
            val embeddedMessage = NdefMessage(embeddedBytes)
            for (record in embeddedMessage.records) {
                if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    String(record.type, Charsets.UTF_8) == "ac"
                ) {
                    val payload = record.payload
                    if (payload.size >= 2) {
                        val refLen = payload[1].toInt() and 0xFF
                        if (payload.size >= 2 + refLen && refLen > 0) {
                            val refId = payload.copyOfRange(2, 2 + refLen)
                            carrierDataIds.add(refId)
                            Logger.d("NfcBridge") { "AC record references carrier ID: ${String(refId, Charsets.UTF_8)}" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.d("NfcBridge") { "Could not parse embedded Hs NDEF: ${e.message}" }
        }

        // Look up carrier data records by their NDEF Record ID
        for (cdId in carrierDataIds) {
            for (record in allRecords) {
                if (record.id != null && record.id.contentEquals(cdId)) {
                    Logger.d("NfcBridge") {
                        "Found carrier data record by ID: tnf=${record.tnf}, type=${String(record.type, Charsets.UTF_8)}"
                    }
                    val wscResult = parseWscPayload(record.payload)
                    if (wscResult != null) return wscResult
                }
            }
        }

        // Fallback: try all non-Hs, non-AAR records as potential WSC data
        for (record in allRecords) {
            val type = String(record.type, Charsets.UTF_8)
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && (type == "Hs" || type == "Hr")) continue
            if (isAarRecord(record)) continue

            if (record.payload.size >= 8) {
                val result = parseWscPayload(record.payload)
                if (result != null) return result
            }
        }

        return null
    }

    /**
     * Parses Wi-Fi Simple Configuration TLV data from NFC Wi-Fi Handover.
     * Returns a WIFI: format string that ParseQrPayloadUseCase can process.
     *
     * Handles both flat TLV structures and the nested Credential (0x100E) wrapper
     * used by Sony and other camera manufacturers.
     *
     * WSC TLV types:
     * - 0x100E = Credential (container — recurse into it)
     * - 0x1045 = SSID (Network Name)
     * - 0x1027 = Network Key (password)
     * - 0x1003 = Auth Type
     * - 0x1020 = MAC Address
     */
    private fun parseWscPayload(data: ByteArray): String? {
        var ssid: String? = null
        var password: String? = null
        var authType = "WPA"

        fun parseTlv(buffer: ByteBuffer) {
            while (buffer.remaining() >= 4) {
                val type = buffer.short.toInt() and 0xFFFF
                val length = buffer.short.toInt() and 0xFFFF

                if (length < 0 || length > 1024 || buffer.remaining() < length) break

                if (type == 0x100E) {
                    // Credential container — parse its contents recursively
                    val credBytes = ByteArray(length)
                    buffer.get(credBytes)
                    parseTlv(ByteBuffer.wrap(credBytes).order(ByteOrder.BIG_ENDIAN))
                } else {
                    val valueBytes = ByteArray(length)
                    buffer.get(valueBytes)

                    when (type) {
                        0x1045 -> {
                            ssid = String(valueBytes, Charsets.UTF_8)
                            Logger.d("NfcBridge") { "WSC TLV SSID found (${valueBytes.size} bytes)" }
                        }
                        0x1027 -> {
                            password = String(valueBytes, Charsets.UTF_8)
                            Logger.d("NfcBridge") { "WSC TLV Network Key found (${valueBytes.size} bytes)" }
                        }
                        0x1003 -> {
                            if (valueBytes.size >= 2) {
                                val auth = ((valueBytes[0].toInt() and 0xFF) shl 8) or
                                    (valueBytes[1].toInt() and 0xFF)
                                authType = when (auth) {
                                    0x0001 -> "Open"
                                    0x0002 -> "WPA"
                                    0x0004 -> "Shared"
                                    0x0008 -> "WPA"
                                    0x0010 -> "WPA2"
                                    0x0020 -> "WPA2"
                                    else -> "WPA"
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            parseTlv(ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN))
        } catch (e: Exception) {
            Logger.w("NfcBridge") { "WSC TLV parse error: ${e.message}" }
            return null
        }

        if (ssid == null) return null

        return "WIFI:T:$authType;S:$ssid;P:${password ?: ""};;".also {
            Logger.d("NfcBridge") { "Parsed WSC credentials: authType=$authType" }
        }
    }

    /**
     * Last-resort byte scanner: scans raw bytes for WSC attribute markers.
     * Handles cases where TLV parsing fails due to alignment issues or extra framing.
     */
    private fun scanForWscAttributes(data: ByteArray): String? {
        var ssid: String? = null
        var password: String? = null

        var i = 0
        while (i < data.size - 4) {
            val type = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val length = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)

            // Check for valid WSC attribute range with sane length
            if (type in 0x1000..0x10FF && length in 1..256 && i + 4 + length <= data.size) {
                when (type) {
                    0x1045 -> ssid = String(data, i + 4, length, Charsets.UTF_8)
                    0x1027 -> password = String(data, i + 4, length, Charsets.UTF_8)
                }
                i += 4 + length
            } else {
                i++
            }
        }

        if (ssid != null) {
            Logger.d("NfcBridge") { "Byte-scan found WSC credentials" }
            return "WIFI:T:WPA;S:$ssid;P:${password ?: ""};;"
        }
        return null
    }
}
