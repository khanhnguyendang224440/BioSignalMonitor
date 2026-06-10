/**
 * @file BleManager.kt
 * @brief Quản lý kết nối BLE và nhận binary packet từ thiết bị ESP32.
 *
 * File này chịu trách nhiệm cho tầng truyền dữ liệu BLE:
 *
 * - Kiểm tra khả năng hỗ trợ Bluetooth trên thiết bị Android.
 * - Quản lý trạng thái kết nối BLE.
 * - Kết nối tới thiết bị ESP32.
 * - Yêu cầu MTU phù hợp với packet Audio và Bio.
 * - Khám phá BLE service và characteristic.
 * - Bật notification để nhận ByteArray từ ESP32.
 * - Chuyển dữ liệu thô sang tầng PacketParser thông qua callback.
 *
 * BleManager chỉ quản lý việc truyền dữ liệu BLE.
 * File này không giải mã packet, không ghép Audio/Bio packet,
 * không lưu RingBuffer và không trực tiếp cập nhật giao diện.
 *
 * Hiện tại các hàm scan và connect thật chưa được kích hoạt để tránh
 * ảnh hưởng đến luồng kiểm thử bằng FakeBleSource.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 10/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import java.util.UUID

/**
 * Các trạng thái kết nối BLE của ứng dụng.
 */
sealed class BleConnectionState {

    data object Idle : BleConnectionState()

    data object Scanning : BleConnectionState()

    data object Connecting : BleConnectionState()

    data object Connected : BleConnectionState()

    data object Ready : BleConnectionState()

    data object Disconnected : BleConnectionState()

    data class Error(
        val message: String
    ) : BleConnectionState()
}

/**
 * Quản lý kết nối BLE với ESP32-S3.
 *
 * @param context Context của ứng dụng.
 * @param onDataReceived Callback trả về ByteArray nhận từ BLE notification.
 * @param onStateChanged Callback thông báo trạng thái BLE mới.
 */
