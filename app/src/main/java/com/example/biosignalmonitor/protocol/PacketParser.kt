/**
 * @file PacketParser.kt
 * @brief Giải mã binary packet nhận từ BLE thành dữ liệu Audio hoặc Bio.
 *
 * File này nhận đầu vào là ByteArray từ FakeBleSource hoặc BLE notification
 * và giải mã theo định dạng packet của hệ thống:
 *
 * Header | Version | Type | Sequence | Payload Length | Data | CRC | Footer
 *
 * Hai loại packet được hỗ trợ:
 *
 * - Audio packet, type 0x01:
 *   Chứa 32 mẫu PCG kiểu int32_t.
 *
 * - Bio packet, type 0x02:
 *   Chứa 32 mẫu PPG IR kiểu uint32_t và 32 mẫu ECG kiểu int16_t.
 *
 * PacketParser chịu trách nhiệm:
 * - Kiểm tra kích thước packet.
 * - Kiểm tra header, version, type và footer.
 * - Kiểm tra payload length.
 * - Giải mã dữ liệu theo thứ tự Little-endian.
 *
 * CRC được đọc từ packet nhưng chưa được xác thực trong giai đoạn hiện tại.
 * Việc kiểm tra CRC16-CCITT sẽ được bổ sung sau khi xác nhận chính xác
 * thuật toán CRC giữa STM32, ESP32 và ứng dụng Android.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bộ giải mã packet Audio và Bio của hệ thống BioSignalMonitor.
 */
object PacketParser {

    private const val HEADER = 0xAA
    private const val VERSION = 0x01

    private const val TYPE_AUDIO = 0x01
    private const val TYPE_BIO = 0x02

    private const val FOOTER = 0x55

    private const val SAMPLE_COUNT = 32

    private const val HEADER_SIZE = 6
    private const val CRC_SIZE = 2
    private const val FOOTER_MARKER_SIZE = 1
    private const val PACKET_END_SIZE = CRC_SIZE + FOOTER_MARKER_SIZE

    private const val AUDIO_PAYLOAD_SIZE =
        SAMPLE_COUNT * Int.SIZE_BYTES

    private const val BIO_PAYLOAD_SIZE =
        SAMPLE_COUNT * Int.SIZE_BYTES +
                SAMPLE_COUNT * Short.SIZE_BYTES

    const val AUDIO_PACKET_SIZE =
        HEADER_SIZE + AUDIO_PAYLOAD_SIZE + PACKET_END_SIZE

    const val BIO_PACKET_SIZE =
        HEADER_SIZE + BIO_PAYLOAD_SIZE + PACKET_END_SIZE

    /**
     * Giải mã một packet binary.
     *
     * @param bytes Packet nhận từ BLE notification hoặc FakeBleSource.
     *
     * @return ParsedBlePacket.Audio nếu là Audio packet hợp lệ,
     * ParsedBlePacket.Bio nếu là Bio packet hợp lệ,
     * hoặc null nếu packet không hợp lệ.
     */
    fun parse(bytes: ByteArray): ParsedBlePacket? {
        if (bytes.size < HEADER_SIZE + PACKET_END_SIZE) {
            return null
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val header = readUnsignedByte(buffer)
        if (header != HEADER) {
            return null
        }

        val version = readUnsignedByte(buffer)
        if (version != VERSION) {
            return null
        }

        val type = readUnsignedByte(buffer)
        val sequence = readUnsignedByte(buffer)
        val payloadLength = readUnsignedShort(buffer)

        val expectedPacketSize =
            HEADER_SIZE + payloadLength + PACKET_END_SIZE

        if (bytes.size != expectedPacketSize) {
            return null
        }

        val packet = when (type) {
            TYPE_AUDIO -> parseAudioPayload(
                buffer = buffer,
                sequence = sequence,
                payloadLength = payloadLength
            )

            TYPE_BIO -> parseBioPayload(
                buffer = buffer,
                sequence = sequence,
                payloadLength = payloadLength
            )

            else -> null
        } ?: return null

        /*
         * CRC được lưu theo Little-endian.
         * Hiện tại chỉ đọc để đưa con trỏ ByteBuffer đến đúng vị trí.
         * Chưa kiểm tra giá trị CRC.
         */
        readUnsignedShort(buffer)

        val footer = readUnsignedByte(buffer)
        if (footer != FOOTER) {
            return null
        }

        /*
         * Sau footer không được còn byte dư.
         */
        if (buffer.hasRemaining()) {
            return null
        }

        return packet
    }

    /**
     * Giải mã payload của Audio packet.
     */
    private fun parseAudioPayload(
        buffer: ByteBuffer,
        sequence: Int,
        payloadLength: Int
    ): ParsedBlePacket.Audio? {
        if (payloadLength != AUDIO_PAYLOAD_SIZE) {
            return null
        }

        if (buffer.remaining() < AUDIO_PAYLOAD_SIZE + PACKET_END_SIZE) {
            return null
        }

        val pcg = IntArray(SAMPLE_COUNT)

        for (index in 0 until SAMPLE_COUNT) {
            pcg[index] = buffer.int
        }

        return ParsedBlePacket.Audio(
            sequence = sequence,
            pcg = pcg
        )
    }

    /**
     * Giải mã payload của Bio packet.
     */
    private fun parseBioPayload(
        buffer: ByteBuffer,
        sequence: Int,
        payloadLength: Int
    ): ParsedBlePacket.Bio? {
        if (payloadLength != BIO_PAYLOAD_SIZE) {
            return null
        }

        if (buffer.remaining() < BIO_PAYLOAD_SIZE + PACKET_END_SIZE) {
            return null
        }

        val ppgIr = LongArray(SAMPLE_COUNT)
        val ecg = ShortArray(SAMPLE_COUNT)

        /*
         * PPG IR ở STM32 là uint32_t.
         *
         * ByteBuffer.int đọc thành Int có dấu, nên cần chuyển sang Long
         * và AND với 0xFFFFFFFFL để giữ đúng toàn bộ giá trị uint32_t.
         */
        for (index in 0 until SAMPLE_COUNT) {
            ppgIr[index] =
                buffer.int.toLong() and 0xFFFFFFFFL
        }

        /*
         * ECG ở STM32 là int16_t, tương ứng với Short trong Kotlin.
         */
        for (index in 0 until SAMPLE_COUNT) {
            ecg[index] = buffer.short
        }

        return ParsedBlePacket.Bio(
            sequence = sequence,
            ppgIr = ppgIr,
            ecg = ecg
        )
    }

    /**
     * Đọc một byte không dấu và chuyển thành Int từ 0 đến 255.
     */
    private fun readUnsignedByte(
        buffer: ByteBuffer
    ): Int {
        return buffer.get().toInt() and 0xFF
    }

    /**
     * Đọc một số nguyên 16 bit không dấu theo Little-endian.
     */
    private fun readUnsignedShort(
        buffer: ByteBuffer
    ): Int {
        return buffer.short.toInt() and 0xFFFF
    }
}