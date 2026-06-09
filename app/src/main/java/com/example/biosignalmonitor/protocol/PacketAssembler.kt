/**
 * @file PacketAssembler.kt
 * @brief Ghép Audio packet và Bio packet cùng sequence thành BioSignalFrame.
 *
 * File này nhận dữ liệu đã được PacketParser giải mã dưới dạng:
 *
 * - ParsedBlePacket.Audio: chứa 32 mẫu PCG.
 * - ParsedBlePacket.Bio: chứa 32 mẫu PPG IR và 32 mẫu ECG.
 *
 * Vì dữ liệu của một block đồng bộ được chia thành hai packet riêng để
 * phù hợp với giới hạn BLE MTU, PacketAssembler lưu tạm từng packet và
 * chỉ tạo BioSignalFrame khi nhận đủ Audio và Bio có cùng sequence.
 *
 * PacketAssembler không trực tiếp nhận BLE, không giải mã ByteArray và
 * không vẽ waveform. Đầu ra của file này được đưa vào SignalRingBuffer.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * 10/6/2026
 * SPDX-License-Identifier: MIT
 */

package com.example.biosignalmonitor.protocol

/**
 * Ghép hai nửa Audio/Bio của cùng một block tín hiệu.
 */
class PacketAssembler {

    /**
     * Audio packet đang chờ Bio packet tương ứng.
     *
     * Key là sequence từ 0 đến 255.
     */
    private val pendingAudio =
        mutableMapOf<Int, ParsedBlePacket.Audio>()

    /**
     * Bio packet đang chờ Audio packet tương ứng.
     */
    private val pendingBio =
        mutableMapOf<Int, ParsedBlePacket.Bio>()

    var audioPacketsReceived: Long = 0L
        private set

    var bioPacketsReceived: Long = 0L
        private set

    var completedFrames: Long = 0L
        private set

    var incompleteFrames: Long = 0L
        private set

    /**
     * Nhận một packet đã parse.
     *
     * @param packet Audio hoặc Bio packet.
     *
     * @return BioSignalFrame nếu đã có đủ Audio và Bio cùng sequence.
     * Trả về null nếu vẫn đang chờ packet còn lại.
     */
    fun push(
        packet: ParsedBlePacket
    ): BioSignalFrame? {
        return when (packet) {
            is ParsedBlePacket.Audio -> {
                audioPacketsReceived++

                pendingAudio[packet.sequence] = packet

                tryBuildFrame(packet.sequence)
            }

            is ParsedBlePacket.Bio -> {
                bioPacketsReceived++

                pendingBio[packet.sequence] = packet

                tryBuildFrame(packet.sequence)
            }
        }
    }

    /**
     * Thử ghép Audio và Bio theo sequence.
     */
    private fun tryBuildFrame(
        sequence: Int
    ): BioSignalFrame? {
        val audio = pendingAudio[sequence]
        val bio = pendingBio[sequence]

        if (audio == null || bio == null) {
            cleanupIfNeeded()
            return null
        }

        val frame = BioSignalFrame(
            sequence = sequence,
            ecg = bio.ecg,
            ppgIr = bio.ppgIr,
            pcg = audio.pcg
        )

        pendingAudio.remove(sequence)
        pendingBio.remove(sequence)

        if (!frame.isValid()) {
            incompleteFrames++
            return null
        }

        completedFrames++

        return frame
    }

    /**
     * Giới hạn số packet chờ để tránh bộ nhớ tăng vô hạn
     * khi một nửa packet bị mất.
     */
    private fun cleanupIfNeeded() {
        val maxPendingPackets = 16

        while (pendingAudio.size > maxPendingPackets) {
            val oldestKey = pendingAudio.keys.firstOrNull()
                ?: break

            pendingAudio.remove(oldestKey)
            incompleteFrames++
        }

        while (pendingBio.size > maxPendingPackets) {
            val oldestKey = pendingBio.keys.firstOrNull()
                ?: break

            pendingBio.remove(oldestKey)
            incompleteFrames++
        }
    }

    /**
     * Xóa toàn bộ packet đang chờ và reset các bộ đếm.
     */
    fun clear() {
        pendingAudio.clear()
        pendingBio.clear()

        audioPacketsReceived = 0L
        bioPacketsReceived = 0L
        completedFrames = 0L
        incompleteFrames = 0L
    }

    /**
     * Số Audio packet đang chờ ghép.
     */
    fun pendingAudioCount(): Int {
        return pendingAudio.size
    }

    /**
     * Số Bio packet đang chờ ghép.
     */
    fun pendingBioCount(): Int {
        return pendingBio.size
    }
}