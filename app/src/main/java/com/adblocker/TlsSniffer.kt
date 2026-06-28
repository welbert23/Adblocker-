package com.adblocker

object TlsSniffer {

    fun extractSni(data: ByteArray): String? {
        if (data.size < 50) return null
        if (data[0].toInt() and 0xFF != 0x16) return null
        val handshakeType = data[5].toInt() and 0xFF
        if (handshakeType != 0x01) return null

        var pos = 43
        if (pos >= data.size) return null
        val sessionIdLen = data[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > data.size) return null
        val cipherLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherLen
        if (pos + 1 > data.size) return null
        val compLen = data[pos].toInt() and 0xFF
        pos += 1 + compLen
        if (pos + 2 > data.size) return null
        val extLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        val extEnd = pos + extLen
        if (extEnd > data.size) return null

        while (pos + 4 <= extEnd) {
            val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            val extDataLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + extDataLen > extEnd) return null
            if (extType == 0x0000) {
                if (pos + 2 > data.size) return null
                val listLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                if (pos + 1 > data.size) return null
                val nameType = data[pos].toInt() and 0xFF
                pos += 1
                if (nameType != 0x00) return null
                if (pos + 2 > data.size) return null
                val nameLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                if (pos + nameLen > data.size) return null
                return String(data, pos, nameLen)
            }
            pos += extDataLen
        }
        return null
    }
}
