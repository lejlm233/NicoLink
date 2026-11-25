package com.filmlens.nicolink.camera.ptp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** PTP/IP 传输层异常（连接/握手/事务失败） */
open class PtpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Init 握手被相机拒绝（Init_Fail）。
 * reason：1 = 该网络配置文件已绑定其他主机 GUID（需在相机端删除配置后重新注册）
 * 2 = 忙（上次会话未释放） / 3 = 其他。
 */
class PtpInitRejectedException(val reason: Int) :
    PtpException("相机拒绝 Init（原因 $reason：1 已绑定其他主机 / 2 忙 / 3 其他）")

/** PTP/IP 常量（CIPA DC-X005 / ISO 15740） */
object PtpIp {
    const val PORT = 15740
    const val PROTOCOL_VERSION = 0x00010000

    // 包类型
    const val TYPE_INIT_COMMAND_REQUEST = 0x01
    const val TYPE_INIT_COMMAND_ACK = 0x02
    const val TYPE_INIT_EVENT_REQUEST = 0x03
    const val TYPE_INIT_EVENT_ACK = 0x04
    const val TYPE_INIT_FAIL = 0x05
    const val TYPE_OPERATION_REQUEST = 0x06
    const val TYPE_OPERATION_RESPONSE = 0x07
    const val TYPE_EVENT = 0x08
    const val TYPE_START_DATA = 0x09
    const val TYPE_DATA = 0x0A
    const val TYPE_CANCEL = 0x0B
    const val TYPE_END_DATA = 0x0C
    const val TYPE_PROBE_REQUEST = 0x0D
    const val TYPE_PROBE_RESPONSE = 0x0E

    // dataPhase
    const val PHASE_NONE = 1
    const val PHASE_DATA_OUT = 2
    const val PHASE_DATA_IN = 3
}

/** PTP 操作码（标准 + 尼康扩展中本项目用到的部分） */
object PtpOp {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val GET_STORAGE_IDS = 0x1004
    const val GET_STORAGE_INFO = 0x1005
    const val GET_OBJECT_HANDLES = 0x1007
    const val GET_OBJECT_INFO = 0x1008
    const val GET_THUMB = 0x100A
    const val DELETE_OBJECT = 0x100B
    const val GET_PARTIAL_OBJECT = 0x101B
    const val GET_DEVICE_PROP_DESC = 0x1014
    const val GET_DEVICE_PROP_VALUE = 0x1015
    const val SET_DEVICE_PROP_VALUE = 0x1016
    const val INITIATE_CAPTURE = 0x100E
    const val TERMINATE_OPEN_CAPTURE = 0x1010   // 0x100F OpenCapture 路径的收门兜底（参数 0）

    // 尼康扩展
    const val NIKON_GET_VENDOR_PROP_CODES = 0x90CA
    const val NIKON_DEVICE_READY = 0x90C8
    const val NIKON_CHANGE_CAMERA_MODE = 0x90C2
    const val NIKON_GET_VENDOR_STORAGE_IDS = 0x9209
    const val NIKON_GET_THUMB_EX = 0x9424
    const val NIKON_START_LIVE_VIEW = 0x9201
    const val NIKON_END_LIVE_VIEW = 0x9202
    const val NIKON_GET_LIVE_VIEW_IMAGE = 0x9203
    const val NIKON_CHANGE_APPLICATION_MODE = 0x9435
    const val NIKON_GET_VENDOR_CODES = 0x9439
    const val NIKON_INITIATE_CAPTURE_REC_IN_MEDIA = 0x9207
    const val NIKON_GET_LIVE_VIEW_IMAGE_EX = 0x9428
    const val NIKON_GET_VENDOR_STORAGE_IDS_EX = 0x9433
    const val NIKON_GET_PARTIAL_OBJECT_EX = 0x9431
    const val NIKON_GET_OBJECT_SIZE = 0x9421
    // P2 拍摄增强（DeviceInfo 声明支持，Z30 实测命令可用；属性仍只读）
    const val NIKON_TERMINATE_CAPTURE = 0x920C   // B 门收门（P3 长曝光用）
    const val NIKON_CHANGE_AF_AREA = 0x9205      // 触屏点对焦（P3 用）
    const val NIKON_AF_DRIVE = 0x90C1            // 触发 AF（P3 用）
    const val NIKON_START_MOVIE_REC = 0x920A     // 开始录像（写卡）
    const val NIKON_END_MOVIE_REC = 0x920B       // 结束录像
    const val NIKON_GET_PAIRING_INFO = 0x952B
    const val NIKON_CONFIRM_PAIRING = 0x935A

