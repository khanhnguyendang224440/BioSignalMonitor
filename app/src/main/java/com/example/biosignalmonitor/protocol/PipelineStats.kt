/**
 * @file PipelineStats.kt
 * @brief Quản lý các thông số thống kê của luồng nhận và xử lý dữ liệu BLE.
 *
 * File này lưu các bộ đếm và trạng thái dùng để kiểm tra toàn bộ pipeline:
 *
 * BLE ByteArray
 * → PacketParser
 * → PacketAssembler
 * → BioSignalFrame
 * → SignalRingBuffer
 * → Waveform UI
 *
 * Các thông số trong PipelineStats được dùng để:
 *
 * - Theo dõi số packet Audio và Bio đã nhận.
 * - Theo dõi số frame hoàn chỉnh đã ghép thành công.
 * - Phát hiện lỗi parse, CRC, mất packet và frame không đầy đủ.
 * - Hiển thị thông tin trong màn hình Statistics của ứng dụng.
 *
 * File này chỉ định nghĩa dữ liệu thống kê, không trực tiếp nhận BLE,
 * parse packet hoặc cập nhật giao diện.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

/**
 * Trạng thái thống kê của pipeline xử lý dữ liệu.
 *
 * @property audioPacketsReceived Tổng số Audio packet đã nhận hợp lệ.
 * @property bioPacketsReceived Tổng số Bio packet đã nhận hợp lệ.
 * @property completedFrames Tổng số frame đủ ECG, PPG và PCG đã ghép thành công.
 * @property parseErrors Tổng số packet không thể giải mã.
 * @property crcErrors Tổng số packet có CRC không hợp lệ.
 * @property incompleteFrames Tổng số frame bị thiếu Audio hoặc Bio packet.
 * @property lostPackets Tổng số packet được phát hiện bị mất theo sequence.
 * @property currentSequence Sequence gần nhất đã xử lý.
 * @property runningTimeMs Thời gian chạy của pipeline, tính bằng mili giây.
 */
data class PipelineStats(
    val audioPacketsReceived: Long = 0L,
    val bioPacketsReceived: Long = 0L,
    val completedFrames: Long = 0L,

    val parseErrors: Long = 0L,
    val crcErrors: Long = 0L,
    val incompleteFrames: Long = 0L,
    val lostPackets: Long = 0L,

    val currentSequence: Int = 0,
    val runningTimeMs: Long = 0L
) {

    /**
     * Tổng số packet Audio và Bio đã nhận hợp lệ.
     */
    val totalPacketsReceived: Long
        get() = audioPacketsReceived + bioPacketsReceived

    /**
     * Kiểm tra pipeline hiện tại có đang không có lỗi hay không.
     *
     * @return true nếu chưa ghi nhận lỗi parse, CRC, packet mất
     * hoặc frame không đầy đủ.
     */
    fun isHealthy(): Boolean {
        return parseErrors == 0L &&
                crcErrors == 0L &&
                incompleteFrames == 0L &&
                lostPackets == 0L
    }
}