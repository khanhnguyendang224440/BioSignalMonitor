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

import android.os.Bundle
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
import com.example.biosignalmonitor.fake.FakeBleSource
import com.example.biosignalmonitor.signal.SignalRingBuffer
import com.example.biosignalmonitor.ui.theme.BioSignalMonitorTheme
import com.example.biosignalmonitor.protocol.PacketParser
import com.example.biosignalmonitor.protocol.ParsedBlePacket

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

        val audioBytes =
            FakeBleSource.makeAudioPacket(sequence = 10)

        val bioBytes =
            FakeBleSource.makeBioPacket(sequence = 10)

        val parsedAudio =
            PacketParser.parse(audioBytes)

        val parsedBio =
            PacketParser.parse(bioBytes)

        Log.d(
            "PARSER_TEST",
            "Audio result = $parsedAudio"
        )

        Log.d(
            "PARSER_TEST",
            "Bio result = $parsedBio"
        )

        if (parsedAudio is ParsedBlePacket.Audio) {
            Log.d(
                "PARSER_TEST",
                "Audio seq=${parsedAudio.sequence}, " +
                        "PCG count=${parsedAudio.pcg.size}"
            )
        } else {
            Log.e(
                "PARSER_TEST",
                "Audio packet parse failed"
            )
        }

        if (parsedBio is ParsedBlePacket.Bio) {
            Log.d(
                "PARSER_TEST",
                "Bio seq=${parsedBio.sequence}, " +
                        "PPG count=${parsedBio.ppgIr.size}, " +
                        "ECG count=${parsedBio.ecg.size}"
            )
        } else {
            Log.e(
                "PARSER_TEST",
                "Bio packet parse failed"
            )
        }

//        val audioBytes = FakeBleSource.makeAudioPacket(sequence = 1)
//        val bioBytes = FakeBleSource.makeBioPacket(sequence = 1)
//
//        Log.d("PACKET_TEST", "audio size = ${audioBytes.size}")
//        Log.d("PACKET_TEST", "bio size = ${bioBytes.size}")
//        Log.d("PACKET_TEST", "audio first byte = 0x%02X".format(audioBytes[0]))
//        Log.d("PACKET_TEST", "bio first byte = 0x%02X".format(bioBytes[0]))
//        Log.d("PACKET_TEST", "audio footer = 0x%02X".format(audioBytes.last()))
//        Log.d("PACKET_TEST", "bio footer = 0x%02X".format(bioBytes.last()))

        setContent {
            BioSignalMonitorTheme {
                val ecgBuffer = remember {
                    SignalRingBuffer(capacity = 500)
                }

                val ppgBuffer = remember {
                    SignalRingBuffer(capacity = 500)
                }

                val pcgBuffer = remember {
                    SignalRingBuffer(capacity = 500)
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
                    Log.d("REALTIME_TEST", "Realtime loop paused because packet format is being updated")
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
                    isPaused = isPaused,
                    showStatistics = showStatistics,
                    onPauseToggle = {
                        isPaused = !isPaused
                    },
                    onReset = {
                        ecgBuffer.clear()
                        ppgBuffer.clear()
                        pcgBuffer.clear()

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
    isPaused: Boolean,
    showStatistics: Boolean,
    onPauseToggle: () -> Unit,
    onReset: () -> Unit,
    onShowStatistics: () -> Unit,
    onCloseStatistics: () -> Unit
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
                timestamp = timestamp
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
                onShowStatistics = onShowStatistics
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
            onDismiss = onCloseStatistics
        )
    }
}

@Composable
fun DashboardHeader(
    sequence: Int,
    timestamp: Long
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
            text = "● SIMULATION",
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
                    text = "${samples.size}/500 samples",
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
    onShowStatistics: () -> Unit
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
            onClick = {},
            enabled = false
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
                Text("BLE status: Not connected")

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
                Text("ECG buffer: $ecgBufferSize / 500")
                Text("PPG buffer: $ppgBufferSize / 500")
                Text("PCG buffer: $pcgBufferSize / 500")
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