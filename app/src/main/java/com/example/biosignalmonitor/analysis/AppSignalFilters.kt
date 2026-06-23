/**
 * @file AppSignalFilters.kt
 * @brief Bộ lọc realtime cho ECG, PPG IR và PCG trước khi hiển thị.
 *
 * Thông số lọc theo denoisePlot.py:
 * ECG: 1–20 Hz, PPG IR: 1–12 Hz, PCG: 20–200 Hz, fs = 1000 Hz.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

class AppSignalFilters {
    private val ecgFilter = SosIirFilter.ecgBandpass1000Hz()
    private val ppgFilter = SosIirFilter.ppgBandpass1000Hz()
    private val pcgFilter = SosIirFilter.pcgBandpass1000Hz()

    fun reset() {
        ecgFilter.reset()
        ppgFilter.reset()
        pcgFilter.reset()
    }

    fun filterEcg(samples: ShortArray): FloatArray {
        return FloatArray(samples.size) { index ->
            ecgFilter.filter(samples[index].toDouble()).toFloat()
        }
    }

    fun filterPpgIr(samples: LongArray): FloatArray {
        return FloatArray(samples.size) { index ->
            ppgFilter.filter(samples[index].toDouble()).toFloat()
        }
    }

    fun filterPcg(samples: IntArray): FloatArray {
        return FloatArray(samples.size) { index ->
            pcgFilter.filter(samples[index].toDouble()).toFloat()
        }
    }
}
