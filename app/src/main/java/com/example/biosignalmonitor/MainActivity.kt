/**
 * @file MainActivity.kt
 * @brief Quản lý màn hình chính của ứng dụng BioSignalMonitor.
 *
 * File này xây dựng dashboard hiển thị waveform ECG, PPG và PCG.
 * Ứng dụng có hai chế độ:
 *
 * - SIMULATION: dùng FakeBleSource để kiểm thử UI khi chưa có thiết bị thật.
 * - BLE REALTIME: sau khi nhận packet BLE thật từ ESP32, tự dừng fake
 *   và đưa dữ liệu thật vào PacketParser → PacketAssembler → RingBuffer → UI.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.biosignalmonitor.analysis.VitalSignsAnalyzer
import com.example.biosignalmonitor.ble.BleConnectionState
import com.example.biosignalmonitor.ble.BleManager
import com.example.biosignalmonitor.fake.FakeBleSource
import com.example.biosignalmonitor.protocol.BioSignalFrame
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
private val BleColor = Color(0xFF38E66B)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BioSignalMonitorTheme {
                val context = LocalContext.current

                val mainHandler = remember {
                    Handler(Looper.getMainLooper())
                }

                var blePermissionGranted by remember {
                    mutableStateOf(hasBlePermissions(context))
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

                var useBleMode by remember {
                    mutableStateOf(false)
                }

                val modeText = if (useBleMode) {
                    "BLE REALTIME"
                } else {
                    "SIMULATION"
                }

                val modeColor = if (useBleMode) {
                    BleColor
                } else {
                    SimulationColor
                }

                val blePermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->
                        blePermissionGranted =
                            result.values.all { granted -> granted }

                        bleStatusText =
                            if (blePermissionGranted) {
                                "BLE permission: granted"
                            } else {
                                "BLE permission: denied"
                            }
                    }

                val ecgBuffer = remember { SignalRingBuffer(capacity = 2000) }
                val ppgBuffer = remember { SignalRingBuffer(capacity = 2000) }
                val pcgBuffer = remember { SignalRingBuffer(capacity = 2000) }

                val realtimeAssembler = remember { PacketAssembler() }
                val bleStreamAssembler = remember { BlePacketStreamAssembler() }
                val vitalSignsAnalyzer = remember { VitalSignsAnalyzer(sampleRateHz = 1000) }

                var ecgSamples by remember { mutableStateOf(FloatArray(0)) }
                var ppgSamples by remember { mutableStateOf(FloatArray(0)) }
                var pcgSamples by remember { mutableStateOf(FloatArray(0)) }

                var currentSequence by remember { mutableStateOf(0) }
                var currentTimestamp by remember { mutableStateOf(0L) }
                var globalSampleCounter by remember { mutableStateOf(0L) }
                var currentHeartRateBpm by remember { mutableStateOf<Double?>(null) }
                var currentPttMs by remember { mutableStateOf<Double?>(null) }
                var analysisStatusText by remember { mutableStateOf("Waiting for ECG/PPG peaks") }
                var isPaused by remember { mutableStateOf(false) }
                var showStatistics by remember { mutableStateOf(false) }

                var packetCount by remember { mutableStateOf(0L) }
                var parseErrorCount by remember { mutableStateOf(0L) }
                var bleNotificationCount by remember { mutableStateOf(0L) }

                fun clearRuntimeData() {
                    ecgBuffer.clear()
                    ppgBuffer.clear()
                    pcgBuffer.clear()
                    realtimeAssembler.clear()
                    bleStreamAssembler.clear()
                    vitalSignsAnalyzer.reset()

                    ecgSamples = FloatArray(0)
                    ppgSamples = FloatArray(0)
                    pcgSamples = FloatArray(0)

                    currentSequence = 0
                    currentTimestamp = 0L
                    globalSampleCounter = 0L
                    currentHeartRateBpm = null
                    currentPttMs = null
                    analysisStatusText = "Waiting for ECG/PPG peaks"
                    packetCount = 0L
                    parseErrorCount = 0L
                    bleNotificationCount = 0L
                }

                fun pushFrameToUi(
                    frame: BioSignalFrame,
                    sourceTag: String
                ) {
                    if (!frame.isValid()) {
                        parseErrorCount++
                        Log.e(
                            sourceTag,
                            "Invalid frame: seq=${frame.sequence}, " +
                                    "ecg=${frame.ecg.size}, " +
                                    "ppg=${frame.ppgIr.size}, " +
                                    "pcg=${frame.pcg.size}"
                        )
                        return
                    }

                    val blockStartSample = globalSampleCounter
                    globalSampleCounter += frame.sampleCount.toLong()

                    val vitalSigns = vitalSignsAnalyzer.processFrame(
                        ecg = frame.ecg,
                        ppgIr = frame.ppgIr,
                        blockStartSample = blockStartSample
                    )

                    currentHeartRateBpm = vitalSigns.heartRateBpm
                    currentPttMs = vitalSigns.pttMs
                    analysisStatusText = vitalSigns.statusText

                    ecgBuffer.pushSamples(frame.ecg)
                    ppgBuffer.pushSamples(frame.ppgIr)
                    pcgBuffer.pushSamples(frame.pcg)

                    ecgSamples = ecgBuffer.snapshot()
                    ppgSamples = ppgBuffer.snapshot()
                    pcgSamples = pcgBuffer.snapshot()

                    currentSequence = frame.sequence
                    currentTimestamp = globalSampleCounter

                    if (realtimeAssembler.completedFrames % 50L == 0L) {
                        Log.d(
                            sourceTag,
                            "frame=${realtimeAssembler.completedFrames}, " +
                                    "seq=${frame.sequence}, " +
                                    "audioRx=${realtimeAssembler.audioPacketsReceived}, " +
                                    "bioRx=${realtimeAssembler.bioPacketsReceived}, " +
                                    "incomplete=${realtimeAssembler.incompleteFrames}, " +
                                    "parseErrors=$parseErrorCount, " +
                                    "buffers=${ecgBuffer.size()}/" +
                                    "${ppgBuffer.size()}/" +
                                    "${pcgBuffer.size()}"
                        )
                    }
                }

                fun handleParsedPacket(
                    parsedPacket: ParsedBlePacket,
                    sourceTag: String
                ) {
                    packetCount++

                    when (parsedPacket) {
                        is ParsedBlePacket.Audio -> {
                            if (parsedPacket.pcg.size != 32) {
                                Log.w(
                                    sourceTag,
                                    "Audio sample count abnormal: " +
                                            "seq=${parsedPacket.sequence}, " +
                                            "pcg=${parsedPacket.pcg.size}"
                                )
                            }
                        }

                        is ParsedBlePacket.Bio -> {
                            if (parsedPacket.ecg.size != 32 || parsedPacket.ppgIr.size != 32) {
                                Log.w(
                                    sourceTag,
                                    "Bio sample count abnormal: " +
                                            "seq=${parsedPacket.sequence}, " +
                                            "ecg=${parsedPacket.ecg.size}, " +
                                            "ppg=${parsedPacket.ppgIr.size}"
                                )
                            }
                        }
                    }

                    val frame = realtimeAssembler.push(parsedPacket)

                    if (frame != null) {
                        pushFrameToUi(
                            frame = frame,
                            sourceTag = sourceTag
                        )
                    } else if (packetCount % 100L == 0L) {
                        Log.d(
                            sourceTag,
                            "Waiting pair: " +
                                    "pendingAudio=${realtimeAssembler.pendingAudioCount()}, " +
                                    "pendingBio=${realtimeAssembler.pendingBioCount()}"
                        )
                    }
                }

                fun handleBleNotification(bytes: ByteArray) {
                    bleNotificationCount++

                    Log.d(
                        "BLE_RAW_TEST",
                        "Notification #$bleNotificationCount: ${bytes.size} bytes"
                    )

                    if (isPaused) {
                        return
                    }

                    val completePackets =
                        bleStreamAssembler.push(bytes)

                    if (completePackets.isEmpty()) {
                        Log.d(
                            "BLE_STREAM",
                            "Waiting more bytes, buffered=${bleStreamAssembler.bufferedSize()}"
                        )
                        return
                    }

                    if (!useBleMode) {
                        Log.d(
                            "BLE_PIPELINE",
                            "First valid BLE packet received -> switch SIMULATION to BLE REALTIME"
                        )

                        useBleMode = true
                        isPaused = false
                        clearRuntimeData()
                        bleStatusText = "BLE streaming: real packet received"
                    }

                    completePackets.forEach { packetBytes ->
                        val parsedPacket =
                            PacketParser.parse(packetBytes)

                        if (parsedPacket == null) {
                            parseErrorCount++
                            Log.e(
                                "BLE_PIPELINE",
                                "PacketParser failed: " +
                                        "size=${packetBytes.size}, " +
                                        "head=${packetBytes.toHexPreview()}"
                            )
                            return@forEach
                        }

                        Log.d(
                            "BLE_PIPELINE",
                            "Parsed packet: " +
                                    "type=${parsedPacket::class.simpleName}, " +
                                    "seq=${parsedPacket.sequence}, " +
                                    "size=${packetBytes.size}"
                        )

                        handleParsedPacket(
                            parsedPacket = parsedPacket,
                            sourceTag = "BLE_PIPELINE"
                        )
                    }
                }

                val bleManager = remember {
                    BleManager(
                        context = context,
                        onDataReceived = { bytes ->
                            mainHandler.post {
                                handleBleNotification(bytes)
                            }
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
                                            "BLE connected: requesting MTU"

                                        BleConnectionState.Ready ->
                                            "BLE ready: waiting packets"

                                        BleConnectionState.Disconnected -> {
                                            if (useBleMode) {
                                                Log.w(
                                                    "BLE_PIPELINE",
                                                    "BLE disconnected -> fallback to simulation"
                                                )
                                                useBleMode = false
                                            }
                                            "BLE disconnected"
                                        }

                                        is BleConnectionState.Error ->
                                            "BLE error: ${state.message}"
                                    }
                            }
                        }
                    )
                }

                DisposableEffect(Unit) {
                    onDispose {
                        bleManager.disconnect()
                    }
                }

                LaunchedEffect(Unit) {
                    var sequence = 0

                    while (true) {
                        if (!isPaused && !useBleMode) {
                            val audioBytes =
                                FakeBleSource.makeAudioPacket(sequence)

                            val bioBytes =
                                FakeBleSource.makeBioPacket(sequence)

                            val parsedAudio =
                                PacketParser.parse(audioBytes)

                            if (parsedAudio != null) {
                                handleParsedPacket(
                                    parsedPacket = parsedAudio,
                                    sourceTag = "REALTIME_TEST"
                                )
                            } else {
                                parseErrorCount++
                                Log.e(
                                    "REALTIME_TEST",
                                    "Audio parse failed at seq=$sequence"
                                )
                            }

                            val parsedBio =
                                PacketParser.parse(bioBytes)

                            if (parsedBio != null) {
                                handleParsedPacket(
                                    parsedPacket = parsedBio,
                                    sourceTag = "REALTIME_TEST"
                                )
                            } else {
                                parseErrorCount++
                                Log.e(
                                    "REALTIME_TEST",
                                    "Bio parse failed at seq=$sequence"
                                )
                            }

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

                            sequence = (sequence + 1) and 0xFF
                        }

                        delay(32L)
                    }
                }

                BioSignalDashboard(
                    ecg = ecgSamples,
                    ppg = ppgSamples,
                    pcg = pcgSamples,
                    sequence = currentSequence,
                    timestamp = currentTimestamp,
                    heartRateBpm = currentHeartRateBpm,
                    pttMs = currentPttMs,
                    analysisStatusText = analysisStatusText,
                    packetCount = packetCount,
                    parseErrorCount = parseErrorCount,
                    bleNotificationCount = bleNotificationCount,
                    ecgBufferSize = ecgBuffer.size(),
                    ppgBufferSize = ppgBuffer.size(),
                    pcgBufferSize = pcgBuffer.size(),
                    modeText = modeText,
                    modeColor = modeColor,
                    bleStatusText = bleStatusText,
                    isPaused = isPaused,
                    showStatistics = showStatistics,
                    onPauseToggle = {
                        isPaused = !isPaused
                    },
                    onReset = {
                        clearRuntimeData()
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
                            Log.d(
                                "BLE_PIPELINE",
                                "Connect BLE clicked -> startScan()"
                            )
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
    heartRateBpm: Double?,
    pttMs: Double?,
    analysisStatusText: String,
    packetCount: Long,
    parseErrorCount: Long,
    bleNotificationCount: Long,
    ecgBufferSize: Int,
    ppgBufferSize: Int,
    pcgBufferSize: Int,
    modeText: String,
    modeColor: Color,
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
                modeText = modeText,
                modeColor = modeColor,
                bleStatusText = bleStatusText
            )

            VitalSignsPanel(
                heartRateBpm = heartRateBpm,
                pttMs = pttMs,
                statusText = analysisStatusText
            )

            SignalCard(
                title = "ECG",
                subtitle = "Điện tim",
                sampleRate = "1000 Hz",
                samples = ecg,
                lineColor = EcgColor,
                modifier = Modifier.weight(1f)
            )

            SignalCard(
                title = "PPG",
                subtitle = "Mạch máu",
                sampleRate = "1000 Hz",
                samples = ppg,
                lineColor = PpgColor,
                modifier = Modifier.weight(1f)
            )

            SignalCard(
                title = "PCG",
                subtitle = "Âm tim",
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
            heartRateBpm = heartRateBpm,
            pttMs = pttMs,
            analysisStatusText = analysisStatusText,
            packetCount = packetCount,
            parseErrorCount = parseErrorCount,
            bleNotificationCount = bleNotificationCount,
            ecgBufferSize = ecgBufferSize,
            ppgBufferSize = ppgBufferSize,
            pcgBufferSize = pcgBufferSize,
            modeText = modeText,
            bleStatusText = bleStatusText,
            onDismiss = onCloseStatistics
        )
    }
}

@Composable
fun DashboardHeader(
    sequence: Int,
    timestamp: Long,
    modeText: String,
    modeColor: Color,
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
            text = "● $modeText | $bleStatusText",
            color = modeColor,
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
fun VitalSignsPanel(
    heartRateBpm: Double?,
    pttMs: Double?,
    statusText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.width(125.dp)
            ) {
                Text(
                    text = "Vital Signs",
                    color = PrimaryText,
                    fontSize = 17.sp
                )
                Text(
                    text = statusText,
                    color = SecondaryText,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "HR: ${formatHeartRate(heartRateBpm)}",
                color = EcgColor,
                fontSize = 18.sp
            )

            Text(
                text = "PTT: ${formatPtt(pttMs)}",
                color = PpgColor,
                fontSize = 18.sp
            )

            Text(
                text = "App analysis: ECG R-peak → PPG IR peak",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
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
    heartRateBpm: Double?,
    pttMs: Double?,
    analysisStatusText: String,
    packetCount: Long,
    parseErrorCount: Long,
    bleNotificationCount: Long,
    ecgBufferSize: Int,
    ppgBufferSize: Int,
    pcgBufferSize: Int,
    modeText: String,
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
                Text("Mode: $modeText")
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
                Text("Parsed packets: $packetCount")
                Text("BLE notifications: $bleNotificationCount")
                Text("Parse errors: $parseErrorCount")

                Text("")
                Text("=== Vital Signs ===")
                Text("HR: ${formatHeartRate(heartRateBpm)}")
                Text("PTT: ${formatPtt(pttMs)}")
                Text("Analysis: $analysisStatusText")

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

fun formatHeartRate(heartRateBpm: Double?): String {
    return heartRateBpm?.let { value ->
        "%.0f bpm".format(value)
    } ?: "-- bpm"
}

fun formatPtt(pttMs: Double?): String {
    return pttMs?.let { value ->
        "%.0f ms".format(value)
    } ?: "-- ms"
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
 * Gom stream BLE thành packet hoàn chỉnh.
 *
 * Dù ESP32 đang gửi được packet đủ 137/201 byte, Android vẫn nên có lớp này
 * để bắt lỗi nếu notification bị tách nhỏ, dính nhiều packet hoặc lệch header.
 */
