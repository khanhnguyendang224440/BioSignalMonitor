/**
 * @file WaveformCanvas.kt
 * @brief Vẽ waveform ECG, PPG và PCG theo kiểu sweep display.
 *
 * Kiểu sweep display giống monitor bệnh viện: con trỏ quét chạy từ trái
 * sang phải; dữ liệu mới được vẽ tại vị trí con trỏ, hết khung thì quay
 * lại đầu và ghi đè lên dữ liệu cũ. Canvas chỉ xử lý tỉ lệ hiển thị và
 * vị trí vẽ; bộ lọc tín hiệu realtime được đặt ở AppSignalFilters/
 * VitalSignsAnalyzer, không đặt trong lớp vẽ Canvas.
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
    sweepSampleIndex: Long,
    displayCapacity: Int,
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

        if (samples.size < 2 || displayCapacity < 2) return@Canvas

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

        fun positiveModulo(value: Long, modulo: Int): Int {
            val result = value % modulo
            return if (result < 0) {
                (result + modulo).toInt()
            } else {
                result.toInt()
            }
        }

        fun pointForSample(
            localIndex: Int,
            value: Float
        ): Pair<Int, Offset> {
            // sweepSampleIndex là chỉ số mẫu kế tiếp sẽ được ghi.
            // Vì samples đang là snapshot theo thứ tự thời gian cũ → mới,
            // mẫu đầu tiên có chỉ số tuyệt đối là sweepSampleIndex - samples.size.
            val firstAbsoluteSampleIndex = sweepSampleIndex - samples.size.toLong()
            val absoluteSampleIndex = firstAbsoluteSampleIndex + localIndex.toLong()
            val screenIndex = positiveModulo(absoluteSampleIndex, displayCapacity)

            val x = screenIndex.toFloat() /
                    (displayCapacity - 1).toFloat() *
                    canvasWidth

            val normalized = (value - mean) / displayLimit * displayGain

            // Dùng soft clipping để tránh đỉnh ECG bị cắt phẳng thành vuông.
            val safeNormalized = tanh(normalized.toDouble()).toFloat()
            val y = centerY - safeNormalized * halfHeight

            return screenIndex to Offset(x, y)
        }

        val paths = mutableListOf<Path>()
        var currentPath = Path()
        var hasCurrentPath = false
        var previousScreenIndex: Int? = null

        samples.forEachIndexed { index, value ->
            val (screenIndex, point) = pointForSample(index, value)

            // Khi con trỏ sweep quay từ cuối khung về đầu khung, không nối
            // đoạn cuối màn hình với đoạn đầu màn hình để tránh đường chéo giả.
            if (previousScreenIndex != null && screenIndex < previousScreenIndex!!) {
                if (hasCurrentPath) {
                    paths.add(currentPath)
                }
                currentPath = Path()
                currentPath.moveTo(point.x, point.y)
                hasCurrentPath = true
            } else {
                if (!hasCurrentPath) {
                    currentPath.moveTo(point.x, point.y)
                    hasCurrentPath = true
                } else {
                    currentPath.lineTo(point.x, point.y)
                }
            }

            previousScreenIndex = screenIndex
        }

        if (hasCurrentPath) {
            paths.add(currentPath)
        }

        for (path in paths) {
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5f)
            )
        }

        // Vạch quét hiện tại: vị trí mẫu kế tiếp sẽ được vẽ/ghi đè.
        val sweepScreenIndex = positiveModulo(sweepSampleIndex, displayCapacity)
        val sweepX = sweepScreenIndex.toFloat() /
                (displayCapacity - 1).toFloat() *
                canvasWidth

        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(sweepX, 0f),
            end = Offset(sweepX, canvasHeight),
            strokeWidth = 2f
        )
    }
}
