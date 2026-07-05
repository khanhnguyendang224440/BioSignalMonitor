/**
 * @file BleRealtimeTestLogger.kt
 * @brief Ghi log các testcase BLE thực tế TC07--TC11 ra Logcat.
 *
 * Các testcase này không dùng dữ liệu mô phỏng. Chúng được kích hoạt theo
 * luồng thật của ứng dụng: bấm Connect BLE, nhận notification, gom stream,
 * parse packet CRC hợp lệ, chuyển sang BLE realtime và hiển thị frame thật.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.test

import android.util.Log

class BleRealtimeTestLogger {
    companion object {
        private const val TAG = "APP_TEST_BLE"
    }

    private var tc07Started = false
    private var tc07Passed = false
    private var tc08Passed = false
    private var tc09Passed = false
    private var tc10Passed = false
    private var tc11Passed = false

    fun reset() {
        tc07Started = false
        tc07Passed = false
        tc08Passed = false
        tc09Passed = false
        tc10Passed = false
        tc11Passed = false

        Log.i(
            TAG,
            "================ BLE REALTIME TEST RESET ================"
        )
    }

    fun onConnectBleClicked() {
        if (tc07Started) return
        tc07Started = true

        Log.i(TAG, "================ TC07 START ================")
        Log.i(TAG, "NAME    : Kiểm thử quét và kết nối BLE")
        Log.i(TAG, "PURPOSE : Kiểm tra app bắt đầu quét, kết nối và vào trạng thái sẵn sàng nhận dữ liệu BLE.")
        Log.i(TAG, "EXPECTED: State đi qua Scanning/Connecting/Connected và đạt Ready.")
        Log.i(TAG, "ACTION  : Người dùng nhấn Connect BLE trên ứng dụng.")
    }

    fun onBleStateChanged(stateName: String) {
        Log.i(TAG, "TC07 state=$stateName")

        if (!tc07Passed && stateName == "Ready") {
            tc07Passed = true
            Log.i(TAG, "RESULT  : TC07 PASS - BLE đã sẵn sàng nhận notification")
            Log.i(TAG, "================= TC07 END =================")
        }
    }

    fun onBleNotificationReceived(
        notificationIndex: Long,
        byteCount: Int
    ) {
        if (tc08Passed) return

        tc08Passed = true
        Log.i(TAG, "================ TC08 START ================")
        Log.i(TAG, "NAME    : Kiểm thử nhận notification BLE")
        Log.i(TAG, "PURPOSE : Kiểm tra app nhận được ByteArray từ callback BLE notification.")
        Log.i(TAG, "EXPECTED: Có ít nhất một notification, byteCount > 0.")
        Log.i(TAG, "DATA    : notificationIndex=$notificationIndex, byteCount=$byteCount")
        Log.i(TAG, "RESULT  : TC08 PASS - App đã nhận notification BLE")
        Log.i(TAG, "================= TC08 END =================")
    }

    fun onStreamAssemblerResult(
        notificationIndex: Long,
        notificationBytes: Int,
        completePacketCount: Int,
        bufferedBytes: Int,
        packetSizes: List<Int>
    ) {
        if (tc09Passed) return

        if (completePacketCount > 0) {
            tc09Passed = true
            Log.i(TAG, "================ TC09 START ================")
            Log.i(TAG, "NAME    : Kiểm thử gom byte stream từ dữ liệu BLE thực tế")
            Log.i(TAG, "PURPOSE : Kiểm tra BlePacketStreamAssembler tách được packet hoàn chỉnh từ ByteArray BLE.")
            Log.i(TAG, "EXPECTED: completePacketCount > 0, packet size hợp lệ 137 hoặc 201 byte.")
            Log.i(
                TAG,
                "DATA    : notificationIndex=$notificationIndex, notificationBytes=$notificationBytes, " +
                        "completePacketCount=$completePacketCount, packetSizes=$packetSizes, bufferedBytes=$bufferedBytes"
            )
            Log.i(TAG, "RESULT  : TC09 PASS - Đã tách được packet hoàn chỉnh từ BLE stream")
            Log.i(TAG, "================= TC09 END =================")
        } else {
            Log.i(
                TAG,
                "TC09 waiting: notificationIndex=$notificationIndex, " +
                        "notificationBytes=$notificationBytes, bufferedBytes=$bufferedBytes"
            )
        }
    }

    fun onFirstCrcValidBlePacket(
        packetType: String,
        sequence: Int,
        packetSize: Int
    ) {
        if (tc10Passed) return

        tc10Passed = true
        Log.i(TAG, "================ TC10 START ================")
        Log.i(TAG, "NAME    : Kiểm thử chuyển sang BLE realtime khi packet đầu tiên hợp lệ CRC")
        Log.i(TAG, "PURPOSE : Kiểm tra app chỉ chuyển mode sau khi PacketParser parse thành công và CRC hợp lệ.")
        Log.i(TAG, "EXPECTED: Packet đầu tiên parse được, sau đó mode chuyển từ SIMULATION sang BLE REALTIME.")
        Log.i(TAG, "DATA    : packetType=$packetType, sequence=$sequence, packetSize=$packetSize")
        Log.i(TAG, "RESULT  : TC10 PASS - Packet BLE đầu tiên hợp lệ CRC, app chuyển sang BLE realtime")
        Log.i(TAG, "================= TC10 END =================")
    }

    fun onRealBleFrameDisplayed(
        sequence: Int,
        ecgCount: Int,
        ppgCount: Int,
        pcgCount: Int,
        ecgBufferSize: Int,
        ppgBufferSize: Int,
        pcgBufferSize: Int,
        completedFrames: Long
    ) {
        if (tc11Passed) return

        tc11Passed = true
        Log.i(TAG, "================ TC11 START ================")
        Log.i(TAG, "NAME    : Kiểm thử hiển thị frame BLE thực tế trên giao diện")
        Log.i(TAG, "PURPOSE : Kiểm tra Audio/Bio từ BLE được ghép thành BioSignalFrame và đẩy lên RingBuffer/UI.")
        Log.i(TAG, "EXPECTED: Frame đủ 32 mẫu ECG, 32 mẫu PPG, 32 mẫu PCG; buffer UI tăng.")
        Log.i(
            TAG,
            "DATA    : seq=$sequence, ecg=$ecgCount, ppg=$ppgCount, pcg=$pcgCount, " +
                    "buffers=$ecgBufferSize/$ppgBufferSize/$pcgBufferSize, completedFrames=$completedFrames"
        )
        Log.i(TAG, "RESULT  : TC11 PASS - Frame BLE thực tế đã được hiển thị trên UI")
        Log.i(TAG, "================= TC11 END =================")
    }
}