class BleManager(
    context: Context,
    private val onDataReceived: (ByteArray) -> Unit,
    private val onStateChanged: (BleConnectionState) -> Unit
) {

    companion object {
        private const val TAG = "BLE_MANAGER"

        /**
         * MTU mục tiêu.
         *
         * ATT payload notification tối đa thường bằng MTU - 3.
         * Khi MTU = 247, payload dự kiến là 244 byte.
         */
        const val TARGET_MTU = 247

        /*
         * TODO:
         * Thay các UUID mẫu bên dưới bằng UUID thật từ code ESP32.
         *
         * Không mở comment hoặc sử dụng kết nối thật cho đến khi
         * xác nhận UUID từ phía ESP32.
         */

        /*
        val SERVICE_UUID: UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000")

        val NOTIFY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000")
        */

        /**
         * UUID chuẩn dùng để bật notification.
         */
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )
    }

    private val applicationContext =
        context.applicationContext

    private val bluetoothManager =
        applicationContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * Trả về true nếu điện thoại có Bluetooth adapter.
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * Trả về true nếu Bluetooth đang được bật.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Khung hàm scan BLE.
     *
     * Hiện tại chưa triển khai để không ảnh hưởng luồng FakeBleSource.
     */
    fun startScan() {
        Log.d(
            TAG,
            "BLE scan is not enabled yet"
        )

        onStateChanged(
            BleConnectionState.Scanning
        )

        /*
         * TODO BLE thật:
         *
         * 1. Lấy BluetoothLeScanner.
         * 2. Tạo ScanCallback.
         * 3. Lọc theo tên thiết bị hoặc Service UUID.
         * 4. Khi tìm thấy ESP32 thì dừng scan.
         * 5. Gọi connectGatt().
         *
         * Không thêm code thật ở đây cho đến khi có:
         * - BLE device name
         * - Service UUID
         * - Notify characteristic UUID
         */
    }

    /**
     * Dừng quá trình scan BLE.
     */
    fun stopScan() {
        Log.d(
            TAG,
            "BLE scan stop requested"
        )

        /*
         * TODO BLE thật:
         * bluetoothAdapter
         *     ?.bluetoothLeScanner
         *     ?.stopScan(scanCallback)
         */
    }

    /**
     * Tạm thời chưa kết nối tới thiết bị thật.
     *
     * Hàm này sẽ được triển khai khi đã có BluetoothDevice từ ScanCallback.
     */
    fun connect() {
        Log.d(
            TAG,
            "BLE connect is not enabled yet"
        )

        onStateChanged(
            BleConnectionState.Connecting
        )

        /*
         * TODO BLE thật:
         *
         * bluetoothGatt = device.connectGatt(
         *     applicationContext,
         *     false,
         *     gattCallback,
         *     BluetoothDevice.TRANSPORT_LE
         * )
         */
    }

    /**
     * Ngắt kết nối và giải phóng BluetoothGatt.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        onStateChanged(
            BleConnectionState.Disconnected
        )

        Log.d(
            TAG,
            "BLE disconnected"
        )
    }

    /**
     * Callback nền của Android BLE.
     *
     * Hiện được khai báo sẵn nhưng chưa được dùng cho kết nối thật.
     */
    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(
                    gatt,
                    status,
                    newState
                )

                /*
                 * TODO BLE thật:
                 *
                 * Khi newState là STATE_CONNECTED:
                 * - lưu bluetoothGatt
                 * - cập nhật Connected
                 * - requestMtu(247)
                 * - discoverServices()
                 *
                 * Khi STATE_DISCONNECTED:
                 * - cập nhật Disconnected
                 */
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                super.onMtuChanged(
                    gatt,
                    mtu,
                    status
                )

                Log.d(
                    TAG,
                    "MTU changed: mtu=$mtu, status=$status"
                )

                /*
                 * TODO BLE thật:
                 *
                 * Sau khi MTU thay đổi thành công:
                 * gatt.discoverServices()
                 */
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                super.onServicesDiscovered(
                    gatt,
                    status
                )

                Log.d(
                    TAG,
                    "Services discovered: status=$status"
                )

                /*
                 * TODO BLE thật:
                 *
                 * val service =
                 *     gatt.getService(SERVICE_UUID)
                 *
                 * val characteristic =
                 *     service?.getCharacteristic(
                 *         NOTIFY_CHARACTERISTIC_UUID
                 *     )
                 *
                 * enableNotifications(
                 *     gatt,
                 *     characteristic
                 * )
                 */
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                super.onCharacteristicChanged(
                    gatt,
                    characteristic
                )

                val bytes =
                    characteristic.value ?: return

                Log.d(
                    TAG,
                    "Notification received: ${bytes.size} bytes"
                )

                /*
                 * Đây là đầu vào thật của pipeline:
                 *
                 * BLE ByteArray
                 * → PacketParser
                 * → PacketAssembler
                 * → BioSignalFrame
                 * → SignalRingBuffer
                 * → UI
                 */
                onDataReceived(bytes)
            }
        }

    /**
     * Bật notification cho characteristic.
     *
     * Hàm đã được chuẩn bị nhưng chưa gọi khi chưa có UUID thật.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val notificationEnabled =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        if (!notificationEnabled) {
            onStateChanged(
                BleConnectionState.Error(
                    "Cannot enable BLE notification"
                )
            )

            return
        }

        val descriptor =
            characteristic.getDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG_UUID
            )

        if (descriptor == null) {
            onStateChanged(
                BleConnectionState.Error(
                    "Notification descriptor not found"
                )
            )

            return
        }

        descriptor.value =
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        val writeStarted =
            gatt.writeDescriptor(descriptor)

        if (!writeStarted) {
            onStateChanged(
                BleConnectionState.Error(
                    "Cannot write notification descriptor"
                )
            )

            return
        }

        onStateChanged(
            BleConnectionState.Ready
        )

        Log.d(
            TAG,
            "BLE notifications enabled"
        )
    }
}