/**
 * @file BioSignalFrame.kt
 * @brief Định nghĩa một block tín hiệu hoàn chỉnh gồm ECG, PPG và PCG.
 *
 * File này biểu diễn dữ liệu sau khi PacketAssembler ghép thành công:
 *
 * Audio packet có sequence N
 * +
 * Bio packet có sequence N
 *
 * Kết quả là một BioSignalFrame chứa đầy đủ ba tín hiệu:
 *
 * - ECG: 32 mẫu kiểu int16_t ở phía ESP32.
 * - PPG IR: 32 mẫu kiểu uint32_t ở phía ESP32.
 * - PCG: 32 mẫu kiểu int32_t ở phía ESP32.
 *
 * BioSignalFrame là dữ liệu đầu ra của tầng ghép packet và là đầu vào
 * cho SignalRingBuffer, bộ lưu dữ liệu CSV và giao diện hiển thị waveform.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

/**
 * Đại diện cho một frame tín hiệu hoàn chỉnh sau khi ghép đủ
 * Audio packet và Bio packet có cùng sequence.
 *
 * @property sequence Số thứ tự của frame, có giá trị từ 0 đến 255.
 * @property ecg Mảng 32 mẫu ECG.
 * @property ppgIr Mảng 32 mẫu PPG IR.
 * @property pcg Mảng 32 mẫu PCG.
 */
data class BioSignalFrame(
    val sequence: Int,
    val ecg: ShortArray,
    val ppgIr: LongArray,
    val pcg: IntArray
) {

    /**
     * Số mẫu trong mỗi kênh của frame.
     *
     * Theo thiết kế hiện tại, giá trị này phải bằng 32.
     */
    val sampleCount: Int
        get() = ecg.size

    /**
     * Kiểm tra frame có đầy đủ và đồng nhất số mẫu giữa ba kênh hay không.
     *
     * @return true nếu ECG, PPG và PCG đều có cùng số mẫu và khác rỗng.
     */
    fun isValid(): Boolean {
        return ecg.isNotEmpty() &&
                ecg.size == ppgIr.size &&
                ecg.size == pcg.size
    }
}