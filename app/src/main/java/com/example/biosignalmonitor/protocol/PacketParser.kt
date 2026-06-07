package com.example.biosignalmonitor.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketParser {
    private const val MAGIC: Short = 0x55AA.toShort()
    private const val CHANNEL_COUNT = 3

    fun parse(bytes: ByteArray): BioPacket? {
        if (bytes.size < 12) return null

        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.short
        if (magic != MAGIC) return null

        val sequence = buffer.short.toInt() and 0xFFFF
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val count = buffer.get().toInt() and 0xFF

        val expectedSize = 2 + 2 + 4 + 1 + CHANNEL_COUNT * count * 2

        if (bytes.size < expectedSize) return null

        val ecg = ShortArray(count)
        val ppg = ShortArray(count)
        val pcg = ShortArray(count)

        for (i in 0 until count) {
            ecg[i] = buffer.short
        }

        for (i in 0 until count) {
            ppg[i] = buffer.short
        }

        for (i in 0 until count) {
            pcg[i] = buffer.short
        }

        return BioPacket(
            sequence = sequence,
            timestamp = timestamp,
            count = count,
            ecg = ecg,
            ppg = ppg,
            pcg = pcg
        )
    }
}