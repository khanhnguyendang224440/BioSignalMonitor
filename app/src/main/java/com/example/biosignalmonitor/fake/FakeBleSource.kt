/**
 * @file FakeBleSource.kt
 * @brief Tạo dữ liệu BLE giả lập để kiểm thử ứng dụng BioSignalMonitor.
 *
 * File này mô phỏng các packet Bio và Audio theo đúng định dạng dữ liệu
 * dự kiến nhận từ thiết bị ESP32:
 *
 * Header | Version | Type | Sequence | Payload Length | Data | CRC | Footer
 *
 * Dữ liệu giả lập được đưa vào cùng luồng phân tích và xử lý với dữ liệu BLE thật.
 * Nhờ đó, ứng dụng có thể kiểm thử chức năng nhận packet, kiểm tra CRC,
 * phát hiện mất packet và hiển thị tín hiệu trước khi kết nối phần cứng STM32.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.fake

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object FakeBleSource {
    private const val HEADER: Byte = 0xAA.toByte()
    private const val VERSION: Byte = 0x01
    private const val TYPE_AUDIO: Byte = 0x01
    private const val TYPE_BIO: Byte = 0x02
    private const val FOOTER: Byte = 0x55

    private const val SAMPLE_COUNT = 32

    private const val HEADER_SIZE = 6
    private const val FOOTER_SIZE = 3

    private const val AUDIO_PAYLOAD_SIZE = SAMPLE_COUNT * 4
    private const val BIO_PAYLOAD_SIZE = SAMPLE_COUNT * 4 + SAMPLE_COUNT * 2

    const val AUDIO_PACKET_SIZE = HEADER_SIZE + AUDIO_PAYLOAD_SIZE + FOOTER_SIZE
    const val BIO_PACKET_SIZE = HEADER_SIZE + BIO_PAYLOAD_SIZE + FOOTER_SIZE

    fun makeAudioPacket(sequence: Int): ByteArray {
        val buffer = ByteBuffer.allocate(AUDIO_PACKET_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)

        putHeader(
            buffer = buffer,
            type = TYPE_AUDIO,
            sequence = sequence,
            payloadLength = AUDIO_PAYLOAD_SIZE
        )

        // PCG[32] - int32_t
        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 8f
            val value = (
                    sin(t) * 700f +
                            sin(t * 8f) * 250f
                    ).toInt()

            buffer.putInt(value)
        }

        putFooter(buffer)

        return buffer.array()
    }

    fun makeBioPacket(sequence: Int): ByteArray {
        val buffer = ByteBuffer.allocate(BIO_PACKET_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)

        putHeader(
            buffer = buffer,
            type = TYPE_BIO,
            sequence = sequence,
            payloadLength = BIO_PAYLOAD_SIZE
        )

        // PPG IR[32] - uint32_t
        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 45f
            val value = (
                    sin(t) * 900f +
                            1200f
                    ).toInt()

            buffer.putInt(value)
        }

        // ECG[32] - int16_t
        for (i in 0 until SAMPLE_COUNT) {
            val t = (sequence * SAMPLE_COUNT + i) / 20f
            val value = (
                    sin(t) * 1000f +
                            sin(t * 4f) * 200f
                    ).toInt()

            buffer.putShort(value.toShort())
        }

        putFooter(buffer)

        return buffer.array()
    }

    private fun putHeader(
        buffer: ByteBuffer,
        type: Byte,
        sequence: Int,
        payloadLength: Int
    ) {
        buffer.put(HEADER)
        buffer.put(VERSION)
        buffer.put(type)
        buffer.put((sequence and 0xFF).toByte())
        buffer.putShort(payloadLength.toShort())
    }

    private fun putFooter(buffer: ByteBuffer) {
        // Tạm thời CRC = 0x0000.
        // Sang bước sau mới tính CRC16-CCITT thật.
        buffer.putShort(0)
        buffer.put(FOOTER)
    }
}