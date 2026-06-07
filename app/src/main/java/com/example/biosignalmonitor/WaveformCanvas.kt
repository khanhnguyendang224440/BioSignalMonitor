package com.example.biosignalmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun WaveformCanvas(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Green
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Vẽ lưới dọc.
        val verticalGridCount = 10
        for (i in 1 until verticalGridCount) {
            val x = canvasWidth * i / verticalGridCount

            drawLine(
                color = Color(0xFF263449),
                start = Offset(x, 0f),
                end = Offset(x, canvasHeight),
                strokeWidth = 1f
            )
        }

        // Vẽ lưới ngang.
        val horizontalGridCount = 4
        for (i in 1 until horizontalGridCount) {
            val y = canvasHeight * i / horizontalGridCount

            drawLine(
                color = Color(0xFF263449),
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = 1f
            )
        }

        // Đường baseline giữa màn hình.
        drawLine(
            color = Color(0xFF3A4A60),
            start = Offset(0f, canvasHeight / 2f),
            end = Offset(canvasWidth, canvasHeight / 2f),
            strokeWidth = 1.5f
        )

        if (samples.size < 2) {
            return@Canvas
        }

        val minValue = samples.minOrNull() ?: return@Canvas
        val maxValue = samples.maxOrNull() ?: return@Canvas
        val range = maxValue - minValue

        if (range == 0f) {
            return@Canvas
        }

        val topPadding = canvasHeight * 0.1f
        val usableHeight = canvasHeight * 0.8f

        val path = Path()

        samples.forEachIndexed { index, value ->
            val x = index.toFloat() /
                    (samples.size - 1).toFloat() *
                    canvasWidth

            val normalizedValue = (value - minValue) / range

            val y = topPadding +
                    usableHeight -
                    normalizedValue * usableHeight

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5f)
        )
    }
}