    /** 进入/退出智能设备配对模式（参数 9/13）。配对态下 0x9439 承担此语义 */
    const val NIKON_PAIRING_MODE = 0x9439
    const val PAIRING_MODE_ENTER = 9
    const val PAIRING_MODE_LEAVE = 13

    /** 0x935A 的参数：0x2001 接受配对 / 0x2002 拒绝 */
    const val HOST_REGISTRATION_CONFIRM_OK = 0x2001
}

/** PTP 响应码（对照 libgphoto2 ptp.h 实表校正） */
object PtpResp {
    const val OK = 0x2001
    const val GENERAL_ERROR = 0x2002
    const val SESSION_NOT_OPEN = 0x2003
    const val INVALID_TRANSACTION_ID = 0x2004
    const val OPERATION_NOT_SUPPORTED = 0x2005
    const val PARAMETER_NOT_SUPPORTED = 0x2006
    const val INCOMPLETE_TRANSFER = 0x2007
    const val INVALID_STORAGE_ID = 0x2008
    const val INVALID_OBJECT_HANDLE = 0x2009
    const val DEVICE_PROP_NOT_SUPPORTED = 0x200A
    const val INVALID_OBJECT_FORMAT_CODE = 0x200B
    const val STORE_FULL = 0x200C
    const val OBJECT_WRITE_PROTECTED = 0x200D
    const val STORE_READ_ONLY = 0x200E
    const val ACCESS_DENIED = 0x200F
    const val NO_THUMBNAIL_PRESENT = 0x2010
    const val SELF_TEST_FAILED = 0x2011
    const val PARTIAL_DELETION = 0x2012
    const val STORE_NOT_AVAILABLE = 0x2013
    const val NO_VALID_OBJECT_INFO = 0x2015
    const val CAPTURE_ALREADY_TERMINATED = 0x2018
    const val DEVICE_BUSY = 0x2019
    const val INVALID_DEVICE_PROP_VALUE = 0x201C
    const val INVALID_PARAMETER = 0x201D
    const val SESSION_ALREADY_OPEN = 0x201E
    const val TRANSACTION_CANCELED = 0x201F

    // 尼康厂商响应码（协议事实，非标准段）
    const val NIKON_INVALID_STATUS = 0xA004       // 机身当前状态不允许（如模式拨盘不在所需档位）
    const val NIKON_NOT_LIVE_VIEW = 0xA00B        // 该操作要求实时取景已开启
    const val NIKON_BULB_RELEASE_BUSY = 0xA200    // B 门收门时正在写卡，稍后重试

    fun name(code: Int): String = when (code) {
        OK -> "OK"
        GENERAL_ERROR -> "GeneralError"
        SESSION_NOT_OPEN -> "SessionNotOpen"
        INVALID_TRANSACTION_ID -> "InvalidTransactionId"
        OPERATION_NOT_SUPPORTED -> "OperationNotSupported"
        PARAMETER_NOT_SUPPORTED -> "ParameterNotSupported"
        INCOMPLETE_TRANSFER -> "IncompleteTransfer"
        INVALID_STORAGE_ID -> "InvalidStorageId"
        INVALID_OBJECT_HANDLE -> "InvalidObjectHandle"
        DEVICE_PROP_NOT_SUPPORTED -> "DevicePropNotSupported"
        INVALID_OBJECT_FORMAT_CODE -> "InvalidObjectFormatCode"
        STORE_FULL -> "StoreFull"
        OBJECT_WRITE_PROTECTED -> "ObjectWriteProtected"
        STORE_READ_ONLY -> "StoreReadOnly"
        ACCESS_DENIED -> "AccessDenied"
        NO_THUMBNAIL_PRESENT -> "NoThumbnailPresent"
        STORE_NOT_AVAILABLE -> "StoreNotAvailable"
        DEVICE_BUSY -> "DeviceBusy"
        INVALID_DEVICE_PROP_VALUE -> "InvalidDevicePropValue"
        INVALID_PARAMETER -> "InvalidParameter"
        SESSION_ALREADY_OPEN -> "SessionAlreadyOpen"
        TRANSACTION_CANCELED -> "TransactionCanceled"
        NIKON_INVALID_STATUS -> "NikonInvalidStatus(机身状态不允许)"
        NIKON_NOT_LIVE_VIEW -> "NikonNotLiveView(需先开监看)"
        NIKON_BULB_RELEASE_BUSY -> "NikonBulbBusy(写卡中)"
        else -> "0x" + code.toString(16).uppercase().padStart(4, '0')
    }
}

