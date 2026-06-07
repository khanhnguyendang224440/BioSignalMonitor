package com.example.biosignalmonitor.signal

class RingBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var index = 0
    private var filled = false

    fun push(value: Float) {
        data[index] = value
        index = (index + 1) % capacity

        if (index == 0) {
            filled = true
        }
    }

    fun pushMany(values: FloatArray) {
        for (value in values) {
            push(value)
        }
    }

    fun snapshot(): FloatArray {
        return if (!filled) {
            data.copyOfRange(0, index)
        } else {
            data.copyOfRange(index, capacity) + data.copyOfRange(0, index)
        }
    }

    fun clear() {
        index = 0
        filled = false
        data.fill(0f)
    }
}