private class BlePacketStreamAssembler {
    companion object {
        private const val TAG = "BLE_STREAM"
        private const val PKT_HEADER = 0xAA
        private const val PKT_VERSION = 0x01
        private const val PKT_FOOTER = 0x55
        private const val PKT_TYPE_AUDIO = 0x01
        private const val PKT_TYPE_BIO = 0x02
        private const val HEADER_SIZE = 6
        private const val FOOTER_SIZE = 3
        private const val AUDIO_PAYLOAD_SIZE = 128
        private const val BIO_PAYLOAD_SIZE = 192
    }

    private var buffer = ByteArray(0)

    fun clear() {
        buffer = ByteArray(0)
    }

    fun bufferedSize(): Int {
        return buffer.size
    }

    fun push(incoming: ByteArray): List<ByteArray> {
        if (incoming.isEmpty()) {
            return emptyList()
        }

        buffer += incoming

        val packets = mutableListOf<ByteArray>()

        while (true) {
            val headerIndex =
                buffer.indexOfFirst { byte ->
                    byte.toInt() and 0xFF == PKT_HEADER
                }

            if (headerIndex < 0) {
                Log.w(
                    TAG,
                    "Drop ${buffer.size} bytes: no header 0xAA"
                )
                buffer = ByteArray(0)
                break
            }

            if (headerIndex > 0) {
                Log.w(
                    TAG,
                    "Drop $headerIndex bytes before header"
                )
                buffer = buffer.copyOfRange(
                    headerIndex,
                    buffer.size
                )
            }

            if (buffer.size < HEADER_SIZE) {
                break
            }

            val version = buffer[1].toInt() and 0xFF
            val type = buffer[2].toInt() and 0xFF
            val sequence = buffer[3].toInt() and 0xFF
            val payloadLength =
                (buffer[4].toInt() and 0xFF) or
                        ((buffer[5].toInt() and 0xFF) shl 8)

            val expectedPayloadLength =
                when (type) {
                    PKT_TYPE_AUDIO -> AUDIO_PAYLOAD_SIZE
                    PKT_TYPE_BIO -> BIO_PAYLOAD_SIZE
                    else -> -1
                }

            if (version != PKT_VERSION || expectedPayloadLength < 0 || payloadLength != expectedPayloadLength) {
                Log.w(
                    TAG,
                    "Invalid header: " +
                            "version=$version, " +
                            "type=$type, " +
                            "seq=$sequence, " +
                            "payloadLength=$payloadLength, " +
                            "preview=${buffer.toHexPreview()}"
                )
                buffer = buffer.copyOfRange(
                    1,
                    buffer.size
                )
                continue
            }

            val totalSize =
                HEADER_SIZE + payloadLength + FOOTER_SIZE

            if (buffer.size < totalSize) {
                Log.d(
                    TAG,
                    "Incomplete packet: type=$type, seq=$sequence, " +
                            "need=$totalSize, have=${buffer.size}"
                )
                break
            }

            val footer =
                buffer[totalSize - 1].toInt() and 0xFF

            if (footer != PKT_FOOTER) {
                Log.w(
                    TAG,
                    "Invalid footer: type=$type, seq=$sequence, " +
                            "footer=0x${footer.toString(16)}, " +
                            "preview=${buffer.toHexPreview()}"
                )
                buffer = buffer.copyOfRange(
                    1,
                    buffer.size
                )
                continue
            }

            val packet =
                buffer.copyOfRange(
                    0,
                    totalSize
                )

            packets.add(packet)

            Log.d(
                TAG,
                "Complete packet extracted: type=$type, seq=$sequence, size=$totalSize"
            )

            buffer =
                if (buffer.size == totalSize) {
                    ByteArray(0)
                } else {
                    buffer.copyOfRange(
                        totalSize,
                        buffer.size
                    )
                }
        }

        return packets
    }
}

private fun ByteArray.toHexPreview(
    maxBytes: Int = 16
): String {
    return take(maxBytes)
        .joinToString(" ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
}

/**
 * Trả về danh sách quyền BLE cần xin theo từng phiên bản Android.
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
