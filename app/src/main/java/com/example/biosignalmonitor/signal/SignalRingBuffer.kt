/**
 * @file SignalRingBuffer.kt
 * @brief Lưu trữ vòng các mẫu tín hiệu mới nhất để hiển thị realtime.
 *
 * File này triển khai bộ đệm vòng có dung lượng cố định cho tín hiệu ECG,
 * PPG và PCG. Khi bộ đệm đầy, mẫu mới sẽ ghi đè lên mẫu cũ nhất.
 *
 * SignalRingBuffer giúp ứng dụng chỉ giữ một số lượng mẫu giới hạn,
 * tránh tăng bộ nhớ liên tục trong quá trình nhận dữ liệu dài hạn.
 *
 * Đầu vào là các mẫu tín hiệu đã được PacketParser và PacketAssembler xử lý.
 * Đầu ra là FloatArray theo đúng thứ tự thời gian để WaveformCanvas hiển thị.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.signal

class SignalRingBuffer(
    private val capacity: Int
) {
    private val buffer = FloatArray(capacity)

    private var writeIndex = 0
    private var currentSize = 0

    fun push(value: Float) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity

        if (currentSize < capacity) {
            currentSize++
        }
    }

    fun pushSamples(samples: ShortArray) {
        for (sample in samples) {
            push(sample.toFloat())
        }
    }

    fun snapshot(): FloatArray {
        if (currentSize == 0) {
            return FloatArray(0)
        }

        val output = FloatArray(currentSize)

        // Khi buffer chưa đầy, dữ liệu nằm từ index 0 đến currentSize - 1.
        if (currentSize < capacity) {
            for (i in 0 until currentSize) {
                output[i] = buffer[i]
            }

            return output
        }

        // Khi buffer đầy, writeIndex chính là vị trí của mẫu cũ nhất.
        var outputIndex = 0

        for (i in writeIndex until capacity) {
            output[outputIndex++] = buffer[i]
        }

        for (i in 0 until writeIndex) {
            output[outputIndex++] = buffer[i]
        }

        return output
    }

    fun clear() {
        writeIndex = 0
        currentSize = 0
        buffer.fill(0f)
    }

    fun size(): Int = currentSize
}