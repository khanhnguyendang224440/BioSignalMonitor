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
 * phát hiện mất packet, hiển thị waveform và kiểm thử HR/PTT trước khi kết nối
 * phần cứng STM32.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.fake

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
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

    private const val FAKE_RR_SAMPLES = 800
    private const val FAKE_R_PEAK_OFFSET = 40
    private const val FAKE_PTT_SAMPLES = 220

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
            val globalSample = sequence * SAMPLE_COUNT + i
            val t = globalSample / 8f
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

        // PPG IR[32] - uint32_t.
        // Tạo pulse PPG xuất hiện sau R-peak khoảng 220 ms
        // để kiểm thử thuật toán PTT trên App mà không đổi format packet.
        for (i in 0 until SAMPLE_COUNT) {
            val globalSample = sequence * SAMPLE_COUNT + i
            val phase = positiveModulo(
                globalSample - FAKE_R_PEAK_OFFSET - FAKE_PTT_SAMPLES,
                FAKE_RR_SAMPLES
            )

            val pulse = gaussian(
                x = phase.toDouble(),
                center = 0.0,
                width = 55.0
            )
            val slowWave = sin(globalSample / 180.0) * 80.0
            val value = (1200.0 + pulse * 900.0 + slowWave).toInt()

            buffer.putInt(value)
        }

        // ECG[32] - int16_t.
        // Tạo QRS giả với R-peak rõ để test HR.
        for (i in 0 until SAMPLE_COUNT) {
            val globalSample = sequence * SAMPLE_COUNT + i
            val phase = positiveModulo(
                globalSample - FAKE_R_PEAK_OFFSET,
                FAKE_RR_SAMPLES
            )

            val rPeak = gaussian(
                x = phase.toDouble(),
                center = 0.0,
                width = 8.0
            ) * 1800.0
            val qDip = gaussian(
                x = phase.toDouble(),
                center = -18.0,
                width = 8.0
            ) * -250.0
            val sDip = gaussian(
                x = phase.toDouble(),
                center = 22.0,
                width = 10.0
            ) * -320.0
            val baseline = sin(globalSample / 140.0) * 45.0

            val value = (baseline + qDip + rPeak + sDip).toInt()
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
        /*
         * CRC16-CCITT được tính trên:
         * Header + Version + Type + Sequence + PayloadLength + Payload
         *
         * Không tính chính trường CRC và Footer.
         * Cách này khớp với STM32/ESP32:
         * Init = 0xFFFF, Polynomial = 0x1021.
         */
        val crcLength = buffer.position()
        val crc = crc16Ccitt(
            data = buffer.array(),
            offset = 0,
            length = crcLength
        )

        buffer.putShort(crc.toShort())
        buffer.put(FOOTER)
    }

    private fun crc16Ccitt(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var crc = 0xFFFF

        for (index in offset until offset + length) {
            crc = crc xor ((data[index].toInt() and 0xFF) shl 8)

            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }

        return crc and 0xFFFF
    }

    private fun gaussian(
        x: Double,
        center: Double,
        width: Double
    ): Double {
        val z = (x - center) / width
        return exp(-0.5 * z * z)
    }

    private fun positiveModulo(
        value: Int,
        modulo: Int
    ): Int {
        val raw = value % modulo
        return if (raw < 0) raw + modulo else raw
    }
}