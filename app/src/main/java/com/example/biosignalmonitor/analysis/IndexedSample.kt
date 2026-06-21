/**
 * @file IndexedSample.kt
 * @brief Mẫu tín hiệu kèm chỉ số mẫu toàn cục do App tự dựng.
 *
 * Packet truyền từ STM32 giữ nguyên định dạng cũ và không chứa timestamp.
 * Vì hệ thống đảm bảo ECG/PPG IR cùng 1 kHz, mỗi frame có 32 mẫu liên tục,
 * App dùng globalSampleCounter để gắn index cho từng mẫu.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

data class IndexedSample(
    val index: Long,
    val value: Double
)
