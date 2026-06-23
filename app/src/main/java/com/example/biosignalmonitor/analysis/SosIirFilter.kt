/**
 * @file SosIirFilter.kt
 * @brief Bộ lọc IIR dạng SOS dùng realtime trên Android App.
 *
 * Các hệ số được tạo theo cùng thông số trong denoisePlot.py:
 * fs = 1000 Hz, Butterworth band-pass order = 4.
 *
 * Lưu ý: Python dùng filtfilt nên phù hợp xử lý offline toàn bộ file CSV.
 * App realtime không thể nhìn trước mẫu tương lai, vì vậy dùng bộ lọc nhân quả
 * một chiều với cùng dải thông để chạy trực tiếp khi nhận BLE packet.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.analysis

class SosIirFilter(
    private val sections: Array<DoubleArray>
) {
    private val z1 = DoubleArray(sections.size)
    private val z2 = DoubleArray(sections.size)

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    fun filter(input: Double): Double {
        var value = input

        for (i in sections.indices) {
            val section = sections[i]
            val b0 = section[0]
            val b1 = section[1]
            val b2 = section[2]
            val a1 = section[4]
            val a2 = section[5]

            val output = b0 * value + z1[i]
            z1[i] = b1 * value - a1 * output + z2[i]
            z2[i] = b2 * value - a2 * output
            value = output
        }

        return value
    }

    companion object {
        fun ecgBandpass1000Hz(): SosIirFilter {
            return SosIirFilter(
                arrayOf(
                    doubleArrayOf(1.0911667053306711e-05, 2.1823334106613423e-05, 1.0911667053306711e-05, 1.0, -1.799856289596911, 0.81180072304903383),
                    doubleArrayOf(1.0, 2.0, 1.0, 1.0, -1.902151139520083, 0.91689666895519417),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9875732356393732, 0.98762019321014793),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9955138129224106, 0.99555419514924015)
                )
            )
        }

        fun ppgBandpass1000Hz(): SosIirFilter {
            return SosIirFilter(
                arrayOf(
                    doubleArrayOf(1.3050725063736034e-06, 2.6101450127472067e-06, 1.3050725063736034e-06, 1.0, -1.88761143937378, 0.89156426139109735),
                    doubleArrayOf(1.0, 2.0, 1.0, 1.0, -1.9471606643897781, 0.95250679378442671),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9870287099440025, 0.98708205648370295),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9957574453877616, 0.99579832500052445)
                )
            )
        }

        fun pcgBandpass1000Hz(): SosIirFilter {
            return SosIirFilter(
                arrayOf(
                    doubleArrayOf(0.033350848426110039, 0.066701696852220077, 0.033350848426110039, 1.0, -0.49766891692489185, 0.11605050814039145),
                    doubleArrayOf(1.0, 2.0, 1.0, 1.0, -0.49825504097024426, 0.52790293494662899),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.7507574493125075, 0.76995420428711048),
                    doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.904133095607339, 0.91985579384620897)
                )
            )
        }
    }
}
