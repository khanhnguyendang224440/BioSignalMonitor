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