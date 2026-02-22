package com.studiocamera.core.data.camera.ptpip

import co.touchlab.kermit.Logger
import com.studiocamera.core.network.TcpSocket

/**
 * Manages a PTP/IP session over dual TCP connections (command + event).
 *
 * Per CIPA DC-005, the initiator (phone) opens two TCP connections:
 * 1. Command/Data connection — for PTP operations and data transfers
 * 2. Event connection — for asynchronous camera events
 *
 * Both connections start with an Init handshake before PTP operations begin.
 */
class PtpIpSession(
    private val host: String,
    private val commandPort: Int,
    private val eventPort: Int = commandPort
) {
    companion object {
        private const val TAG = "PtpIpSession"
        private val DEVICE_GUID = ByteArray(16) { (it + 0x42).toByte() } // Studio Camera GUID
        private const val DEVICE_NAME = "Studio Camera"
    }

    private var commandSocket: TcpSocket? = null
    private var eventSocket: TcpSocket? = null
    private var connectionNumber: Int = 0
    @Volatile private var transactionId: Int = 1
    var sessionOpen: Boolean = false
        private set

    /** Opens command and event connections, performs Init handshake, opens PTP session. */
    fun open() {
        Logger.d(TAG) { "Opening PTP/IP session to $host:$commandPort" }

        try {
            // 1. Command connection + init
            val cmdSock = TcpSocket(host, commandPort)
            commandSocket = cmdSock

            val initPayload = PtpIpCodec.buildInitCommandPayload(DEVICE_GUID, DEVICE_NAME)
            val initPacket = PtpIpPacket(PtpIpPacketType.INIT_COMMAND_REQUEST, initPayload)
            cmdSock.send(PtpIpCodec.encode(initPacket))

            val ack = PtpIpCodec.readFromSocket(cmdSock)
            check(ack.type == PtpIpPacketType.INIT_COMMAND_ACK) {
                "Expected InitCommandAck, got type 0x${ack.type.toString(16)}"
            }
            connectionNumber = PtpIpCodec.parseConnectionNumber(ack.payload)
            Logger.d(TAG) { "Command init OK, connection# $connectionNumber" }

            // 2. Event connection + init
            val evtSock = TcpSocket(host, eventPort)
            eventSocket = evtSock

            val evtPayload = PtpIpCodec.buildInitEventPayload(connectionNumber)
            val evtPacket = PtpIpPacket(PtpIpPacketType.INIT_EVENT_REQUEST, evtPayload)
            evtSock.send(PtpIpCodec.encode(evtPacket))

            val evtAck = PtpIpCodec.readFromSocket(evtSock)
            check(evtAck.type == PtpIpPacketType.INIT_EVENT_ACK) {
                "Expected InitEventAck, got type 0x${evtAck.type.toString(16)}"
            }
            Logger.d(TAG) { "Event init OK" }

            // 3. OpenSession PTP operation
            val openResult = executeOperation(PtpOpCode.OPEN_SESSION, intArrayOf(1))
            check(openResult.first == PtpOpCode.RESPONSE_OK) {
                "OpenSession failed: 0x${openResult.first.toString(16)}"
            }
            sessionOpen = true
            Logger.i(TAG) { "PTP/IP session opened" }
        } catch (e: Exception) {
            // Clean up sockets on any failure to prevent leaks
            close()
            throw e
        }
    }

    /** Closes the PTP session and both TCP connections. */
    fun close() {
        if (sessionOpen) {
            try {
                executeOperation(PtpOpCode.CLOSE_SESSION)
            } catch (_: Exception) { }
            sessionOpen = false
        }
        eventSocket?.close()
        eventSocket = null
        commandSocket?.close()
        commandSocket = null
        Logger.d(TAG) { "PTP/IP session closed" }
    }

    /**
     * Executes a PTP operation and returns (responseCode, responsePayload).
     * Handles the data phase if the camera sends data back.
     */
    fun executeOperation(
        opCode: UShort,
        params: IntArray = intArrayOf()
    ): Pair<UShort, ByteArray> {
        val sock = commandSocket ?: error("Command socket not open")
        val txId = transactionId++

        // Send operation request (dataPhase=1 means no data from initiator)
        val reqPayload = PtpIpCodec.buildOperationRequest(
            dataPhase = 1,
            opCode = opCode,
            transactionId = txId,
            params = params
        )
        sock.send(PtpIpCodec.encode(PtpIpPacket(PtpIpPacketType.OPERATION_REQUEST, reqPayload)))

        // Read response — may include data phase packets
        var dataPayload = ByteArray(0)
        var packetsRead = 0
        val maxPackets = 10_000 // guard against infinite loop on malformed streams
        while (packetsRead < maxPackets) {
            packetsRead++
            val response = PtpIpCodec.readFromSocket(sock)
            when (response.type) {
                PtpIpPacketType.OPERATION_RESPONSE -> {
                    val responseCode = PtpIpCodec.parseResponseCode(response.payload)
                    return Pair(responseCode, dataPayload)
                }
                PtpIpPacketType.START_DATA_PACKET -> {
                    // Start of data phase — total length is in payload
                    // Continue reading DataPackets until EndDataPacket
                }
                PtpIpPacketType.DATA_PACKET -> {
                    // Skip the 4-byte transaction ID prefix
                    val data = if (response.payload.size > 4) {
                        response.payload.copyOfRange(4, response.payload.size)
                    } else {
                        response.payload
                    }
                    dataPayload = dataPayload + data
                }
                PtpIpPacketType.END_DATA_PACKET -> {
                    // Final data chunk — skip 4-byte transaction ID prefix
                    val data = if (response.payload.size > 4) {
                        response.payload.copyOfRange(4, response.payload.size)
                    } else {
                        response.payload
                    }
                    dataPayload = dataPayload + data
                }
                else -> {
                    Logger.w(TAG) { "Unexpected packet type: 0x${response.type.toString(16)}" }
                }
            }
        }
        error("PTP/IP response exceeded $maxPackets packets — aborting")
    }

    /**
     * Executes an operation that sends data to the camera.
     * Used for SetDevicePropValue.
     */
    fun executeOperationWithData(
        opCode: UShort,
        params: IntArray = intArrayOf(),
        data: ByteArray
    ): Pair<UShort, ByteArray> {
        val sock = commandSocket ?: error("Command socket not open")
        val txId = transactionId++

        // Send operation request (dataPhase=2 means data from initiator)
        val reqPayload = PtpIpCodec.buildOperationRequest(
            dataPhase = 2,
            opCode = opCode,
            transactionId = txId,
            params = params
        )
        sock.send(PtpIpCodec.encode(PtpIpPacket(PtpIpPacketType.OPERATION_REQUEST, reqPayload)))

        // Send StartDataPacket
        val startDataPayload = ByteArray(12)
        PtpIpCodec.writeLeInt32(startDataPayload, 0, txId)
        // Total data length as 64-bit LE
        PtpIpCodec.writeLeInt32(startDataPayload, 4, data.size)
        PtpIpCodec.writeLeInt32(startDataPayload, 8, 0)
        sock.send(PtpIpCodec.encode(PtpIpPacket(PtpIpPacketType.START_DATA_PACKET, startDataPayload)))

        // Send EndDataPacket with actual data
        val endPayload = ByteArray(4 + data.size)
        PtpIpCodec.writeLeInt32(endPayload, 0, txId)
        data.copyInto(endPayload, 4)
        sock.send(PtpIpCodec.encode(PtpIpPacket(PtpIpPacketType.END_DATA_PACKET, endPayload)))

        // Read response — skip any unexpected packets until we get OperationResponse
        var packetsRead = 0
        val maxPackets = 100
        while (packetsRead < maxPackets) {
            packetsRead++
            val response = PtpIpCodec.readFromSocket(sock)
            if (response.type == PtpIpPacketType.OPERATION_RESPONSE) {
                val responseCode = PtpIpCodec.parseResponseCode(response.payload)
                return Pair(responseCode, ByteArray(0))
            }
            Logger.d(TAG) { "executeOperationWithData: skipping packet type 0x${response.type.toString(16)}" }
        }
        error("executeOperationWithData: no OperationResponse after $maxPackets packets")
    }

    /** Reads a single raw packet from the command socket. Used for live view streaming. */
    fun readRawPacket(): PtpIpPacket {
        val sock = commandSocket ?: error("Command socket not open")
        return PtpIpCodec.readFromSocket(sock)
    }
}
