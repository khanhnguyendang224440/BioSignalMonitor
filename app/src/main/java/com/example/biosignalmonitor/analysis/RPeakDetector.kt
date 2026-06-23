/**
 * @file RPeakDetector.kt
 * @brief Phát hiện đỉnh R trên tín hiệu ECG đã nhận ở App.
 *
 * Thuật toán bản đầu dùng adaptive threshold theo cửa sổ gần nhất và refractory
 * period để tránh bắt nhiều đỉnh trên cùng một nhịp. Mục tiêu là chạy được
 * realtime cho đồ án; sau này có thể thay bằng Pan-Tompkins hoặc AI denoise.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

import kotlin.math.abs
import kotlin.math.max

class RPeakDetector(
    private val sampleRateHz: Int = 1000
) {
    private val refractorySamples: Long = (0.25 * sampleRateHz).toLong()
    private var lastPeakSample: Long = Long.MIN_VALUE / 4
    private var lastProcessedSample: Long = Long.MIN_VALUE / 4

    fun reset() {
        lastPeakSample = Long.MIN_VALUE / 4
        lastProcessedSample = Long.MIN_VALUE / 4
    }

    fun detectNew(window: List<IndexedSample>): List<Long> {
        if (window.size < 5) return emptyList()

        val newestIndex = window.last().index
        val recent = window.takeLast(minOf(window.size, sampleRateHz * 2))
        val mean = SignalPreprocessor.mean(recent)
        val std = SignalPreprocessor.standardDeviation(recent, mean)

        val centeredRecent = recent.map { it.value - mean }
        val maxPositive = centeredRecent.maxOrNull() ?: return emptyList()
        val maxNegativeAbs = abs(centeredRecent.minOrNull() ?: 0.0)

        // AD8232 thường cho R-peak dương. Nếu phần cứng bị đảo cực,
        // chọn cực âm có biên độ lớn hơn để vẫn bắt được đỉnh.
        val polarity = if (maxNegativeAbs > maxPositive * 1.25) -1.0 else 1.0
        val mainAmplitude = max(maxPositive, maxNegativeAbs)
        if (mainAmplitude < 1.0 || std < 0.5) return emptyList()

        val threshold = max(mainAmplitude * 0.35, std * 0.9)
        val peaks = mutableListOf<Long>()

        // Chỉ xử lý đến mẫu áp chót để có đủ láng giềng trái/phải.
        for (i in 1 until window.size - 1) {
            val currentSample = window[i]
            val sampleIndex = currentSample.index

            if (sampleIndex <= lastProcessedSample) continue
            if (sampleIndex >= newestIndex) break

            val prev = polarity * (window[i - 1].value - mean)
            val curr = polarity * (window[i].value - mean)
            val next = polarity * (window[i + 1].value - mean)

            val isLocalPeak = curr > prev && curr >= next
            val isAboveThreshold = curr > threshold
            val farEnough = sampleIndex - lastPeakSample >= refractorySamples

            if (isLocalPeak && isAboveThreshold && farEnough) {
                peaks.add(sampleIndex)
                lastPeakSample = sampleIndex
            }
        }

        lastProcessedSample = newestIndex - 1L
        return peaks
    }
}
