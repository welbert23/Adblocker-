package com.adblocker

import java.net.InetAddress
import java.nio.ByteBuffer

object IpUtil {

    const val IPV6_HEADER_LEN = 40

    fun ipVersion(pkt: ByteArray): Int = (pkt[0].toInt() shr 4) and 0x0F

    fun ipHeaderLen(pkt: ByteArray): Int = (pkt[0].toInt() and 0x0F) * 4

    fun totalLen(pkt: ByteArray): Int = ((pkt[2].toInt() and 0xFF) shl 8) or (pkt[3].toInt() and 0xFF)

    fun protocol(pkt: ByteArray): Int = pkt[9].toInt() and 0xFF

    fun srcIp(pkt: ByteArray): ByteArray {
        val ip = ByteArray(4)
        System.arraycopy(pkt, 12, ip, 0, 4)
        return ip
    }

    fun dstIp(pkt: ByteArray): ByteArray {
        val ip = ByteArray(4)
        System.arraycopy(pkt, 16, ip, 0, 4)
        return ip
    }

    fun ip6Protocol(pkt: ByteArray): Int = pkt[6].toInt() and 0xFF

    fun ip6PayloadLength(pkt: ByteArray): Int =
        ((pkt[4].toInt() and 0xFF) shl 8) or (pkt[5].toInt() and 0xFF)

    fun ip6SrcIp(pkt: ByteArray): ByteArray {
        val ip = ByteArray(16)
        System.arraycopy(pkt, 8, ip, 0, 16)
        return ip
    }

    fun ip6DstIp(pkt: ByteArray): ByteArray {
        val ip = ByteArray(16)
        System.arraycopy(pkt, 24, ip, 0, 16)
        return ip
    }

