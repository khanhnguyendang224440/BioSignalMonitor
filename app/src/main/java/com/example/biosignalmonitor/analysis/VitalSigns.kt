/**
 * @file VitalSigns.kt
 * @brief Kết quả tính toán nhịp tim và PAT trên Android App.
 *
 * HR được tính từ hai R-peak ECG liên tiếp. PAT được tính từ R-peak ECG
 * đến PPG foot tương ứng sau khi App tự khôi phục trục thời gian theo chỉ
 * số mẫu. Với sampleRateHz = 1000, mỗi sample tương ứng 1 ms.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

data class VitalSigns(
    val heartRateBpm: Double? = null,
    val patMs: Double? = null,
    val patMeanMs: Double? = null,
    val lastRPeakSample: Long? = null,
    val lastPpgFootSample: Long? = null,
    val statusText: String = "Waiting for ECG R-peak / PPG foot"
)
