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

/**
 * Bộ đệm vòng dùng để lưu các mẫu tín hiệu mới nhất.
 *
 * @property capacity Số mẫu tối đa được giữ trong buffer.
 */
class SignalRingBuffer(
    private val capacity: Int
) {
    private val buffer = FloatArray(capacity)

    private var writeIndex = 0
    private var currentSize = 0

    init {
        require(capacity > 0) {
            "Capacity must be greater than 0"
        }
    }

    /**
     * Thêm một mẫu Float vào buffer.
     */
    fun push(value: Float) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity

        if (currentSize < capacity) {
            currentSize++
        }
    }

    /**
     * Thêm mảng ECG kiểu int16_t / ShortArray.
     */
    fun pushSamples(samples: ShortArray) {
        for (sample in samples) {
            push(sample.toFloat())
        }
    }

    /**
     * Thêm mảng PCG kiểu int32_t / IntArray.
     */
    fun pushSamples(samples: IntArray) {
        for (sample in samples) {
            push(sample.toFloat())
        }
    }

    /**
     * Thêm mảng PPG IR kiểu uint32_t đã được lưu bằng LongArray.
     */
    fun pushSamples(samples: LongArray) {
        for (sample in samples) {
            push(sample.toFloat())
        }
    }

    /**
     * Trả về dữ liệu theo đúng thứ tự thời gian:
     * mẫu cũ nhất đứng trước, mẫu mới nhất đứng sau.
     */
    fun snapshot(): FloatArray {
        if (currentSize == 0) {
            return FloatArray(0)
        }

        val output = FloatArray(currentSize)

        if (currentSize < capacity) {
            for (index in 0 until currentSize) {
                output[index] = buffer[index]
            }

            return output
        }

        var outputIndex = 0

        for (index in writeIndex until capacity) {
            output[outputIndex++] = buffer[index]
        }

        for (index in 0 until writeIndex) {
            output[outputIndex++] = buffer[index]
        }

        return output
    }

    /**
     * Xóa toàn bộ dữ liệu trong buffer.
     */
    fun clear() {
        buffer.fill(0f)
        writeIndex = 0
        currentSize = 0
    }

    /**
     * Trả về số mẫu hiện đang có trong buffer.
     */
    fun size(): Int {
        return currentSize
    }

    /**
     * Trả về dung lượng tối đa của buffer.
     */
    fun capacity(): Int {
        return capacity
    }
}