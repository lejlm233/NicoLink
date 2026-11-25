package com.filmlens.nicolink.camera.ptp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * PTP/IP 传输层：命令通道 + 事件通道、Init 握手、事务读写。
 *
 * 只负责「把字节按 PTP/IP 包收发正确」，不解释具体业务操作。
 * 所有事务串行化（相机只接受一个命令通道），下载/取景/属性读写都排在同一把锁后面。
 */
class PtpIpTransport(private val log: (String) -> Unit) {

    private var commandSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var cmdIo: PacketIo? = null
    private var evtIo: PacketIo? = null
    private var eventThread: Thread? = null

    @Volatile
    private var closing = false

    private var txCounter = 0
    private val transactionLock = Mutex()

    private val _events = MutableSharedFlow<PtpEventPacket>(extraBufferCapacity = 64)

    /** 事件通道（ObjectAdded / CaptureComplete 等），UI 层当前按需订阅 */
    val events = _events.asSharedFlow()

    var cameraName: String = ""
        private set
    var cameraGuid: ByteArray = ByteArray(0)
        private set
    var connectionNumber: Int = 0
        private set

    val connected: Boolean
        get() = commandSocket?.isConnected == true && !closing

    /**
     * 建链：命令 socket 的 Init 握手 → 事件 socket 的 Init。
     * [guid] 必须是持久 GUID（相机用它在 STA 下记住主机）。
     * [network] 传相机热点所在的 Wi-Fi 网络（AP 模式必须绑定，否则流量走移动数据）。
     */
    suspend fun connect(
        host: String,
        port: Int,
        friendlyName: String,
        guid: ByteArray,
        network: android.net.Network? = null,
    ) = withContext(Dispatchers.IO) {
        close()
        closing = false
        log("TCP 连接 $host:$port …${if (network != null) "（绑定相机热点网络）" else ""}")

        val factory = network?.socketFactory
        val cmdSocket = if (factory != null) factory.createSocket() else Socket()
        cmdSocket.tcpNoDelay = true
        cmdSocket.keepAlive = true
        try {
            cmdSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            cmdSocket.close()
            throw PtpException("连不上 $host:$port（${e.message}）", e)
        }
        cmdSocket.soTimeout = READ_TIMEOUT_MS
        val cmd = PacketIo(cmdSocket.getInputStream(), cmdSocket.getOutputStream())

        cmd.send(PtpIp.TYPE_INIT_COMMAND_REQUEST, PtpCodec.initCommandRequest(guid, friendlyName))
        val ack = cmd.receive()
        when (ack.type) {
            PtpIp.TYPE_INIT_COMMAND_ACK -> {
                // 载荷：ConnectionID u32@0 + 相机 GUID 16B@4 + 相机名 UTF-16LE@20
                connectionNumber = if (ack.payload.size >= 4) PtpCodec.readU32(ack.payload, 0) else 0
                cameraGuid = if (ack.payload.size >= 20) ack.payload.copyOfRange(4, 20) else ByteArray(0)
                cameraName = if (ack.payload.size > 20) {
                    PtpCodec.readUtf16z(ack.payload, 20)
                } else {
                    ""
                }
                log("Init_Command_Ack：connection=$connectionNumber，相机名=「$cameraName」")
            }
            PtpIp.TYPE_INIT_FAIL -> {
                val reason = if (ack.payload.size >= 4) PtpCodec.readU32(ack.payload, 0) else -1
                cmdSocket.close()
                throw PtpInitRejectedException(reason)
            }
            else -> {
                cmdSocket.close()
                throw PtpException("Init 握手收到意外包类型 0x${ack.type.toString(16)}")
            }
        }

        val evSocket = if (factory != null) factory.createSocket() else Socket()
        evSocket.tcpNoDelay = true
        evSocket.keepAlive = true
            try {
                evSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                cmdSocket.close()
                evSocket.close()
                throw PtpException("事件通道连不上（${e.message}）", e)
            }
            evSocket.soTimeout = 0
            val evt = PacketIo(evSocket.getInputStream(), evSocket.getOutputStream())
            evt.send(PtpIp.TYPE_INIT_EVENT_REQUEST, PtpCodec.u32(connectionNumber))
            val evAck = evt.receive()
            if (evAck.type != PtpIp.TYPE_INIT_EVENT_ACK) {
                cmdSocket.close()
                evSocket.close()
                throw PtpException("Init_Event 收到意外包类型 0x${evAck.type.toString(16)}")
            }
            log("事件通道就绪")

            commandSocket = cmdSocket
            eventSocket = evSocket
            cmdIo = cmd
            evtIo = evt
            txCounter = 0
            eventThread = Thread({ eventLoop(evt) }, "ptp-event").apply {
                isDaemon = true
                start()
            }
        }

    /** 关闭 socket（相机侧会自行释放会话；正常断开前应先 CloseSession） */
    fun close() {
        closing = true
        runCatching { eventThread?.interrupt() }
        eventThread = null
        runCatching { evtIo?.let { it } }
        runCatching { eventSocket?.close() }
        runCatching { commandSocket?.close() }
        eventSocket = null
        commandSocket = null
        cmdIo = null
        evtIo = null
    }

