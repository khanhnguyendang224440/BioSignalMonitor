package com.example.biosignalmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.biosignalmonitor.ui.theme.BioSignalMonitorTheme
import kotlin.math.sin
import android.util.Log
import com.example.biosignalmonitor.fake.FakeBleSource
import com.example.biosignalmonitor.protocol.PacketParser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val testBytes = FakeBleSource.makePacket(sequence = 1)
        val testPacket = PacketParser.parse(testBytes)

        Log.d("PACKET_TEST", "packet = $testPacket")
        Log.d("PACKET_TEST", "ecg count = ${testPacket?.ecg?.size}")
        Log.d("PACKET_TEST", "ppg count = ${testPacket?.ppg?.size}")
        Log.d("PACKET_TEST", "pcg count = ${testPacket?.pcg?.size}")

        setContent {
            BioSignalMonitorTheme {

                val ecgFake = remember {
                    FloatArray(500) { i ->
                        val t = i / 30f
                        (sin(t) * 1000f + sin(t * 3f) * 200f)
                    }
                }

                val ppgFake = remember {
                    FloatArray(500) { i ->
                        val t = i / 50f
                        sin(t) * 1000f
                    }
                }

                val pcgFake = remember {
                    FloatArray(500) { i ->
                        val t = i / 8f
                        (sin(t) * 800f + sin(t * 8f) * 250f)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "BioSignalMonitor",
                        fontSize = 26.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    SignalRow(
                        title = "ECG",
                        samples = ecgFake,
                        modifier = Modifier.weight(1f)
                    )

                    SignalRow(
                        title = "PPG",
                        samples = ppgFake,
                        modifier = Modifier.weight(1f)
                    )

                    SignalRow(
                        title = "PCG",
                        samples = pcgFake,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SignalRow(
    title: String,
    samples: FloatArray,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        WaveformCanvas(
            samples = samples,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}