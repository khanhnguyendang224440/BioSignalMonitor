/**
 * @file PpgPeakDetector.kt
 * @brief Phát hiện đỉnh PPG IR trên Android App.
 *
 * Phiên bản đầu dùng PPG peak vì dễ kiểm thử hơn PPG foot. Khi cần bám sát
 * hướng ước lượng huyết áp hơn, có thể thay detector này bằng bộ tìm foot point.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

import kotlin.math.max

class PpgPeakDetector(
    private val sampleRateHz: Int = 1000
) {
    private val refractorySamples: Long = (0.30 * sampleRateHz).toLong()
    private var lastPeakSample: Long = Long.MIN_VALUE / 4
    private var lastProcessedSample: Long = Long.MIN_VALUE / 4

    fun reset() {
        lastPeakSample = Long.MIN_VALUE / 4
        lastProcessedSample = Long.MIN_VALUE / 4
    }

    fun detectNew(window: List<IndexedSample>): List<Long> {
        if (window.size < 5) return emptyList()

        val newestIndex = window.last().index
        val recent = window.takeLast(minOf(window.size, sampleRateHz * 3))
        val mean = SignalPreprocessor.mean(recent)

        val centeredRecent = recent.map { it.value - mean }
        val maxValue = centeredRecent.maxOrNull() ?: return emptyList()
        val minValue = centeredRecent.minOrNull() ?: return emptyList()
        val amplitude = maxValue - minValue
        if (amplitude < 1.0) return emptyList()

        val threshold = maxValue - amplitude * 0.35
        val peaks = mutableListOf<Long>()

        for (i in 1 until window.size - 1) {
            val sampleIndex = window[i].index

            if (sampleIndex <= lastProcessedSample) continue
            if (sampleIndex >= newestIndex) break

            val prev = window[i - 1].value - mean
            val curr = window[i].value - mean
            val next = window[i + 1].value - mean

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
