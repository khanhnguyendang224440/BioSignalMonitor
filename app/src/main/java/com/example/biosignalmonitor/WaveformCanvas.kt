package com.example.biosignalmonitor

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun WaveformCanvas(
    samples: FloatArray,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxWidth()
    ) {
        if (samples.size < 2) {
            return@Canvas
        }

        val minValue = samples.minOrNull() ?: 0f
        val maxValue = samples.maxOrNull() ?: 1f
        val range = (maxValue - minValue).takeIf { it != 0f } ?: 1f

        val path = Path()

        samples.forEachIndexed { index, value ->
            val x = index.toFloat() / (samples.size - 1) * size.width
            val normalized = (value - minValue) / range
            val y = size.height - normalized * size.height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color.Green,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}