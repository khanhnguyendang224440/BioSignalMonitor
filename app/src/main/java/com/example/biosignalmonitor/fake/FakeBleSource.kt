package com.example.biosignalmonitor.fake

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object FakeBleSource {
    private const val MAGIC: Short = 0x55AA.toShort()
    private const val SAMPLE_COUNT = 32
    private const val CHANNEL_COUNT = 3

    fun makePacket(sequence: Int): ByteArray {
        val packetSize = 2 + 2 + 4 + 1 + CHANNEL_COUNT * SAMPLE_COUNT * 2

        val buffer = ByteBuffer.allocate(packetSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        val timestamp = sequence * 32

        buffer.putShort(MAGIC)
        buffer.putShort(sequence.toShort())
        buffer.putInt(timestamp)
        buffer.put(SAMPLE_COUNT.toByte())

        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 20f
            val value = (sin(t) * 1000f + sin(t * 4f) * 200f).toInt()
            buffer.putShort(value.toShort())
        }

        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 45f
            val value = (sin(t) * 900f + 1200f).toInt()
            buffer.putShort(value.toShort())
        }

        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 8f
            val value = (sin(t) * 700f + sin(t * 8f) * 250f).toInt()
            buffer.putShort(value.toShort())
        }

        return buffer.array()
    }
}