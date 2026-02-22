package com.studiocamera.core.data.camera.ptpip

import com.studiocamera.core.network.TcpSocket

/**
 * PTP/IP packet types per CIPA DC-005 specification.
 */
object PtpIpPacketType {
    const val INIT_COMMAND_REQUEST = 0x00000001
    const val INIT_COMMAND_ACK = 0x00000002
    const val INIT_EVENT_REQUEST = 0x00000003
    const val INIT_EVENT_ACK = 0x00000004
    const val OPERATION_REQUEST = 0x00000006
    const val OPERATION_RESPONSE = 0x00000007
    const val START_DATA_PACKET = 0x00000009
    const val DATA_PACKET = 0x0000000A
    const val CANCEL_TRANSACTION = 0x0000000B
    const val END_DATA_PACKET = 0x0000000C
}

/**
 * Standard PTP operation codes.
 */
object PtpOpCode {
    const val OPEN_SESSION: UShort = 0x1002u
    const val CLOSE_SESSION: UShort = 0x1003u
    const val GET_DEVICE_PROP_VALUE: UShort = 0x1015u
    const val SET_DEVICE_PROP_VALUE: UShort = 0x1016u
    const val INITIATE_CAPTURE: UShort = 0x100Eu
    const val GET_OBJECT_INFO: UShort = 0x1008u
    const val GET_OBJECT: UShort = 0x1009u

    // PTP response codes
    const val RESPONSE_OK: UShort = 0x2001u
}

/**
 * A decoded PTP/IP packet.
 */
data class PtpIpPacket(
    val type: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PtpIpPacket) return false
        return type == other.type && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * type + payload.contentHashCode()
}

/**
 * Encodes and decodes PTP/IP packets per CIPA DC-005.
 *
 * Packet structure:
 * - Bytes [0..3]: Total packet length (32-bit LE, includes these 4 bytes)
 * - Bytes [4..7]: Packet type (32-bit LE)
 * - Bytes [8..]: Payload
 */
object PtpIpCodec {

    /** Encodes a packet into a byte array for transmission. */
    fun encode(packet: PtpIpPacket): ByteArray {
        val totalLength = 8 + packet.payload.size
        val data = ByteArray(totalLength)
        writeLeInt32(data, 0, totalLength)
        writeLeInt32(data, 4, packet.type)
        packet.payload.copyInto(data, 8)
        return data
    }

    /**
     * Reads exactly one PTP/IP packet from the TCP socket.
     * Blocks until the full packet is received.
     */
    fun readFromSocket(socket: TcpSocket): PtpIpPacket {
        // Read 8-byte header (length + type)
        val header = readExact(socket, 8)
        val totalLength = readLeInt32(header, 0)
        val type = readLeInt32(header, 4)

        val payloadLength = totalLength - 8
        val payload = if (payloadLength > 0) {
            readExact(socket, payloadLength)
        } else {
            ByteArray(0)
        }

        return PtpIpPacket(type, payload)
    }

    /** Builds an InitCommandRequest payload with a GUID and friendly name. */
    fun buildInitCommandPayload(guid: ByteArray, friendlyName: String): ByteArray {
        // GUID: 16 bytes
        // Friendly name: UTF-16LE null-terminated
        val nameBytes = encodeUtf16Le(friendlyName)
        val payload = ByteArray(16 + nameBytes.size + 4) // GUID + name + version
        guid.copyInto(payload, 0, 0, minOf(guid.size, 16))
        nameBytes.copyInto(payload, 16)
        // Protocol version: 0x00010000 (1.0)
        writeLeInt32(payload, 16 + nameBytes.size, 0x00010000)
        return payload
    }

    /** Builds an InitEventRequest payload with the connection number from InitCommandAck. */
    fun buildInitEventPayload(connectionNumber: Int): ByteArray {
        val payload = ByteArray(4)
        writeLeInt32(payload, 0, connectionNumber)
        return payload
    }

    /** Builds an OperationRequest payload. */
    fun buildOperationRequest(
        dataPhase: Int,
        opCode: UShort,
        transactionId: Int,
        params: IntArray = intArrayOf()
    ): ByteArray {
        // DataPhaseInfo (4) + OperationCode (2) + TransactionID (4) + Params (4 each)
        val payload = ByteArray(4 + 2 + 4 + params.size * 4)
        writeLeInt32(payload, 0, dataPhase)
        writeLeUInt16(payload, 4, opCode)
        writeLeInt32(payload, 6, transactionId)
        for (i in params.indices) {
            writeLeInt32(payload, 10 + i * 4, params[i])
        }
        return payload
    }

    /** Extracts the connection number from an InitCommandAck payload. */
    fun parseConnectionNumber(payload: ByteArray): Int {
        if (payload.size < 4) return 0
        return readLeInt32(payload, 0)
    }

    /**
     * Extracts the response code from an OperationResponse payload.
     * Per CIPA DC-005 §2.3.5, OperationResponse payload layout:
     * - Bytes [0..3]: DataPhaseInfo / TransactionID
     * - Bytes [4..5]: ResponseCode (16-bit LE)
     */
    fun parseResponseCode(payload: ByteArray): UShort {
        if (payload.size < 6) return 0u
        return readLeUInt16(payload, 4)
    }

    // --- LE encoding helpers ---

    fun readLeInt32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun readLeUInt16(data: ByteArray, offset: Int): UShort {
        return ((data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)).toUShort()
    }

    fun writeLeInt32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    fun writeLeUInt16(data: ByteArray, offset: Int, value: UShort) {
        val v = value.toInt()
        data[offset] = (v and 0xFF).toByte()
        data[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun readExact(socket: TcpSocket, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val remaining = length - offset
            // Read directly into result buffer at the correct offset
            val temp = ByteArray(remaining)
            val n = socket.receive(temp)
            if (n <= 0) error("Connection closed while reading PTP/IP packet (needed $length, got $offset)")
            temp.copyInto(result, offset, 0, n)
            offset += n
        }
        return result
    }

    private fun encodeUtf16Le(str: String): ByteArray {
        // UTF-16LE with null terminator (2 bytes per char + 2 null bytes)
        val bytes = ByteArray((str.length + 1) * 2)
        for (i in str.indices) {
            val c = str[i].code
            bytes[i * 2] = (c and 0xFF).toByte()
            bytes[i * 2 + 1] = ((c shr 8) and 0xFF).toByte()
        }
        // Last 2 bytes are already 0 (null terminator)
        return bytes
    }
}
