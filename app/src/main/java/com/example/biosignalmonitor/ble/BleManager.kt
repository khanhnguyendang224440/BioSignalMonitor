/**
 * @file BleManager.kt
 * @brief Quản lý kết nối BLE và nhận binary packet từ thiết bị ESP32.
 *
 * File này chịu trách nhiệm cho tầng truyền dữ liệu BLE:
 *
 * - Kiểm tra khả năng hỗ trợ Bluetooth trên thiết bị Android.
 * - Quản lý trạng thái kết nối BLE.
 * - Quét thiết bị BLE đang phát quảng bá.
 * - Tìm đúng thiết bị ESP32 theo tên ESP32_BLE.
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
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 10/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
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

        const val TARGET_DEVICE_NAME =
            "ESP32_PERIPHERAL_PHUC"

        const val TARGET_MTU = 247

        val SERVICE_UUID: UUID =
            UUID.fromString(
                "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
            )

        val NOTIFY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString(
                "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
            )

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

    private var isScanning = false

    /**
     * Callback scan BLE.
     *
     * Khi tìm thấy thiết bị có tên ESP32_BLE, app sẽ dừng scan
     * và bắt đầu kết nối tới thiết bị đó.
     */
    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                super.onScanResult(
                    callbackType,
                    result
                )

                val device =
                    result.device

                val deviceName =
                    result.scanRecord?.deviceName
                        ?: device.name
                        ?: "Unknown"

                Log.d(
                    TAG,
                    "Found BLE device: name=$deviceName, address=${device.address}, rssi=${result.rssi}"
                )

                if (deviceName == TARGET_DEVICE_NAME) {
                    Log.d(
                        TAG,
                        "Target device found: $TARGET_DEVICE_NAME"
                    )

                    stopScan()
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)

                isScanning = false

                Log.e(
                    TAG,
                    "BLE scan failed: errorCode=$errorCode"
                )

                onStateChanged(
                    BleConnectionState.Error(
                        "BLE scan failed: $errorCode"
                    )
                )
            }
        }

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
     * Bắt đầu quét BLE để tìm thiết bị ESP32_BLE.
     *
     * Lưu ý:
     * - Hàm này giả định MainActivity đã kiểm tra runtime permission.
     * - Với Android 10, điện thoại thường cần bật Location để scan BLE.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter =
            bluetoothAdapter

        if (adapter == null) {
            Log.e(
                TAG,
                "Bluetooth is not supported"
            )

            onStateChanged(
                BleConnectionState.Error(
                    "Bluetooth is not supported"
                )
            )

            return
        }

        if (!adapter.isEnabled) {
            Log.e(
                TAG,
                "Bluetooth is disabled"
            )

            onStateChanged(
                BleConnectionState.Error(
                    "Bluetooth is disabled"
                )
            )

            return
        }

        val scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {
            Log.e(
                TAG,
                "BluetoothLeScanner is null"
            )

            onStateChanged(
                BleConnectionState.Error(
                    "BluetoothLeScanner is null"
                )
            )

            return
        }

        if (isScanning) {
            Log.d(
                TAG,
                "BLE scan is already running"
            )

            return
        }

        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build()

        Log.d(
            TAG,
            "BLE scan started, target=$TARGET_DEVICE_NAME"
        )

        isScanning = true

        onStateChanged(
            BleConnectionState.Scanning
        )

        scanner.startScan(
            null,
            scanSettings,
            scanCallback
        )
    }

    /**
     * Dừng quá trình scan BLE.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) {
            return
        }

        val scanner =
            bluetoothAdapter?.bluetoothLeScanner

        scanner?.stopScan(
            scanCallback
        )

        isScanning = false

        Log.d(
            TAG,
            "BLE scan stopped"
        )
    }

    /**
     * Hàm connect công khai tạm giữ lại để dùng về sau.
     *
     * Hiện luồng connect thật được gọi tự động sau khi scan tìm thấy
     * BluetoothDevice đúng tên ESP32_BLE.
     */
    fun connect() {
        Log.d(
            TAG,
            "Use startScan() to find and connect target BLE device"
        )
    }

    /**
     * Kết nối tới thiết bị BLE đã tìm thấy.
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(
        device: BluetoothDevice
    ) {
        Log.d(
            TAG,
            "Connecting to ${device.name ?: TARGET_DEVICE_NAME}, address=${device.address}"
        )

        onStateChanged(
            BleConnectionState.Connecting
        )

        bluetoothGatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    applicationContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(
                    applicationContext,
                    false,
                    gattCallback
                )
            }
    }

    /**
     * Ngắt kết nối và giải phóng BluetoothGatt.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()

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
     */
    private val gattCallback =
        object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
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

                Log.d(
                    TAG,
                    "Connection state changed: status=$status, newState=$newState"
                )

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(
                        TAG,
                        "GATT connection error: status=$status"
                    )

                    onStateChanged(
                        BleConnectionState.Error(
                            "GATT connection error: $status"
                        )
                    )

                    gatt.close()
                    bluetoothGatt = null
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        bluetoothGatt = gatt

                        Log.d(
                            TAG,
                            "BLE connected"
                        )

                        onStateChanged(
                            BleConnectionState.Connected
                        )

                        val mtuRequestStarted =
                            gatt.requestMtu(TARGET_MTU)

                        Log.d(
                            TAG,
                            "Request MTU $TARGET_MTU started=$mtuRequestStarted"
                        )

                        if (!mtuRequestStarted) {
                            Log.w(
                                TAG,
                                "MTU request failed to start, discovering services directly"
                            )

                            gatt.discoverServices()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(
                            TAG,
                            "BLE disconnected from device"
                        )

                        bluetoothGatt = null

                        onStateChanged(
                            BleConnectionState.Disconnected
                        )

                        gatt.close()
                    }
                }
            }

            @SuppressLint("MissingPermission")
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
                 * Dù MTU có thể không đạt 247 trên một số máy,
                 * vẫn thử discover service để xem thiết bị có sẵn sàng không.
                 */
                gatt.discoverServices()
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

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onStateChanged(
                        BleConnectionState.Error(
                            "Service discovery failed: $status"
                        )
                    )

                    return
                }

                val service =
                    gatt.getService(
                        SERVICE_UUID
                    )

                if (service == null) {
                    Log.e(
                        TAG,
                        "Target service not found: $SERVICE_UUID"
                    )

                    onStateChanged(
                        BleConnectionState.Error(
                            "Target service not found"
                        )
                    )

                    return
                }

                val characteristic =
                    service.getCharacteristic(
                        NOTIFY_CHARACTERISTIC_UUID
                    )

                if (characteristic == null) {
                    Log.e(
                        TAG,
                        "Notify characteristic not found: $NOTIFY_CHARACTERISTIC_UUID"
                    )

                    onStateChanged(
                        BleConnectionState.Error(
                            "Notify characteristic not found"
                        )
                    )

                    return
                }

                val properties =
                    characteristic.properties

                val supportsNotify =
                    properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

                val supportsIndicate =
                    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

                Log.d(
                    TAG,
                    "Characteristic properties: notify=$supportsNotify, indicate=$supportsIndicate"
                )

                if (!supportsNotify && !supportsIndicate) {
                    onStateChanged(
                        BleConnectionState.Error(
                            "Characteristic does not support notify/indicate"
                        )
                    )

                    return
                }

                enableNotifications(
                    gatt,
                    characteristic
                )
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

                handleNotification(bytes)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                super.onDescriptorWrite(
                    gatt,
                    descriptor,
                    status
                )

                Log.d(
                    TAG,
                    "Notification descriptor write: status=$status"
                )

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onStateChanged(
                        BleConnectionState.Ready
                    )

                    Log.d(
                        TAG,
                        "BLE notifications enabled"
                    )
                } else {
                    onStateChanged(
                        BleConnectionState.Error(
                            "Notification descriptor write failed: $status"
                        )
                    )
                }
            }
        }

    /**
     * Xử lý dữ liệu nhận được từ BLE notification.
     */
    private fun handleNotification(
        bytes: ByteArray
    ) {
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

    /**
     * Bật notification hoặc indication cho characteristic.
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

        val properties =
            characteristic.properties

        val supportsNotify =
            properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

        descriptor.value =
            if (supportsNotify) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }

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

        Log.d(
            TAG,
            "Notification descriptor write started"
        )
    }
}