/** PTP 事件码 */
object PtpEvent {
    const val OBJECT_ADDED = 0x4002
    const val DEVICE_PROP_CHANGED = 0x4006
    const val CAPTURE_COMPLETE = 0x400D
}

/** PTP 设备属性（标准 0x50xx + 尼康私有 0xD1xx） */
object PtpProp {
    const val BATTERY_LEVEL = 0x5001
    // 拍摄参数（P0 参数条只读显示）
    const val WHITE_BALANCE = 0x5005
    const val APERTURE = 0x5007          // FNumber，值/100 = f 值
    const val EXPOSURE_TIME = 0x500D     // 快门，1/10000 秒；0xFFFFFFFF=B门
    const val EXPOSURE_PROGRAM = 0x500E  // 1=M 2=P 3=A 4=S
    const val ISO = 0x500F               // 0xFFFF=Auto
    const val EXPOSURE_BIAS = 0x5010     // 曝光补偿
    const val NIKON_SHUTTER = 0xD100     // 尼康快门（分子/分母打包，优先于 0x500D）
    // 尼康水平仪候选属性（Z30 USB dump 证实存在，只读；PTP/IP 是否可读待探测）
    const val NIKON_ANGLE_LEVEL = 0xD067
    const val NIKON_ANGLE_PITCHING = 0xD07D
    const val NIKON_ANGLE_YAWING = 0xD07E
    // 尼康取景
    const val NIKON_LIVE_VIEW_IMAGE_SIZE = 0xD1AC
    const val NIKON_LIVE_VIEW_SELECTOR = 0xD1A6
    const val NIKON_LIVE_VIEW_PROHIBIT = 0xD1A4
}

/** 一次操作事务的响应 */
data class PtpResponse(
    val code: Int,
    val transactionId: Int,
    val params: List<Int> = emptyList(),
    val data: ByteArray? = null,
) {
    val ok: Boolean get() = code == PtpResp.OK
}

/** 事件通道收到的事件 */
data class PtpEventPacket(
    val code: Int,
    val transactionId: Int,
    val params: List<Int>,
)

/** 数据阶段写入目标（下载时直接写文件，避免整张图进内存） */
fun interface DataSink {
    fun write(buffer: ByteArray, offset: Int, length: Int)
}

/** 原始 PTP/IP 包 */
internal class RawPacket(val type: Int, val payload: ByteArray)

/** 小端读写 + PTP 字符串编解码 */
object PtpCodec {

    fun u16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    fun u32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    fun readU64(bytes: ByteArray, offset: Int): Long =
        (readU32(bytes, offset).toLong() and 0xFFFFFFFFL) or
            ((readU32(bytes, offset + 4).toLong() and 0xFFFFFFFFL) shl 32)

