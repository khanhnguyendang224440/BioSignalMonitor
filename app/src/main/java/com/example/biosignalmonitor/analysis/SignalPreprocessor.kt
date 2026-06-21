/**
 * @file SignalPreprocessor.kt
 * @brief Một số hàm tiền xử lý tín hiệu đơn giản dùng cho HR/PTT.
 *
 * Các hàm trong file này được thiết kế nhẹ để chạy realtime trên App.
 * Đây không phải pipeline AI ECG denoise; AI denoise vẫn là nhánh xử lý riêng.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

import kotlin.math.sqrt

object SignalPreprocessor {

    fun mean(values: List<IndexedSample>): Double {
        if (values.isEmpty()) return 0.0
        return values.sumOf { it.value } / values.size.toDouble()
    }

    fun standardDeviation(values: List<IndexedSample>, mean: Double): Double {
        if (values.isEmpty()) return 0.0

        val variance = values.sumOf { sample ->
            val diff = sample.value - mean
            diff * diff
        } / values.size.toDouble()

        return sqrt(variance)
    }

    fun trimWindow(
        window: MutableList<IndexedSample>,
        newestIndex: Long,
        maxSamples: Int
    ) {
        val minIndex = newestIndex - maxSamples + 1L
        while (window.isNotEmpty() && window.first().index < minIndex) {
            window.removeAt(0)
        }
    }
}
