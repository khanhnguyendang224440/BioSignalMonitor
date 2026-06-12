/**
 * @file MainActivity.kt
 * @brief Quản lý màn hình chính của ứng dụng BioSignalMonitor.
 *
 * File này xây dựng giao diện dashboard dùng để hiển thị waveform ECG,
 * PPG và PCG, đồng thời quản lý các thao tác người dùng như tạm dừng,
 * tiếp tục, reset và xem thông số hệ thống.
 *
 * Trong giai đoạn hiện tại, MainActivity sử dụng dữ liệu giả lập để kiểm
 * thử giao diện. Logic nhận packet, giải mã và ghép dữ liệu sẽ được tách
 * sang các thành phần riêng trong tầng protocol và signal.
 *
 * MainActivity chỉ nên quản lý trạng thái giao diện và điều phối dữ liệu,
 * không trực tiếp xử lý cấu trúc binary packet BLE.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.biosignalmonitor.ble.BleConnectionState
import com.example.biosignalmonitor.ble.BleManager
import com.example.biosignalmonitor.fake.FakeBleSource
import com.example.biosignalmonitor.protocol.PacketAssembler
import com.example.biosignalmonitor.protocol.PacketParser
import com.example.biosignalmonitor.protocol.ParsedBlePacket
import com.example.biosignalmonitor.signal.SignalRingBuffer
import com.example.biosignalmonitor.ui.theme.BioSignalMonitorTheme
import kotlinx.coroutines.delay

private val AppBackground = Color(0xFF0B1220)
private val CardBackground = Color(0xFF121C2B)
private val PrimaryText = Color(0xFFF4F7FB)
private val SecondaryText = Color(0xFF9EADBF)

private val EcgColor = Color(0xFF38E66B)
private val PpgColor = Color(0xFFFF4D6D)
private val PcgColor = Color(0xFF35C7FF)
private val SimulationColor = Color(0xFFFFB020)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//            // =========================================================
//            // 1. Tạo hai packet giả theo đúng định dạng STM32/BLE
//            // =========================================================
//
//            val audioBytes =
//                FakeBleSource.makeAudioPacket(sequence = 10)
//
//            val bioBytes =
//                FakeBleSource.makeBioPacket(sequence = 10)
//
//            // =========================================================
//            // 2. Parse ByteArray thành Audio packet và Bio packet
//            // =========================================================
//
//            val parsedAudio =
//                PacketParser.parse(audioBytes)
//
//            val parsedBio =
//                PacketParser.parse(bioBytes)
//
//            // =========================================================
//            // 3. Kiểm tra kết quả PacketParser
//            // =========================================================
//
//            if (parsedAudio is ParsedBlePacket.Audio) {
//                Log.d(
//                    "PARSER_TEST",
//                    "Audio seq=${parsedAudio.sequence}, " +
//                            "PCG count=${parsedAudio.pcg.size}"
//                )
//            } else {
//                Log.e(
//                    "PARSER_TEST",
//                    "Audio packet parse failed"
//                )
//            }
//
//            if (parsedBio is ParsedBlePacket.Bio) {
//                Log.d(
//                    "PARSER_TEST",
//                    "Bio seq=${parsedBio.sequence}, " +
//                            "PPG count=${parsedBio.ppgIr.size}, " +
//                            "ECG count=${parsedBio.ecg.size}"
//                )
//            } else {
//                Log.e(
//                    "PARSER_TEST",
//                    "Bio packet parse failed"
//                )
//            }
//
//            // =========================================================
//            // 4. Kiểm tra PacketAssembler
//            // =========================================================
//
//            val packetAssembler = PacketAssembler()
//
//            val frameAfterAudio =
//                parsedAudio?.let { packet ->
//                    packetAssembler.push(packet)
//                }
//
//            Log.d(
//                "ASSEMBLER_TEST",
//                "After Audio: frame=${frameAfterAudio != null}, " +
//                        "pendingAudio=${packetAssembler.pendingAudioCount()}, " +
//                        "pendingBio=${packetAssembler.pendingBioCount()}"
//            )
//
//            val frameAfterBio =
//                parsedBio?.let { packet ->
//                    packetAssembler.push(packet)
//                }
//
//            if (frameAfterBio != null) {
//                Log.d(
//                    "ASSEMBLER_TEST",
//                    "Frame seq=${frameAfterBio.sequence}, " +
//                            "ECG=${frameAfterBio.ecg.size}, " +
//                            "PPG=${frameAfterBio.ppgIr.size}, " +
//                            "PCG=${frameAfterBio.pcg.size}, " +
//                            "valid=${frameAfterBio.isValid()}"
//                )
//
//                Log.d(
//                    "ASSEMBLER_TEST",
//                    "audioRx=${packetAssembler.audioPacketsReceived}, " +
//                            "bioRx=${packetAssembler.bioPacketsReceived}, " +
//                            "completed=${packetAssembler.completedFrames}, " +
//                            "incomplete=${packetAssembler.incompleteFrames}"
//                )
//            } else {
//                Log.e(
//                    "ASSEMBLER_TEST",
//                    "Frame assembly failed"
//                )
//            }
//
//            // =========================================================
//            // 5. Giao diện ứng dụng
//
//            // =========================================================

        setContent {
            BioSignalMonitorTheme {

                val context =
                    LocalContext.current

                var blePermissionGranted by remember {
                    mutableStateOf(
                        hasBlePermissions(context)
                    )
                }

                var bleStatusText by remember {
                    mutableStateOf(
                        if (blePermissionGranted) {
                            "BLE permission: granted"
                        } else {
                            "BLE permission: not granted"
                        }
                    )
                }

                val blePermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->

                        blePermissionGranted =
                            result.values.all { granted ->
                                granted
                            }

                        bleStatusText =
                            if (blePermissionGranted) {
                                "BLE permission: granted"
                            } else {
                                "BLE permission: denied"
                            }
                    }

                /*
                 * Handler dùng để đưa callback BLE từ background thread
                 * về main thread trước khi cập nhật Compose state.
                 */
                val mainHandler =
                    remember {
                        Handler(
                            Looper.getMainLooper()
                        )
                    }

                /*
                 * BleManager thật:
                 *
                 * - Bấm Connect BLE sẽ gọi startScan().
                 * - Khi tìm thấy ESP32_BLE, BleManager sẽ tự connect.
                 * - Khi bật được notification, dữ liệu thô sẽ đi vào
                 *   onDataReceived dưới dạng ByteArray.
                 *
                 * Hiện tại onDataReceived mới log kích thước raw bytes,
                 * chưa nối vào PacketParser để tránh trộn dữ liệu BLE thật
                 * với FakeBleSource khi đang kiểm thử giao diện.
                 */
                val bleManager =
                    remember {
                        BleManager(
                            context = context,
                            onDataReceived = { bytes ->
                                Log.d(
                                    "BLE_RAW_TEST",
                                    "BLE raw notification: ${bytes.size} bytes"
                                )
                            },
                            onStateChanged = { state ->
                                mainHandler.post {
                                    bleStatusText =
                                        when (state) {
                                            BleConnectionState.Idle ->
                                                "BLE idle"

                                            BleConnectionState.Scanning ->
                                                "BLE scanning: ESP32_BLE"

                                            BleConnectionState.Connecting ->
                                                "BLE connecting"

                                            BleConnectionState.Connected ->
                                                "BLE connected"

                                            BleConnectionState.Ready ->
                                                "BLE ready: notifications enabled"

                                            BleConnectionState.Disconnected ->
                                                "BLE disconnected"

                                            is BleConnectionState.Error ->
                                                "BLE error: ${state.message}"
                                        }
                                }
                            }
                        )
                    }

                /*
                 * Khi Activity bị đóng, dừng scan và ngắt kết nối BLE
                 * để tránh rò tài nguyên BluetoothGatt.
                 */
                DisposableEffect(Unit) {
                    onDispose {
                        bleManager.disconnect()
                    }
                }

                val ecgBuffer = remember {
                    SignalRingBuffer(capacity = 2000)
                }

                val ppgBuffer = remember {
                    SignalRingBuffer(capacity = 2000)
                }

                val pcgBuffer = remember {
                    SignalRingBuffer(capacity = 2000)
                }

                val realtimeAssembler = remember {
                    PacketAssembler()
                }

                var ecgSamples by remember {
                    mutableStateOf(FloatArray(0))
                }

                var ppgSamples by remember {
                    mutableStateOf(FloatArray(0))
                }

                var pcgSamples by remember {
                    mutableStateOf(FloatArray(0))
                }

                var currentSequence by remember {
                    mutableStateOf(0)
                }

                var currentTimestamp by remember {
                    mutableStateOf(0L)
                }

                var isPaused by remember {
                    mutableStateOf(false)
                }

                var showStatistics by remember {
                    mutableStateOf(false)
                }

                var packetCount by remember {
                    mutableStateOf(0L)
                }

                var parseErrorCount by remember {
                    mutableStateOf(0L)
                }

                LaunchedEffect(Unit) {
                    var sequence = 0
                    var elapsedTimeMs = 0L

                    while (true) {
                        if (!isPaused) {
                            // =====================================================
                            // 1. Giả lập hai BLE notification cùng sequence
                            // =====================================================

                            val audioBytes =
                                FakeBleSource.makeAudioPacket(sequence)

                            val bioBytes =
                                FakeBleSource.makeBioPacket(sequence)

                            // =====================================================
                            // 2. Parse Audio packet
                            // =====================================================

                            val parsedAudio =
                                PacketParser.parse(audioBytes)

                            if (parsedAudio != null) {
                                packetCount++

                                val frameFromAudio =
                                    realtimeAssembler.push(parsedAudio)

                                /*
                                 * Thông thường frameFromAudio sẽ null vì Bio packet
                                 * của cùng sequence chưa được đưa vào assembler.
                                 */
                                if (frameFromAudio != null) {
                                    ecgBuffer.pushSamples(frameFromAudio.ecg)
                                    ppgBuffer.pushSamples(frameFromAudio.ppgIr)
                                    pcgBuffer.pushSamples(frameFromAudio.pcg)

                                    ecgSamples = ecgBuffer.snapshot()
                                    ppgSamples = ppgBuffer.snapshot()
                                    pcgSamples = pcgBuffer.snapshot()

                                    currentSequence = frameFromAudio.sequence
                                    elapsedTimeMs += 32L
                                    currentTimestamp = elapsedTimeMs
                                }
                            } else {
                                parseErrorCount++

                                Log.e(
                                    "REALTIME_TEST",
                                    "Audio parse failed at seq=$sequence"
                                )
                            }

                            // =====================================================
                            // 3. Parse Bio packet
                            // =====================================================

                            val parsedBio =
                                PacketParser.parse(bioBytes)

                            if (parsedBio != null) {
                                packetCount++

                                val frameFromBio =
                                    realtimeAssembler.push(parsedBio)

                                /*
                                 * Sau khi Bio packet vào, assembler đã có cả Audio
                                 * và Bio cùng sequence nên thường tạo frame tại đây.
                                 */
                                if (frameFromBio != null) {
                                    ecgBuffer.pushSamples(frameFromBio.ecg)
                                    ppgBuffer.pushSamples(frameFromBio.ppgIr)
                                    pcgBuffer.pushSamples(frameFromBio.pcg)

                                    ecgSamples = ecgBuffer.snapshot()
                                    ppgSamples = ppgBuffer.snapshot()
                                    pcgSamples = pcgBuffer.snapshot()

                                    currentSequence = frameFromBio.sequence
                                    elapsedTimeMs += 32L
                                    currentTimestamp = elapsedTimeMs
                                }
                            } else {
                                parseErrorCount++

                                Log.e(
                                    "REALTIME_TEST",
                                    "Bio parse failed at seq=$sequence"
                                )
                            }

                            // Chỉ log định kỳ để tránh làm chậm ứng dụng.
                            if (sequence % 50 == 0) {
                                Log.d(
                                    "REALTIME_TEST",
                                    "seq=$sequence, " +
                                            "audioRx=${realtimeAssembler.audioPacketsReceived}, " +
                                            "bioRx=${realtimeAssembler.bioPacketsReceived}, " +
                                            "frames=${realtimeAssembler.completedFrames}, " +
                                            "incomplete=${realtimeAssembler.incompleteFrames}, " +
                                            "parseErrors=$parseErrorCount, " +
                                            "buffers=${ecgBuffer.size()}/" +
                                            "${ppgBuffer.size()}/" +
                                            "${pcgBuffer.size()}"
                                )
                            }

                            /*
                             * Sequence trong packet là uint8_t:
                             * 0, 1, 2, ..., 255, sau đó quay về 0.
                             */
                            sequence = (sequence + 1) and 0xFF
                        }

                        /*
                         * Mỗi block truyền có 32 mẫu ở tần số hiệu dụng 1000 Hz:
                         * 32 / 1000 = 0,032 giây = 32 ms.
                         */
                        delay(32L)
                    }
                }

                BioSignalDashboard(
                    ecg = ecgSamples,
                    ppg = ppgSamples,
                    pcg = pcgSamples,
                    sequence = currentSequence,
                    timestamp = currentTimestamp,
                    packetCount = packetCount,
                    parseErrorCount = parseErrorCount,
                    ecgBufferSize = ecgBuffer.size(),
                    ppgBufferSize = ppgBuffer.size(),
                    pcgBufferSize = pcgBuffer.size(),
                    bleStatusText = bleStatusText,
                    isPaused = isPaused,
                    showStatistics = showStatistics,
                    onPauseToggle = {
                        isPaused = !isPaused
                    },
                    onReset = {
                        ecgBuffer.clear()
                        ppgBuffer.clear()
                        pcgBuffer.clear()

                        realtimeAssembler.clear()

                        ecgSamples = FloatArray(0)
                        ppgSamples = FloatArray(0)
                        pcgSamples = FloatArray(0)

                        currentSequence = 0
                        currentTimestamp = 0L
                        packetCount = 0L
                        parseErrorCount = 0L
                    },
                    onShowStatistics = {
                        showStatistics = true
                    },
                    onCloseStatistics = {
                        showStatistics = false
                    },
                    onConnectBle = {
                        if (hasBlePermissions(context)) {
                            blePermissionGranted = true
                            bleStatusText = "BLE scan requested"

                            /*
                             * BLE thật:
                             * Sau khi đã có runtime permission, bắt đầu quét
                             * để tìm thiết bị có tên ESP32_BLE.
                             *
                             * Lưu ý:
                             * - FakeBleSource vẫn tiếp tục chạy để không làm hỏng
                             *   pipeline kiểm thử hiện tại.
                             * - Dữ liệu BLE thật hiện mới được log raw size ở
                             *   tag BLE_RAW_TEST, chưa đưa vào waveform.
                             */
                            bleManager.startScan()
                        } else {
                            bleStatusText = "BLE permission: requesting..."

                            blePermissionLauncher.launch(
                                requiredBlePermissions()
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BioSignalDashboard(
    ecg: FloatArray,
    ppg: FloatArray,
    pcg: FloatArray,
    sequence: Int,
    timestamp: Long,
    packetCount: Long,
    parseErrorCount: Long,
    ecgBufferSize: Int,
    ppgBufferSize: Int,
    pcgBufferSize: Int,
    bleStatusText: String,
    isPaused: Boolean,
    showStatistics: Boolean,
    onPauseToggle: () -> Unit,
    onReset: () -> Unit,
    onShowStatistics: () -> Unit,
    onCloseStatistics: () -> Unit,
    onConnectBle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                )
        ) {
            DashboardHeader(
                sequence = sequence,
                timestamp = timestamp,
                bleStatusText = bleStatusText
            )

            SignalCard(
                title = "ECG",
                subtitle = "Electrocardiogram",
                sampleRate = "1000 Hz",
                samples = ecg,
                lineColor = EcgColor,
                modifier = Modifier.weight(1f)
            )

            SignalCard(
                title = "PPG",
                subtitle = "Photoplethysmogram",
                sampleRate = "1000 Hz",
                samples = ppg,
                lineColor = PpgColor,
                modifier = Modifier.weight(1f)
            )

            SignalCard(
                title = "PCG",
                subtitle = "Phonocardiogram",
                sampleRate = "1000 Hz",
                samples = pcg,
                lineColor = PcgColor,
                modifier = Modifier.weight(1f)
            )

            ControlBar(
                isPaused = isPaused,
                onPauseToggle = onPauseToggle,
                onReset = onReset,
                onShowStatistics = onShowStatistics,
                onConnectBle = onConnectBle
            )
        }
    }

    if (showStatistics) {
        StatisticsDialog(
            sequence = sequence,
            timestamp = timestamp,
            packetCount = packetCount,
            parseErrorCount = parseErrorCount,
            ecgBufferSize = ecgBufferSize,
            ppgBufferSize = ppgBufferSize,
            pcgBufferSize = pcgBufferSize,
            bleStatusText = bleStatusText,
            onDismiss = onCloseStatistics
        )
    }
}

@Composable
fun DashboardHeader(
    sequence: Int,
    timestamp: Long,
    bleStatusText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "BioSignal Monitor",
            color = PrimaryText,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "● SIMULATION | $bleStatusText",
            color = SimulationColor,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Seq: $sequence",
            color = SecondaryText,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.width(18.dp))

        Text(
            text = "Time: ${formatTime(timestamp)}",
            color = SecondaryText,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SignalCard(
    title: String,
    subtitle: String,
    sampleRate: String,
    samples: FloatArray,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.width(125.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = lineColor,
                    fontSize = 20.sp
                )

                Text(
                    text = subtitle,
                    color = SecondaryText,
                    fontSize = 11.sp
                )

                Text(
                    text = sampleRate,
                    color = PrimaryText,
                    fontSize = 12.sp
                )

                Text(
                    text = "${samples.size}/2000 samples",
                    color = SecondaryText,
                    fontSize = 11.sp
                )
            }

            WaveformCanvas(
                samples = samples,
                lineColor = lineColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun ControlBar(
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onReset: () -> Unit,
    onShowStatistics: () -> Unit,
    onConnectBle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPauseToggle
        ) {
            Text(
                text = if (isPaused) {
                    "Resume"
                } else {
                    "Pause"
                }
            )
        }

        OutlinedButton(
            onClick = onReset
        ) {
            Text("Reset")
        }

        OutlinedButton(
            onClick = onShowStatistics
        ) {
            Text("Statistics")
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onConnectBle,
            enabled = true
        ) {
            Text("Connect BLE")
        }

        OutlinedButton(
            onClick = {},
            enabled = false
        ) {
            Text("Save CSV")
        }
    }
}

@Composable
fun StatisticsDialog(
    sequence: Int,
    timestamp: Long,
    packetCount: Long,
    parseErrorCount: Long,
    ecgBufferSize: Int,
    ppgBufferSize: Int,
    pcgBufferSize: Int,
    bleStatusText: String,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("System Statistics")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("=== Mode ===")
                Text("Mode: Simulation")
                Text("BLE status: $bleStatusText")

                Text("")
                Text("=== Packet Format ===")
                Text("Packet strategy: Split Audio/Bio")
                Text("Header: 0xAA")
                Text("Version: 0x01")
                Text("Footer: 0x55")
                Text("Audio type: 0x01")
                Text("Bio type: 0x02")

                Text("")
                Text("=== Packet Size ===")
                Text("Audio packet: 137 bytes")
                Text("Bio packet: 201 bytes")

                Text("")
                Text("=== Runtime ===")
                Text("Sequence: $sequence")
                Text("Running time: ${formatTime(timestamp)}")
                Text("Packets received: $packetCount")
                Text("Parse errors: $parseErrorCount")

                Text("")
                Text("=== Ring Buffer ===")
                Text("ECG buffer: $ecgBufferSize / 2000")
                Text("PPG buffer: $ppgBufferSize / 2000")
                Text("PCG buffer: $pcgBufferSize / 2000")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

fun formatTime(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000L

    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val milliseconds = timestampMs % 1000L

    return String.format(
        "%02d:%02d:%02d.%03d",
        hours,
        minutes,
        seconds,
        milliseconds
    )
}

/**
 * Trả về danh sách quyền BLE cần xin theo từng phiên bản Android.
 *
 * Android 12 trở lên:
 * - BLUETOOTH_SCAN để quét thiết bị BLE.
 * - BLUETOOTH_CONNECT để kết nối và giao tiếp BLE.
 *
 * Android 11 trở xuống:
 * - ACCESS_FINE_LOCATION thường cần thiết để scan BLE.
 */
private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

/**
 * Kiểm tra app đã được cấp đủ quyền BLE cần thiết hay chưa.
 *
 * Hàm này chỉ kiểm tra quyền, chưa scan BLE thật.
 * Khi bấm Connect BLE, nếu chưa đủ quyền thì app sẽ hiện hộp thoại xin quyền.
 */
private fun hasBlePermissions(
    context: Context
): Boolean {
    return requiredBlePermissions().all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
