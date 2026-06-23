package com.adblocker

import java.nio.ByteBuffer

class DnsPacket(private val buffer: ByteBuffer) {
    private val transactionId: Int = buffer.getShort(0).toInt() and 0xFFFF
    val questions: MutableList<String> = mutableListOf()
    val isResponse: Boolean
        get() = ((buffer.getShort(2).toInt() shr 15) and 1) == 1

    init {
        val qdCount = buffer.getShort(4).toInt() and 0xFFFF
        var pos = 12
        for (i in 0 until qdCount) {
            val domain = readDomain(pos)
            if (domain != null) {
                questions.add(domain)
                pos += domain.length + 2
                pos += 4
            }
        }
    }

    private fun readDomain(startPos: Int): String? {
        var pos = startPos
        val parts = mutableListOf<String>()
        while (pos < buffer.limit()) {
            val len = buffer.get(pos).toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) return null
            pos++
            if (pos + len > buffer.limit()) return null
            val label = ByteArray(len)
            buffer.position(pos)
            buffer.get(label)
            parts.add(String(label))
            pos += len
        }
        return if (parts.isEmpty()) null else parts.joinToString(".")
    }

    fun asResponse(respondWithIp: String = "0.0.0.0"): ByteBuffer {
        val ipBytes = try {
            java.net.InetAddress.getByName(respondWithIp).address
        } catch (e: Exception) {
            byteArrayOf(0, 0, 0, 0)
        }

        val resp = ByteBuffer.allocate(512)
        resp.putShort(transactionId.toShort())
        resp.putShort(0x8580.toShort())
        resp.putShort(questions.size.toShort())
        resp.putShort(questions.size.toShort())
        resp.putShort(0)
        resp.putShort(0)

        for (q in questions) {
            for (part in q.split(".")) {
                resp.put(part.length.toByte())
                resp.put(part.toByteArray())
            }
            resp.put(0)
            resp.putShort(1)
            resp.putShort(1)
        }

        for (q in questions) {
            for (part in q.split(".")) {
                resp.put(part.length.toByte())
                resp.put(part.toByteArray())
            }
            resp.put(0)
            resp.putShort(1)
            resp.putShort(1)
            resp.putInt(60)
            resp.putShort(4)
            resp.put(ipBytes)
        }

        resp.flip()
        return resp
    }

    fun matchesDomain(domainSet: Set<String>): Boolean {
        return questions.any { q ->
            val lower = q.lowercase()
            domainSet.any { adDomain ->
                val clean = adDomain.lowercase().removePrefix("www.")
                lower == clean || lower.endsWith(".$clean") || lower.endsWith(".$clean.")
            }
        }
    }

    fun shouldBlock(
        adBlockList: Set<String>,
        adultBlockList: Set<String>,
        safeList: Set<String>,
        blockAdult: Boolean
    ): Boolean {
        return questions.any { q ->
            val lower = q.lowercase().removePrefix("www.")

            val isSafe = safeList.any { safe ->
                val cleanSafe = safe.lowercase().removePrefix("www.")
                lower == cleanSafe || lower.endsWith(".$cleanSafe")
            }
            if (isSafe) return@any false

            if (blockAdult) {
                val isAdult = adultBlockList.any { adult ->
                    val cleanAdult = adult.lowercase().removePrefix("www.")
                    lower == cleanAdult || lower.endsWith(".$cleanAdult")
                }
                if (isAdult) return@any true
            }

            adBlockList.any { ad ->
                val cleanAd = ad.lowercase().removePrefix("www.")
                lower == cleanAd || lower.endsWith(".$cleanAd")
            }
        }
    }
}