    /** 16 字节 GUID（不存在则用全 0，由调用方持久化） */
    fun guidFromHex(hex: String): ByteArray {
        val out = ByteArray(16)
        val clean = hex.trim().replace("-", "")
        var i = 0
        while (i < 16 && i * 2 + 1 < clean.length) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            i++
        }
        return out
    }

    fun guidToHex(guid: ByteArray): String =
        guid.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    /** Init_Command_Request：GUID + UTF-16LE 友好名(NUL 结尾) + protocolVersion */
    fun initCommandRequest(guid: ByteArray, friendlyName: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(guid)
        out.write(utf16le(friendlyName))
        out.write(byteArrayOf(0, 0))
        out.write(u32(PtpIp.PROTOCOL_VERSION))
        return out.toByteArray()
    }

    fun operationRequest(dataPhase: Int, opcode: Int, transactionId: Int, params: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(dataPhase))
        out.write(u16(opcode))
        out.write(u32(transactionId))
        params.forEach { out.write(u32(it)) }
        return out.toByteArray()
    }

    fun startData(transactionId: Int, totalLength: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(transactionId))
        out.write(u32((totalLength and 0xFFFFFFFFL).toInt()))
        out.write(u32((totalLength ushr 32).toInt()))
        return out.toByteArray()
    }

    /** PTP 标准字符串：1 字节长度（含 NUL）+ UTF-16LE */
    fun readPtpString(bytes: ByteArray, offset: Int): Pair<String, Int> {
        if (offset >= bytes.size) return "" to offset
        val count = bytes[offset].toInt() and 0xFF
        val start = offset + 1
        val end = (start + count * 2).coerceAtMost(bytes.size)
        val sb = StringBuilder()
        var i = start
        while (i + 1 < end) {
            val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            if (code == 0) break
            sb.append(code.toChar())
        }
        return sb.toString() to (start + count * 2)
    }

    /** UTF-16LE 且以 0x0000 结尾（Init 包里的相机名） */
    fun readUtf16z(bytes: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        var i = offset
        while (i + 1 < bytes.size) {
            val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            if (code == 0) break
            sb.append(code.toChar())
        }
        return sb.toString()
    }

    fun utf16le(text: String): ByteArray {
        val out = ByteArray(text.length * 2)
        text.forEachIndexed { index, c ->
            out[index * 2] = (c.code and 0xFF).toByte()
            out[index * 2 + 1] = ((c.code shr 8) and 0xFF).toByte()
        }
        return out
    }
}

/** 一个 socket 上的 PTP/IP 分包读写 */
internal class PacketIo(private val input: InputStream, private val output: OutputStream) {

    fun send(type: Int, payload: ByteArray = EMPTY) {
        val length = 8 + payload.size
        val header = ByteArray(8)
        header[0] = (length and 0xFF).toByte()
        header[1] = ((length shr 8) and 0xFF).toByte()
        header[2] = ((length shr 16) and 0xFF).toByte()
        header[3] = ((length shr 24) and 0xFF).toByte()
        header[4] = (type and 0xFF).toByte()
        header[5] = ((type shr 8) and 0xFF).toByte()
        header[6] = ((type shr 16) and 0xFF).toByte()
        header[7] = ((type shr 24) and 0xFF).toByte()
        output.write(header)
        if (payload.isNotEmpty()) output.write(payload)
        output.flush()
    }

    fun receive(): RawPacket {
        val header = readFully(8)
        val length = PtpCodec.readU32(header, 0)
        val type = PtpCodec.readU32(header, 4)
        if (length < 8) throw PtpException("非法 PTP/IP 包长度 $length")
        val body = if (length > 8) readFully(length - 8) else EMPTY
        return RawPacket(type, body)
    }

    private fun readFully(size: Int): ByteArray {
        val out = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(out, read, size - read)
            if (n < 0) throw PtpException("连接被相机关闭")
            read += n
        }
        return out
    }

    companion object {
        val EMPTY = ByteArray(0)
    }
}

/** 读取 PTP 数据区的游标（GetDeviceInfo / GetObjectInfo 等） */
internal class PtpReader(private val bytes: ByteArray) {
    var offset: Int = 0
        private set

    fun u8(): Int = bytes[offset++].toInt() and 0xFF

    fun u16(): Int {
        val v = PtpCodec.readU16(bytes, offset)
        offset += 2
        return v
    }

    fun u32(): Int {
        val v = PtpCodec.readU32(bytes, offset)
        offset += 4
        return v
    }

    fun string(): String {
        val (text, next) = PtpCodec.readPtpString(bytes, offset)
        offset = next
        return text
    }

    fun u16Array(): List<Int> {
        val count = u32()
        val out = ArrayList<Int>(count.coerceIn(0, 4096))
        repeat(count) { out.add(u16()) }
        return out
    }

    fun u32Array(): List<Int> {
        val count = u32()
        val out = ArrayList<Int>(count.coerceIn(0, 65536))
        repeat(count) { out.add(u32()) }
        return out
    }
}
