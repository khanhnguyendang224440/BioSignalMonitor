/**
 * @file PacketParser.kt
 * @brief Giải mã binary packet nhận từ BLE thành dữ liệu Audio hoặc Bio.
 *
 * Định dạng packet:
 *
 * Header | Version | Type | Sequence | Payload Length | Payload | CRC | Footer
 *
 * CRC16-CCITT được tính trên vùng:
 *
 * Header + Version + Type + Sequence + Payload Length + Payload
 *
 * Không tính trường CRC và Footer.
 */

package com.example.biosignalmonitor.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketParser {

    enum class ParseError {
        NONE,
        PACKET_TOO_SHORT,
        HEADER_INVALID,
        VERSION_INVALID,
        TYPE_INVALID,
        LENGTH_INVALID,
        PAYLOAD_INVALID,
        FOOTER_INVALID,
        CRC_MISMATCH,
        TRAILING_BYTES
    }

    var lastError: ParseError = ParseError.NONE
        private set

    private const val HEADER = 0xAA
    private const val VERSION = 0x01

    private const val TYPE_AUDIO = 0x01
    private const val TYPE_BIO = 0x02

    private const val FOOTER = 0x55

    private const val SAMPLE_COUNT = 32

    private const val HEADER_SIZE = 6
    private const val CRC_SIZE = 2
    private const val FOOTER_SIZE = 1
    private const val PACKET_END_SIZE = CRC_SIZE + FOOTER_SIZE

    private const val AUDIO_PAYLOAD_SIZE = SAMPLE_COUNT * Int.SIZE_BYTES

    private const val BIO_PAYLOAD_SIZE =
        SAMPLE_COUNT * Int.SIZE_BYTES +
                SAMPLE_COUNT * Short.SIZE_BYTES

    const val AUDIO_PACKET_SIZE =
        HEADER_SIZE + AUDIO_PAYLOAD_SIZE + PACKET_END_SIZE

    const val BIO_PACKET_SIZE =
        HEADER_SIZE + BIO_PAYLOAD_SIZE + PACKET_END_SIZE

    fun parse(bytes: ByteArray): ParsedBlePacket? {
        lastError = ParseError.NONE

        if (bytes.size < HEADER_SIZE + PACKET_END_SIZE) {
            lastError = ParseError.PACKET_TOO_SHORT
            return null
        }

        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val header = readUnsignedByte(buffer)
        if (header != HEADER) {
            lastError = ParseError.HEADER_INVALID
            return null
        }

        val version = readUnsignedByte(buffer)
        if (version != VERSION) {
            lastError = ParseError.VERSION_INVALID
            return null
        }

        val type = readUnsignedByte(buffer)
        if (type != TYPE_AUDIO && type != TYPE_BIO) {
            lastError = ParseError.TYPE_INVALID
            return null
        }

        val sequence = readUnsignedByte(buffer)
        val payloadLength = readUnsignedShort(buffer)

        val expectedPacketSize =
            HEADER_SIZE + payloadLength + PACKET_END_SIZE

        if (bytes.size != expectedPacketSize) {
            lastError = ParseError.LENGTH_INVALID
            return null
        }

        val footerIndex = expectedPacketSize - FOOTER_SIZE
        val footer = bytes[footerIndex].toInt() and 0xFF
        if (footer != FOOTER) {
            lastError = ParseError.FOOTER_INVALID
            return null
        }

        /*
         * CRC nằm ngay trước Footer.
         * Packet dùng Little-endian nên byte thấp đứng trước byte cao.
         */
        val crcIndex = expectedPacketSize - PACKET_END_SIZE
        val receivedCrc = readUnsignedShortLittleEndian(bytes, crcIndex)

        /*
         * Tính lại CRC16-CCITT trên Header + Payload.
         * Không tính CRC và Footer.
         */
        val calculatedCrc = crc16Ccitt(
            data = bytes,
            offset = 0,
            length = HEADER_SIZE + payloadLength
        )

        if (calculatedCrc != receivedCrc) {
            lastError = ParseError.CRC_MISMATCH
            return null
        }

        val parsedPacket = when (type) {
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
        } ?: run {
            lastError = ParseError.PAYLOAD_INVALID
            return null
        }

        /*
         * Payload đã đọc xong, đọc tiếp CRC để đưa con trỏ ByteBuffer
         * tới đúng vị trí Footer.
         */
        readUnsignedShort(buffer)

        val footerFromBuffer = readUnsignedByte(buffer)
        if (footerFromBuffer != FOOTER) {
            lastError = ParseError.FOOTER_INVALID
            return null
        }

        if (buffer.hasRemaining()) {
            lastError = ParseError.TRAILING_BYTES
            return null
        }

        lastError = ParseError.NONE
        return parsedPacket
    }

    private fun parseAudioPayload(
        buffer: ByteBuffer,
        sequence: Int,
        payloadLength: Int
    ): ParsedBlePacket.Audio? {
        if (payloadLength != AUDIO_PAYLOAD_SIZE) {
            lastError = ParseError.LENGTH_INVALID
            return null
        }

        if (buffer.remaining() < AUDIO_PAYLOAD_SIZE + PACKET_END_SIZE) {
            lastError = ParseError.PAYLOAD_INVALID
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

    private fun parseBioPayload(
        buffer: ByteBuffer,
        sequence: Int,
        payloadLength: Int
    ): ParsedBlePacket.Bio? {
        if (payloadLength != BIO_PAYLOAD_SIZE) {
            lastError = ParseError.LENGTH_INVALID
            return null
        }

        if (buffer.remaining() < BIO_PAYLOAD_SIZE + PACKET_END_SIZE) {
            lastError = ParseError.PAYLOAD_INVALID
            return null
        }

        val ppgIr = LongArray(SAMPLE_COUNT)
        val ecg = ShortArray(SAMPLE_COUNT)

        for (index in 0 until SAMPLE_COUNT) {
            ppgIr[index] = buffer.int.toLong() and 0xFFFFFFFFL
        }

        for (index in 0 until SAMPLE_COUNT) {
            ecg[index] = buffer.short
        }

        return ParsedBlePacket.Bio(
            sequence = sequence,
            ppgIr = ppgIr,
            ecg = ecg
        )
    }

    private fun readUnsignedByte(
        buffer: ByteBuffer
    ): Int {
        return buffer.get().toInt() and 0xFF
    }

    private fun readUnsignedShort(
        buffer: ByteBuffer
    ): Int {
        return buffer.short.toInt() and 0xFFFF
    }

    private fun readUnsignedShortLittleEndian(
        data: ByteArray,
        offset: Int
    ): Int {
        val low = data[offset].toInt() and 0xFF
        val high = data[offset + 1].toInt() and 0xFF
        return low or (high shl 8)
    }

    /*
     * CRC16-CCITT giống STM32/ESP32:
     * Init = 0xFFFF
     * Polynomial = 0x1021
     */
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
}