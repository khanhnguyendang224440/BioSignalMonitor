/**
 * @file WaveformCanvas.kt
 * @brief Vẽ waveform của các tín hiệu ECG, PPG và PCG trên giao diện.
 *
 * WaveformCanvas chỉ xử lý tỉ lệ hiển thị. Bộ lọc tín hiệu realtime được đặt
 * ở AppSignalFilters/VitalSignsAnalyzer, không đặt trong lớp vẽ Canvas.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 9/6/2026
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

@Composable
fun WaveformCanvas(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Green,
    displayGain: Float = 1.0f,
    clipStd: Float = 3.5f
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

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

        drawLine(
            color = Color(0xFF3A4A60),
            start = Offset(0f, canvasHeight / 2f),
            end = Offset(canvasWidth, canvasHeight / 2f),
            strokeWidth = 1.5f
        )

        if (samples.size < 2) return@Canvas

        val mean = samples.sum() / samples.size.toFloat()

        var variance = 0f
        for (value in samples) {
            val diff = value - mean
            variance += diff * diff
        }
        variance /= samples.size.toFloat()

        val std = sqrt(variance)
        val displayLimit = max(std * clipStd, 1f)

        val topPadding = canvasHeight * 0.1f
        val usableHeight = canvasHeight * 0.8f
        val halfHeight = usableHeight / 2f
        val centerY = topPadding + halfHeight

        val path = Path()

        samples.forEachIndexed { index, value ->
            val x = index.toFloat() /
                    (samples.size - 1).toFloat() *
                    canvasWidth

            val normalized = (value - mean) / displayLimit * displayGain

            // Dùng soft clipping để tránh đỉnh ECG bị cắt phẳng thành vuông.
            val safeNormalized = tanh(normalized.toDouble()).toFloat()
            val y = centerY - safeNormalized * halfHeight

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
