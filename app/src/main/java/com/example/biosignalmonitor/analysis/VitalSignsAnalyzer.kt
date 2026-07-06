/**
 * @file VitalSignsAnalyzer.kt
 * @brief Lọc nhẹ, tìm R-peak ECG, PPG foot và tính HR + PAT ở Android App.
 *
 * App giữ nguyên packet raw từ STM32. Vì STM32 đảm bảo ECG và PPG IR cùng
 * 1 kHz, block 32 mẫu liên tục và không mất mẫu, App tự tạo trục thời gian
 * bằng globalSampleCounter. Ở 1 kHz, một sample tương ứng 1 ms.
 *
 * HR  = khoảng cách giữa hai R-peak ECG liên tiếp.
 * PAT = khoảng thời gian từ R-peak ECG đến PPG foot tương ứng ở ngón tay.
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

    private val ecgFilter = SosIirFilter.ecgBandpass1000Hz()
    private val ppgFilter = SosIirFilter.ppgBandpass1000Hz()

    private val rPeakDetector = RPeakDetector(sampleRateHz)
    private val ppgFootDetector = PpgFootDetector(sampleRateHz)

    private val pendingRPeaks = mutableListOf<Long>()
    private val ppgFootHistory = mutableListOf<Long>()
    private val patHistory = mutableListOf<IndexedSample>()

    private var lastRPeakSample: Long? = null
    private var lastPpgFootSample: Long? = null
    private var latestHeartRateBpm: Double? = null
    private var latestPatMs: Double? = null
    private var latestPatMeanMs: Double? = null

    fun reset() {
        ecgWindow.clear()
        ppgWindow.clear()
        pendingRPeaks.clear()
        ppgFootHistory.clear()
        patHistory.clear()

        ecgFilter.reset()
        ppgFilter.reset()

        rPeakDetector.reset()
        ppgFootDetector.reset()

        lastRPeakSample = null
        lastPpgFootSample = null
        latestHeartRateBpm = null
        latestPatMs = null
        latestPatMeanMs = null
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
            val filteredEcg = ecgFilter.filter(ecg[i].toDouble())
            val filteredPpg = ppgFilter.filter(ppgIr[i].toDouble())

            ecgWindow.add(
                IndexedSample(
                    index = sampleIndex,
                    value = filteredEcg
                )
            )
            ppgWindow.add(
                IndexedSample(
                    index = sampleIndex,
                    value = filteredPpg
                )
            )
        }

        val newestIndex = blockStartSample + count - 1L
        val maxSamples = sampleRateHz * maxWindowSeconds
        SignalPreprocessor.trimWindow(ecgWindow, newestIndex, maxSamples)
        SignalPreprocessor.trimWindow(ppgWindow, newestIndex, maxSamples)

        val newRPeaks = rPeakDetector.detectNew(ecgWindow)
        val newPpgFeet = ppgFootDetector.detectNew(ppgWindow)

        handleRPeaks(newRPeaks)
        handlePpgFeet(newPpgFeet, newestIndex)
        matchPat(newestIndex)

        val status = buildStatusText(newRPeaks.size, newPpgFeet.size)
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

    private fun handlePpgFeet(
        ppgFeet: List<Long>,
        newestIndex: Long
    ) {
        ppgFootHistory.addAll(ppgFeet)

        val historyWindow = sampleRateHz * 2L
        ppgFootHistory.removeAll { foot ->
            newestIndex - foot > historyWindow
        }
    }

    private fun matchPat(newestIndex: Long) {
        // PAT vẫn được tính từ R-peak ECG tới PPG foot/đáy tương ứng.
        // Mở rộng cửa sổ ghép để không bỏ sót nhịp khi tín hiệu thực tế lệch nhẹ.
        val minPatSamples = (0.18 * sampleRateHz).toLong()
        val maxPatSamples = (0.50 * sampleRateHz).toLong()

        val iterator = pendingRPeaks.iterator()
        while (iterator.hasNext()) {
            val rPeak = iterator.next()

            val matchedFoot = ppgFootHistory.firstOrNull { ppgFoot ->
                val diff = ppgFoot - rPeak
                diff in minPatSamples..maxPatSamples
            }

            if (matchedFoot != null) {
                val patMs = (matchedFoot - rPeak) * 1000.0 / sampleRateHz
                latestPatMs = patMs
                lastPpgFootSample = matchedFoot
                addPatToRollingWindow(
                    patSampleIndex = matchedFoot,
                    patMs = patMs,
                    newestIndex = newestIndex
                )
                iterator.remove()
            } else if (newestIndex - rPeak > maxPatSamples) {
                iterator.remove()
            }
        }
    }


    private fun addPatToRollingWindow(
        patSampleIndex: Long,
        patMs: Double,
        newestIndex: Long
    ) {
        patHistory.add(
            IndexedSample(
                index = patSampleIndex,
                value = patMs
            )
        )

        val rollingWindowSamples = sampleRateHz * 10L
        patHistory.removeAll { pat ->
            newestIndex - pat.index > rollingWindowSamples
        }

        latestPatMeanMs = if (patHistory.isNotEmpty()) {
            patHistory.sumOf { pat -> pat.value } / patHistory.size
        } else {
            null
        }
    }

    private fun currentResult(status: String): VitalSigns {
        return VitalSigns(
            heartRateBpm = latestHeartRateBpm,
            patMs = latestPatMs,
            patMeanMs = latestPatMeanMs,
            lastRPeakSample = lastRPeakSample,
            lastPpgFootSample = lastPpgFootSample,
            statusText = status
        )
    }

    private fun buildStatusText(
        newRPeakCount: Int,
        newPpgFootCount: Int
    ): String {
        return when {
            latestHeartRateBpm != null && latestPatMeanMs != null ->
                "HR/PAT mean ready"

            latestHeartRateBpm != null ->
                "HR ready, waiting PPG foot"

            newRPeakCount > 0 && newPpgFootCount == 0 ->
                "R-peak detected, waiting PPG foot"

            newPpgFootCount > 0 && newRPeakCount == 0 ->
                "PPG foot detected, waiting ECG R-peak"

            else ->
                "Waiting for ECG R-peak / PPG foot"
        }
    }
}
