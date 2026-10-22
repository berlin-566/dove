package com.dove.app.fountain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32

const val MAGIC_BYTE_1: Byte = 0x44 // 'D'
const val MAGIC_BYTE_2: Byte = 0x56 // 'V'
const val HEADER_SIZE = 20

data class Symbol(
    val sessionId: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val seed: Long,
    val data: ByteArray
) {
    fun serialize(): ByteArray {
        val totalSize = HEADER_SIZE + data.size + 4
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE_1)
        buf.put(MAGIC_BYTE_2)
        buf.putLong(sessionId)
        buf.putShort(totalChunks.toShort())
        buf.putShort(chunkSize.toShort())
        buf.putInt(seed.toInt())
        buf.putShort(data.size.toShort())
        buf.put(data)

        val crc = CRC32()
        crc.update(buf.array(), 0, HEADER_SIZE + data.size)
        buf.putInt(crc.value.toInt())

        return buf.array()
    }

    companion object {
        fun deserialize(raw: ByteArray): Symbol? {
            if (raw.size < HEADER_SIZE + 4) return null
            if (raw[0] != MAGIC_BYTE_1 || raw[1] != MAGIC_BYTE_2) return null

            val dataLen = raw.size - 4
            val crc = CRC32()
            crc.update(raw, 0, dataLen)
            val expectedCrc = ByteBuffer.wrap(raw, dataLen, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (crc.value != expectedCrc) return null

            val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
            buf.position(2)
            val sessionId = buf.long
            val totalChunks = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.short.toInt() and 0xFFFF
            val seed = buf.int.toLong() and 0xFFFFFFFFL
            val payloadLen = buf.short.toInt() and 0xFFFF

            if (HEADER_SIZE + payloadLen > dataLen) return null

            val data = ByteArray(payloadLen)
            buf.get(data)

            return Symbol(sessionId, totalChunks, chunkSize, seed, data)
        }
    }
}

class FountainDecoder(
    val sessionId: Long,
    val totalChunks: Int,
    val chunkSize: Int
) {
    val resolvedChunks = mutableMapOf<Int, ByteArray>()
    private val pendingSymbols = mutableListOf<SymbolEntry>()

    private class SymbolEntry(val indices: MutableSet<Int>, val data: ByteArray)

    fun addSymbol(symbol: Symbol): Boolean {
        if (symbol.sessionId != sessionId) return false

        val rawIndices = pickIndices(symbol.seed, totalChunks)
        val entryData = symbol.data.copyOf()
        val unresolvedIndices = mutableSetOf<Int>()

        for (idx in rawIndices) {
            val resolved = resolvedChunks[idx]
            if (resolved != null) {
                xorBytes(entryData, resolved)
            } else {
                unresolvedIndices.add(idx)
            }
        }

        if (unresolvedIndices.isEmpty()) {
            return isComplete()
        }

        pendingSymbols.add(SymbolEntry(unresolvedIndices, entryData))
        peel()

        return isComplete()
    }

    private fun peel() {
        var progress = true
        while (progress) {
            progress = false
            val it = pendingSymbols.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.indices.size == 1) {
                    val chunkIdx = entry.indices.first()
                    val resolved = entry.data.copyOf()
                    resolvedChunks[chunkIdx] = resolved
                    it.remove()
                    progress = true

                    for (other in pendingSymbols) {
                        if (other.indices.contains(chunkIdx)) {
                            xorBytes(other.data, resolved)
                            other.indices.remove(chunkIdx)
                        }
                    }
                    break
                }
            }
        }
    }

    fun isComplete(): Boolean = resolvedChunks.size >= totalChunks

    fun progressPercentage(): Int {
        if (totalChunks == 0) return 100
        return ((resolvedChunks.size.toFloat() / totalChunks) * 100).toInt().coerceIn(0, 100)
    }

    fun reconstruct(): ByteArray {
        val out = ByteBuffer.allocate(totalChunks * chunkSize)
        for (i in 0 until totalChunks) {
            val chunk = resolvedChunks[i] ?: ByteArray(chunkSize)
            out.put(chunk)
        }
        return out.array()
    }
}

private fun pickIndices(seed: Long, k: Int): List<Int> {
    if (k <= 1) return listOf(0)
    val rand = Random(seed)

    var degree = 1
    val p = rand.nextDouble()
    if (p > (1.0 / k)) {
        var d = (k * (1.0 - p)).toInt()
        if (d < 2) d = 2
        if (d > k) d = k
        degree = d
    }

    val indices = (0 until k).toMutableList()
    val result = mutableListOf<Int>()
    for (i in 0 until degree) {
        val pickIndex = rand.nextInt(indices.size)
        result.add(indices.removeAt(pickIndex))
    }
    return result
}

private fun xorBytes(dst: ByteArray, src: ByteArray) {
    val len = minOf(dst.size, src.size)
    for (i in 0 until len) {
        dst[i] = (dst[i].toInt() xor src[i].toInt()).toByte()
    }
}
