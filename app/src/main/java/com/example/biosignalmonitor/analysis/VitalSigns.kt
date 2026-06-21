/**
 * @file VitalSigns.kt
 * @brief Kết quả tính toán nhịp tim và PTT trên Android App.
 *
 * HR và PTT được tính từ raw ECG + PPG IR sau khi App tự khôi phục trục
 * thời gian theo chỉ số mẫu. Với sampleRateHz = 1000, mỗi sample tương ứng 1 ms.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

data class VitalSigns(
    val heartRateBpm: Double? = null,
    val pttMs: Double? = null,
    val lastRPeakSample: Long? = null,
    val lastPpgPeakSample: Long? = null,
    val statusText: String = "Waiting for ECG/PPG peaks"
)
