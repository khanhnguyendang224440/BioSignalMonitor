/**
 * @file PacketAssembler.kt
 * @brief Ghép Audio packet và Bio packet có cùng sequence thành một frame hoàn chỉnh.
 *
 * File này sẽ nhận các đối tượng ParsedBlePacket sau khi PacketParser
 * giải mã dữ liệu BLE.
 *
 * Trong giai đoạn hiện tại, phần ghép packet chưa được triển khai đầy đủ.
 * File được giữ lại để chuẩn bị cho bước ghép:
 *
 * Audio packet sequence N
 * +
 * Bio packet sequence N
 * →
 * BioSignalFrame sequence N
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

class PacketAssembler {

    /**
     * Tạm thời nhận packet đã parse nhưng chưa thực hiện ghép.
     *
     * @return null cho đến khi logic assembler được triển khai.
     */
    fun push(packet: ParsedBlePacket): BioSignalFrame? {
        return null
    }
}