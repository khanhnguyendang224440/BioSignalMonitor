/**
 * @file ParsedBlePacket.kt
 * @brief Định nghĩa các loại packet sau khi giải mã dữ liệu BLE.
 *
 * File này biểu diễn kết quả đầu ra của PacketParser sau khi một ByteArray
 * được kiểm tra và giải mã theo định dạng packet của hệ thống:
 *
 * Header | Version | Type | Sequence | Payload Length | Data | CRC | Footer
 *
 * Hệ thống hiện sử dụng hai loại packet:
 *
 * - Audio packet:
 *   Chứa 32 mẫu PCG, mỗi mẫu có kiểu int32_t ở phía STM32.
 *
 * - Bio packet:
 *   Chứa 32 mẫu PPG IR kiểu uint32_t và 32 mẫu ECG kiểu int16_t.
 *
 * Các model trong file này chỉ lưu dữ liệu đã được giải mã.
 * Chúng không chịu trách nhiệm kiểm tra header, footer, payload length hoặc CRC.
 * Các bước kiểm tra đó được thực hiện trong PacketParser.kt.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

/**
 * Đại diện cho một packet BLE đã được PacketParser giải mã thành công.
 *
 * Một notification BLE chỉ chứa một trong hai loại packet:
 *
 * - [Audio]: chứa tín hiệu PCG.
 * - [Bio]: chứa tín hiệu PPG IR và ECG.
 */
sealed class ParsedBlePacket {

    /**
     * Sequence của packet, có giá trị từ 0 đến 255.
     *
     * Sequence được dùng để:
     * - Theo dõi thứ tự packet.
     * - Phát hiện packet bị mất.
     * - Ghép Audio packet và Bio packet thuộc cùng một block.
     */
    abstract val sequence: Int

    /**
     * Packet Audio có type = 0x01.
     *
     * Packet này chứa 32 mẫu PCG.
     *
     * Ở phía STM32:
     * - Mỗi mẫu PCG có kiểu int32_t.
     *
     * Ở phía Kotlin:
     * - int32_t tương ứng với Int.
     * - Mảng 32 mẫu được lưu bằng IntArray.
     *
     * @property sequence Số thứ tự của packet, từ 0 đến 255.
     * @property pcg Mảng mẫu PCG đã được giải mã.
     */
    data class Audio(
        override val sequence: Int,
        val pcg: IntArray
    ) : ParsedBlePacket()

    /**
     * Packet Bio có type = 0x02.
     *
     * Packet này chứa:
     * - 32 mẫu PPG IR.
     * - 32 mẫu ECG.
     *
     * Ở phía STM32:
     * - PPG IR có kiểu uint32_t.
     * - ECG có kiểu int16_t.
     *
     * Ở phía Kotlin:
     * - uint32_t được lưu bằng Long để tránh mất miền giá trị không dấu.
     * - int16_t được lưu bằng Short.
     *
     * @property sequence Số thứ tự của packet, từ 0 đến 255.
     * @property ppgIr Mảng mẫu PPG IR đã được giải mã.
     * @property ecg Mảng mẫu ECG đã được giải mã.
     */
    data class Bio(
        override val sequence: Int,
        val ppgIr: LongArray,
        val ecg: ShortArray
    ) : ParsedBlePacket()
}