    fun srcPort(pkt: ByteArray, ihl: Int): Int =
        ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)

    fun dstPort(pkt: ByteArray, ihl: Int): Int =
        ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)

    fun tcpFlags(pkt: ByteArray, ihl: Int): Int {
        val tcpHeaderLen = ((pkt[ihl + 12].toInt() and 0xF0) shr 2)
        val flagsOffset = ihl + 13
        return pkt[flagsOffset].toInt() and 0x3F
    }

    const val TCP_SYN = 0x02
    const val TCP_SYN_ACK = 0x12
    const val TCP_ACK = 0x10
    const val TCP_PSH_ACK = 0x18
    const val TCP_FIN_ACK = 0x11
    const val TCP_RST = 0x04
    const val TCP_RST_ACK = 0x14

    fun tcpSeq(pkt: ByteArray, ihl: Int): Long {
        val b = ByteBuffer.wrap(pkt, ihl + 4, 4)
        return b.getInt().toLong() and 0xFFFFFFFFL
    }

    fun tcpAck(pkt: ByteArray, ihl: Int): Long {
        val b = ByteBuffer.wrap(pkt, ihl + 8, 4)
        return b.getInt().toLong() and 0xFFFFFFFFL
    }

    fun tcpDataLen(pkt: ByteArray, ihl: Int): Int {
        val tcpHeaderLen = ((pkt[ihl + 12].toInt() and 0xF0) shr 2)
        return pkt.size - ihl - tcpHeaderLen
    }

    fun tcpDataOffset(pkt: ByteArray, ihl: Int): Int {
        val tcpHeaderLen = ((pkt[ihl + 12].toInt() and 0xF0) shr 2)
        return ihl + tcpHeaderLen
    }

    data class TcpKey(val srcIp: ByteArray, val srcPort: Int, val dstIp: ByteArray, val dstPort: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TcpKey) return false
            return srcIp.contentEquals(other.srcIp) && srcPort == other.srcPort &&
                    dstIp.contentEquals(other.dstIp) && dstPort == other.dstPort
        }
        override fun hashCode(): Int {
            return srcIp.contentHashCode() * 31 + srcPort + dstIp.contentHashCode() * 31 + dstPort
        }
    }

    class TcpState {
        var clientSeq = 0L
        var serverSeq = 0L
        var clientAck = 0L
        var serverAck = 0L
        var synReceived = false
        var synAckSent = false
        var established = false
        var closed = false
        var blocked = false
        var sniChecked = false
        var sniDomain: String? = null
        var remoteSocket: java.net.Socket? = null
        var remoteOut: java.io.OutputStream? = null
        var remoteIn: java.io.InputStream? = null
        var relayThread: Thread? = null
        var pendingData: ByteArray? = null

        fun key(): TcpKey = TcpKey(dstIp, dstPort, srcIp, srcPort)

        var srcIp: ByteArray = ByteArray(4)
        var srcPort: Int = 0
        var dstIp: ByteArray = ByteArray(4)
        var dstPort: Int = 0
    }

    private fun tcpPseudoChecksum(srcIp: ByteArray, dstIp: ByteArray, protocol: Int, tcpSegment: ByteArray): Int {
        val isV6 = srcIp.size == 16
        val segLen = tcpSegment.size
        val pseudoLen = if (isV6) 40 + segLen else 12 + segLen
        val pseudo = ByteBuffer.allocate(pseudoLen)
        if (isV6) {
            pseudo.put(srcIp)
            pseudo.put(dstIp)
            pseudo.putInt(segLen)
            pseudo.put(0x00.toByte())
            pseudo.put(0x00.toByte())
            pseudo.put(0x00.toByte())
            pseudo.put(protocol.toByte())
        } else {
            pseudo.put(dstIp)
            pseudo.put(srcIp)
            pseudo.put(0x00.toByte())
            pseudo.put(protocol.toByte())
            pseudo.putShort(segLen.toShort())
        }
        pseudo.put(tcpSegment)
        return tcpChecksum(pseudo.array(), pseudoLen)
    }

    fun createIp6Header(srcIp: ByteArray, dstIp: ByteArray, protocol: Int, payloadLen: Int): ByteArray {
        val hdr = ByteBuffer.allocate(40)
        hdr.put(0x60.toByte())
        hdr.put(0x00.toByte())
        hdr.put(0x00.toByte())
        hdr.put(0x00.toByte())
        hdr.putShort(payloadLen.toShort())
        hdr.put(protocol.toByte())
        hdr.put(64.toByte())
        hdr.put(srcIp)
        hdr.put(dstIp)
        return hdr.array()
    }

    fun createIpHeader(srcIp: ByteArray, dstIp: ByteArray, protocol: Int, afterIpLen: Int): ByteArray {
        if (srcIp.size == 16) {
            return createIp6Header(srcIp, dstIp, protocol, afterIpLen)
        }
        val totalLen = 20 + afterIpLen
        val hdr = ByteBuffer.allocate(20)
        hdr.put(0x45.toByte())
        hdr.put(0x00.toByte())
        hdr.putShort(totalLen.toShort())
        hdr.putInt(0)
        hdr.put(64.toByte())
        hdr.put(protocol.toByte())
        hdr.putShort(0)
        hdr.put(srcIp)
        hdr.put(dstIp)
        val sum = ipChecksum(hdr.array(), 20)
        hdr.putShort(10, sum.toShort())
        return hdr.array()
    }

    fun createTcpSynAck(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        seq: Long, ack: Long,
        mss: Int = 1460
    ): ByteArray {
        val tcpLen = 24
        val tcp = ByteBuffer.allocate(tcpLen)
        tcp.putShort(srcPort.toShort())
        tcp.putShort(dstPort.toShort())
        tcp.putInt((seq and 0xFFFFFFFFL).toInt())
        tcp.putInt((ack and 0xFFFFFFFFL).toInt())
        tcp.put((0x60).toByte())
        tcp.put(0x12.toByte())
        tcp.putShort(65535.toShort())
        tcp.putShort(0)
        tcp.putShort(0)
        tcp.put(0x02.toByte())
        tcp.put(0x04.toByte())
        tcp.putShort(mss.toShort())

        val check = tcpPseudoChecksum(srcIp, dstIp, 6, tcp.array())
        tcp.putShort(16, check.toShort())

        val ip = createIpHeader(srcIp, dstIp, 6, tcpLen)
        return ip + tcp.array()
    }

    fun createTcpRst(srcIp: ByteArray, dstIp: ByteArray,
                      srcPort: Int, dstPort: Int,
                      seq: Long, ack: Long): ByteArray {
        val tcpLen = 20
        val tcp = ByteBuffer.allocate(tcpLen)
        tcp.putShort(srcPort.toShort())
        tcp.putShort(dstPort.toShort())
        tcp.putInt((seq and 0xFFFFFFFFL).toInt())
        tcp.putInt((ack and 0xFFFFFFFFL).toInt())
        tcp.put(0x50.toByte())
        tcp.put(0x14.toByte())
        tcp.putShort(0.toShort())
        tcp.putShort(0)
        tcp.putShort(0)

        val check = tcpPseudoChecksum(srcIp, dstIp, 6, tcp.array())
        tcp.putShort(16, check.toShort())

        val ip = createIpHeader(srcIp, dstIp, 6, tcpLen)
        return ip + tcp.array()
    }

    fun createTcpDataPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        seq: Long, ack: Long,
        data: ByteArray, fin: Boolean = false
    ): ByteArray {
        if (data.isEmpty()) return createTcpAck(srcIp, dstIp, srcPort, dstPort, seq, ack)
        val tcpLen = 20
        val flags = if (fin) 0x19 else 0x18
        val tcpBuf = ByteBuffer.allocate(tcpLen + data.size)
        tcpBuf.putShort(srcPort.toShort())
        tcpBuf.putShort(dstPort.toShort())
        tcpBuf.putInt((seq and 0xFFFFFFFFL).toInt())
        tcpBuf.putInt((ack and 0xFFFFFFFFL).toInt())
        tcpBuf.put(0x50.toByte())
        tcpBuf.put(flags.toByte())
        tcpBuf.putShort(65535.toShort())
        tcpBuf.putShort(0)
        tcpBuf.putShort(0)
        tcpBuf.put(data)

        val tcpSegment = tcpBuf.array()
        val check = tcpPseudoChecksum(srcIp, dstIp, 6, tcpSegment)
        tcpSegment[16] = (check shr 8).toByte()
        tcpSegment[17] = (check and 0xFF).toByte()

        val ip = createIpHeader(srcIp, dstIp, 6, tcpLen + data.size)
        return ip + tcpSegment
    }

    fun createTcpAck(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        seq: Long, ack: Long
    ): ByteArray {
        val tcpLen = 20
        val tcp = ByteBuffer.allocate(tcpLen)
        tcp.putShort(srcPort.toShort())
        tcp.putShort(dstPort.toShort())
        tcp.putInt((seq and 0xFFFFFFFFL).toInt())
        tcp.putInt((ack and 0xFFFFFFFFL).toInt())
        tcp.put(0x50.toByte())
        tcp.put(0x10.toByte())
        tcp.putShort(65535.toShort())
        tcp.putShort(0)
        tcp.putShort(0)

        val check = tcpPseudoChecksum(srcIp, dstIp, 6, tcp.array())
        tcp.putShort(16, check.toShort())

        val ip = createIpHeader(srcIp, dstIp, 6, tcpLen)
        return ip + tcp.array()
    }

    fun ipChecksum(data: ByteArray, len: Int): Int {
        var sum = 0L
        var i = 0
        while (i < len) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.toInt() xor 0xFFFF) and 0xFFFF
    }

    fun tcpChecksum(data: ByteArray, len: Int): Int {
        var sum = 0L
        var i = 0
        val paddedLen = if (len % 2 == 0) len else len + 1
        while (i < paddedLen) {
            val b1 = if (i < len) data[i].toInt() and 0xFF else 0
            val b2 = if (i + 1 < len) data[i + 1].toInt() and 0xFF else 0
            sum += ((b1 shl 8) or b2)
            i += 2
        }
        while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.toInt() xor 0xFFFF) and 0xFFFF
    }
}
