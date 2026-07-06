/**
 * @file PpgFootDetector.kt
 * @brief Phát hiện chân sóng PPG IR để tính PAT trên Android App.
 *
 * Detector này tìm điểm cực tiểu cục bộ của PPG sau lọc, tương ứng với
 * PPG foot/onset trước pha đi lên của sóng mạch. Điểm này được ghép với
 * R-peak ECG để tính PAT = R-peak ECG -> PPG foot.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

class PpgFootDetector(
    private val sampleRateHz: Int = 1000
) {
    private val refractorySamples: Long = (0.30 * sampleRateHz).toLong()
    private val lookAheadSamples: Int = (0.08 * sampleRateHz).toInt()
        .coerceAtLeast(3)
    private var lastFootSample: Long = Long.MIN_VALUE / 4
    private var lastProcessedSample: Long = Long.MIN_VALUE / 4

    fun reset() {
        lastFootSample = Long.MIN_VALUE / 4
        lastProcessedSample = Long.MIN_VALUE / 4
    }

    fun detectNew(window: List<IndexedSample>): List<Long> {
        if (window.size < lookAheadSamples + 3) return emptyList()

        val newestIndex = window.last().index
        val recent = window.takeLast(minOf(window.size, sampleRateHz * 3))
        val mean = SignalPreprocessor.mean(recent)

        val centeredRecent = recent.map { it.value - mean }
        val maxValue = centeredRecent.maxOrNull() ?: return emptyList()
        val minValue = centeredRecent.minOrNull() ?: return emptyList()
        val amplitude = maxValue - minValue
        if (amplitude < 1.0) return emptyList()

        // Với PPG foot, cần bắt vùng đáy trước pha đi lên rõ của sóng mạch.
        val footThreshold = minValue + amplitude * 0.55
        val minRiseAfterFoot = amplitude * 0.18
        val feet = mutableListOf<Long>()

        val maxProcessPosition = window.size - lookAheadSamples - 1
        if (maxProcessPosition <= 1) return emptyList()

        for (i in 1..maxProcessPosition) {
            val sampleIndex = window[i].index

            if (sampleIndex <= lastProcessedSample) continue
            if (sampleIndex >= newestIndex) break

            val prev = window[i - 1].value - mean
            val curr = window[i].value - mean
            val next = window[i + 1].value - mean
            val maxAfterFoot = ((i + 1)..(i + lookAheadSamples))
                .maxOf { position ->
                    window[position].value - mean
                }

            val isLocalFoot = curr <= prev && curr <= next
            val isNearBottom = curr <= footThreshold
            val isRisingAfterFoot = maxAfterFoot - curr >= minRiseAfterFoot
            val farEnough = sampleIndex - lastFootSample >= refractorySamples

            if (isLocalFoot && isNearBottom && isRisingAfterFoot && farEnough) {
                feet.add(sampleIndex)
                lastFootSample = sampleIndex
            }
        }

        lastProcessedSample = window[maxProcessPosition].index
        return feet
    }
}
