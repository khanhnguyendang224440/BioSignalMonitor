/**
 * @file VitalSignsAnalyzer.kt
 * @brief Lọc nhẹ, tìm đỉnh ECG/PPG và tính HR + PTT ở Android App.
 *
 * App giữ nguyên packet raw từ STM32. Vì STM32 đảm bảo ECG và PPG IR cùng
 * 1 kHz, block 32 mẫu liên tục và không mất mẫu, App tự tạo trục thời gian
 * bằng globalSampleCounter. Ở 1 kHz, một sample tương ứng 1 ms.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

class VitalSignsAnalyzer(
    private val sampleRateHz: Int = 1000,
    private val maxWindowSeconds: Int = 5
) {
    private val ecgWindow = mutableListOf<IndexedSample>()
    private val ppgWindow = mutableListOf<IndexedSample>()

    private val rPeakDetector = RPeakDetector(sampleRateHz)
    private val ppgPeakDetector = PpgPeakDetector(sampleRateHz)

    private val pendingRPeaks = mutableListOf<Long>()
    private val ppgPeakHistory = mutableListOf<Long>()

    private var lastRPeakSample: Long? = null
    private var lastPpgPeakSample: Long? = null
    private var latestHeartRateBpm: Double? = null
    private var latestPttMs: Double? = null

    fun reset() {
        ecgWindow.clear()
        ppgWindow.clear()
        pendingRPeaks.clear()
        ppgPeakHistory.clear()

        rPeakDetector.reset()
        ppgPeakDetector.reset()

        lastRPeakSample = null
        lastPpgPeakSample = null
        latestHeartRateBpm = null
        latestPttMs = null
    }

    fun processFrame(
        ecg: ShortArray,
        ppgIr: LongArray,
        blockStartSample: Long
    ): VitalSigns {
        val count = minOf(ecg.size, ppgIr.size)
        if (count == 0) {
            return currentResult("Empty frame")
        }

        for (i in 0 until count) {
            val sampleIndex = blockStartSample + i.toLong()
            ecgWindow.add(
                IndexedSample(
                    index = sampleIndex,
                    value = ecg[i].toDouble()
                )
            )
            ppgWindow.add(
                IndexedSample(
                    index = sampleIndex,
                    value = ppgIr[i].toDouble()
                )
            )
        }

        val newestIndex = blockStartSample + count - 1L
        val maxSamples = sampleRateHz * maxWindowSeconds
        SignalPreprocessor.trimWindow(ecgWindow, newestIndex, maxSamples)
        SignalPreprocessor.trimWindow(ppgWindow, newestIndex, maxSamples)

        val newRPeaks = rPeakDetector.detectNew(ecgWindow)
        val newPpgPeaks = ppgPeakDetector.detectNew(ppgWindow)

        handleRPeaks(newRPeaks)
        handlePpgPeaks(newPpgPeaks, newestIndex)
        matchPtt(newestIndex)

        val status = buildStatusText(newRPeaks.size, newPpgPeaks.size)
        return currentResult(status)
    }

    private fun handleRPeaks(rPeaks: List<Long>) {
        for (rPeak in rPeaks) {
            val previousR = lastRPeakSample
            if (previousR != null) {
                val rrSamples = rPeak - previousR
                if (rrSamples > 0) {
                    val hr = 60.0 * sampleRateHz / rrSamples.toDouble()
                    if (hr in 40.0..180.0) {
                        latestHeartRateBpm = hr
                    }
                }
            }

            lastRPeakSample = rPeak
            pendingRPeaks.add(rPeak)
        }
    }

    private fun handlePpgPeaks(
        ppgPeaks: List<Long>,
        newestIndex: Long
    ) {
        ppgPeakHistory.addAll(ppgPeaks)

        val historyWindow = sampleRateHz * 2L
        ppgPeakHistory.removeAll { peak ->
            newestIndex - peak > historyWindow
        }
    }

    private fun matchPtt(newestIndex: Long) {
        val minPttSamples = (0.10 * sampleRateHz).toLong()
        val maxPttSamples = (0.50 * sampleRateHz).toLong()

        val iterator = pendingRPeaks.iterator()
        while (iterator.hasNext()) {
            val rPeak = iterator.next()

            val matchedPpg = ppgPeakHistory.firstOrNull { ppgPeak ->
                val diff = ppgPeak - rPeak
                diff in minPttSamples..maxPttSamples
            }

            if (matchedPpg != null) {
                latestPttMs = (matchedPpg - rPeak) * 1000.0 / sampleRateHz
                lastPpgPeakSample = matchedPpg
                iterator.remove()
            } else if (newestIndex - rPeak > maxPttSamples) {
                iterator.remove()
            }
        }
    }

    private fun currentResult(status: String): VitalSigns {
        return VitalSigns(
            heartRateBpm = latestHeartRateBpm,
            pttMs = latestPttMs,
            lastRPeakSample = lastRPeakSample,
            lastPpgPeakSample = lastPpgPeakSample,
            statusText = status
        )
    }

    private fun buildStatusText(
        newRPeakCount: Int,
        newPpgPeakCount: Int
    ): String {
        return when {
            latestHeartRateBpm != null && latestPttMs != null ->
                "HR/PTT ready"

            latestHeartRateBpm != null ->
                "HR ready, waiting PPG peak"

            newRPeakCount > 0 && newPpgPeakCount == 0 ->
                "R-peak detected, waiting PPG"

            newPpgPeakCount > 0 && newRPeakCount == 0 ->
                "PPG peak detected, waiting ECG"

            else ->
                "Waiting for ECG/PPG peaks"
        }
    }
}