    /**
     * 执行一次 PTP 事务。
     *
     * @param transactionId 传 0 给 OpenSession；其余情况自动递增
     * @param sink 有数据返回时收到的分块（下载直接写文件）；不传则整包进内存
     */
    suspend fun transaction(
        opcode: Int,
        params: List<Int> = emptyList(),
        dataPhase: Int = PtpIp.PHASE_NONE,
        dataOut: ByteArray? = null,
        transactionId: Int? = null,
        sink: DataSink? = null,
    ): PtpResponse = transactionLock.withLock {
        withContext(Dispatchers.IO) {
            val io = cmdIo ?: throw PtpException("未连接相机")
            if (closing) throw PtpException("连接已关闭")
            val id = transactionId ?: (++txCounter)
            io.send(
                PtpIp.TYPE_OPERATION_REQUEST,
                PtpCodec.operationRequest(dataPhase, opcode, id, params),
            )
            if (dataOut != null) sendDataOut(io, id, dataOut)
            readResult(io, id, sink)
        }
    }

    /** 只发不读（Probe 等心跳应答用不到，保留给 Cancel） */
    suspend fun cancel(transactionId: Int) = withContext(Dispatchers.IO) {
        cmdIo?.send(PtpIp.TYPE_CANCEL, PtpCodec.u32(transactionId))
    }

    private fun sendDataOut(io: PacketIo, transactionId: Int, data: ByteArray) {
        io.send(PtpIp.TYPE_START_DATA, PtpCodec.startData(transactionId, data.size.toLong()))
        if (data.isEmpty()) {
            io.send(PtpIp.TYPE_END_DATA, PtpCodec.u32(transactionId))
            return
        }
        var offset = 0
        while (offset < data.size) {
            val remaining = data.size - offset
            val chunk = minOf(remaining, OUT_CHUNK)
            val isLast = chunk == remaining
            // Data / End_Data 载荷 = transactionId + 原始数据，无内层长度头
            io.send(
                if (isLast) PtpIp.TYPE_END_DATA else PtpIp.TYPE_DATA,
                PtpCodec.u32(transactionId) + data.copyOfRange(offset, offset + chunk),
            )
            offset += chunk
        }
    }

    private fun readResult(io: PacketIo, transactionId: Int, sink: DataSink?): PtpResponse {
        val buffer = if (sink == null) ByteArrayOutputStream() else null
        var total = 0L
        while (true) {
            val packet = io.receive()
            when (packet.type) {
                PtpIp.TYPE_OPERATION_RESPONSE -> {
                    if (packet.payload.size < 6) throw PtpException("Operation_Response 包过短")
                    val code = PtpCodec.readU16(packet.payload, 0)
                    val id = PtpCodec.readU32(packet.payload, 2)
                    val params = readParams(packet.payload, 6)
                    if (id != transactionId) {
                        log("警告：响应事务号 $id 与请求 $transactionId 不一致")
                    }
                    return PtpResponse(code, id, params, buffer?.toByteArray())
                }
                PtpIp.TYPE_START_DATA -> {
                    if (packet.payload.size >= 12) {
                        total = PtpCodec.readU64(packet.payload, 4)
                        log("数据阶段开始：$total 字节")
                    }
                }
                PtpIp.TYPE_DATA, PtpIp.TYPE_END_DATA -> {
                    // Data / End_Data 载荷 = transactionId(4) + 原始数据，没有内层长度头
                    if (packet.payload.size < 4) throw PtpException("数据包过短")
                    val id = PtpCodec.readU32(packet.payload, 0)
                    if (id != transactionId) {
                        log("警告：数据包事务号 $id 与请求 $transactionId 不一致")
                    }
                    val dataLen = packet.payload.size - 4
                    if (dataLen > 0) {
                        if (sink != null) {
                            sink.write(packet.payload, 4, dataLen)
                        } else {
                            buffer?.write(packet.payload, 4, dataLen)
                        }
                    }
                }
                PtpIp.TYPE_PROBE_REQUEST -> io.send(PtpIp.TYPE_PROBE_RESPONSE)
                PtpIp.TYPE_EVENT -> handleEventPacket(packet)
                PtpIp.TYPE_CANCEL -> log("相机取消了事务 $transactionId")
                else -> log("命令通道收到未知包 0x${packet.type.toString(16)}")
            }
        }
    }

    private fun handleEventPacket(packet: RawPacket) {
        if (packet.payload.size < 6) return
        val code = PtpCodec.readU16(packet.payload, 0)
        val id = PtpCodec.readU32(packet.payload, 2)
        _events.tryEmit(PtpEventPacket(code, id, readParams(packet.payload, 6)))
    }

    private fun eventLoop(io: PacketIo) {
        try {
            while (!closing) {
                val packet = io.receive()
                when (packet.type) {
                    PtpIp.TYPE_PROBE_REQUEST -> {
                        io.send(PtpIp.TYPE_PROBE_RESPONSE)
                        log("收到 Probe_Request，已回 Probe_Response")
                    }
                    PtpIp.TYPE_EVENT -> handleEventPacket(packet)
                    else -> log("事件通道收到包 0x${packet.type.toString(16)}")
                }
            }
        } catch (e: Exception) {
            if (!closing) log("事件通道结束：${e.message}")
        }
    }

    private fun readParams(bytes: ByteArray, offset: Int): List<Int> {
        val out = ArrayList<Int>()
        var i = offset
        while (i + 3 < bytes.size) {
            out.add(PtpCodec.readU32(bytes, i))
            i += 4
        }
        return out
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6000
        const val READ_TIMEOUT_MS = 20000
        const val OUT_CHUNK = 1024 * 1024
    }
}
