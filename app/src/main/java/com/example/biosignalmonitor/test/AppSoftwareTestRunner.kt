/**
 * @file AppSoftwareTestRunner.kt
 * @brief Chạy từng test case phần mềm ngay trên app và in log riêng ra Logcat.
 *
 * Cách dùng:
 * - Chạy app trên điện thoại.
 * - Chọn TC01..TC07 trên giao diện.
 * - Bấm "Run TC" để chạy đúng một test case.
 * - Mở Logcat/Terminal và lọc theo tag: APP_TEST.
 *
 * Mục tiêu: mỗi test case tạo được một cặp ảnh minh chứng:
 * 1) ảnh app đang chạy/chọn test case,
 * 2) ảnh Logcat có log START/PASS/FAIL của đúng test case đó.
 *
 * Copyright (c) 2026 Nguyen Dang Khanh
 * SPDX-License-Identifier: MIT
 */
package com.example.biosignalmonitor.test

import android.util.Log
import com.example.biosignalmonitor.analysis.IndexedSample
import com.example.biosignalmonitor.analysis.PpgFootDetector
import com.example.biosignalmonitor.analysis.RPeakDetector
import com.example.biosignalmonitor.fake.FakeBleSource
import com.example.biosignalmonitor.protocol.BioSignalFrame
import com.example.biosignalmonitor.protocol.PacketAssembler
import com.example.biosignalmonitor.protocol.PacketParser
import com.example.biosignalmonitor.protocol.ParsedBlePacket
import com.example.biosignalmonitor.signal.SignalRingBuffer
import kotlin.math.abs

object AppSoftwareTestRunner {
    private const val TAG = "APP_TEST"

    data class TestCase(
        val id: String,
        val title: String,
        val purpose: String,
        val expected: String
    ) {
        val label: String
            get() = "$id - $title"
    }

    data class TestResult(
        val testCase: TestCase,
        val passed: Boolean,
        val detail: String
    ) {
        val statusText: String
            get() = if (passed) {
                "${testCase.id}: PASS - ${testCase.title}"
            } else {
                "${testCase.id}: FAIL - ${testCase.title}"
            }
    }

    val testCases: List<TestCase> = listOf(
        TestCase(
            id = "TC01",
            title = "Parse valid fake packets",
            purpose = "Kiểm tra FakeBleSource tạo packet Audio/Bio đúng định dạng và PacketParser đọc được packet hợp lệ.",
            expected = "Audio/Bio parse thành công, đúng sequence, đúng số mẫu, CRC hợp lệ."
        ),
        TestCase(
            id = "TC02",
            title = "Reject CRC mismatch",
            purpose = "Cố ý sửa một byte payload để tạo lỗi CRC.",
            expected = "PacketParser trả về null và lastError = CRC_MISMATCH."
        ),
        TestCase(
            id = "TC03",
            title = "Assembler missing pair error",
            purpose = "Cố ý tạo các packet Audio thiếu packet Bio ghép đôi để kiểm tra lỗi incomplete frame.",
            expected = "PacketAssembler không tạo BioSignalFrame, pending được dọn và incompleteFrames tăng."
        ),
        TestCase(
            id = "TC04",
            title = "RingBuffer overwrite",
            purpose = "Kiểm tra RingBuffer khi đầy sẽ ghi đè mẫu cũ nhất.",
            expected = "Snapshot còn đúng các mẫu mới nhất và đúng thứ tự thời gian."
        ),
        TestCase(
            id = "TC05",
            title = "R-peak detector",
            purpose = "Kiểm tra thuật toán phát hiện R-peak với tín hiệu ECG giả lập.",
            expected = "Phát hiện đúng các đỉnh R giả lập tại vị trí mong muốn."
        ),
        TestCase(
            id = "TC06",
            title = "PPG foot detector",
            purpose = "Kiểm tra thuật toán phát hiện chân sóng PPG với tín hiệu giả lập.",
            expected = "Phát hiện đúng các PPG foot giả lập tại vị trí mong muốn."
        ),
        TestCase(
            id = "TC07",
            title = "Assembler pending cleanup",
            purpose = "Kiểm tra PacketAssembler không để packet chờ ghép tăng vô hạn.",
            expected = "Số pending packet được giới hạn và incompleteFrames tăng khi thiếu packet ghép đôi."
        )
    )

    fun run(testCaseId: String): TestResult {
        val testCase = testCases.firstOrNull { it.id == testCaseId }
            ?: testCases.first()

        Log.i(TAG, "")
        Log.i(TAG, "================ ${testCase.id} START ================")
        Log.i(TAG, "NAME    : ${testCase.title}")
        Log.i(TAG, "PURPOSE : ${testCase.purpose}")
        Log.i(TAG, "EXPECTED: ${testCase.expected}")
        Log.i(TAG, "UI NOTE : Take screenshot of app now, then take screenshot of this Logcat block.")

        val passed = try {
            when (testCase.id) {
                "TC01" -> testParseValidFakePackets()
                "TC02" -> testRejectCrcMismatch()
                "TC03" -> testAssembleAudioBioFrame()
                "TC04" -> testRingBufferOverwrite()
                "TC05" -> testRPeakDetectorBasic()
                "TC06" -> testPpgFootDetectorBasic()
                "TC07" -> testAssemblerPendingCleanup()
                else -> false
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "${testCase.id} EXCEPTION: ${throwable.message}", throwable)
            false
        }

        val detail = if (passed) {
            "PASS"
        } else {
            "FAIL"
        }

        Log.i(TAG, "RESULT  : ${testCase.id} $detail")
        Log.i(TAG, "================= ${testCase.id} END =================")
        Log.i(TAG, "")

        return TestResult(
            testCase = testCase,
            passed = passed,
            detail = detail
        )
    }

    fun runAll() {
        Log.i(TAG, "================ SOFTWARE TEST START ================")
        var passCount = 0
        testCases.forEach { testCase ->
            val result = run(testCase.id)
            if (result.passed) {
                passCount++
            }
        }
        val failCount = testCases.size - passCount
        Log.i(TAG, "SUMMARY: pass=$passCount, fail=$failCount, total=${testCases.size}")
        Log.i(TAG, "================= SOFTWARE TEST END =================")
    }

    private fun testParseValidFakePackets(): Boolean {
        val audioBytes = FakeBleSource.makeAudioPacket(sequence = 7)
        val bioBytes = FakeBleSource.makeBioPacket(sequence = 7)

        val audioPacket = PacketParser.parse(audioBytes)
        val audioError = PacketParser.lastError
        val bioPacket = PacketParser.parse(bioBytes)
        val bioError = PacketParser.lastError

        val audioOk = audioPacket is ParsedBlePacket.Audio &&
                audioPacket.sequence == 7 &&
                audioPacket.pcg.size == 32 &&
                audioBytes.size == PacketParser.AUDIO_PACKET_SIZE

        val bioOk = bioPacket is ParsedBlePacket.Bio &&
                bioPacket.sequence == 7 &&
                bioPacket.ecg.size == 32 &&
                bioPacket.ppgIr.size == 32 &&
                bioBytes.size == PacketParser.BIO_PACKET_SIZE

        Log.i(
            TAG,
            "TC01 audioOk=$audioOk, audioError=$audioError, " +
                    "audioSize=${audioBytes.size}, expectedAudio=${PacketParser.AUDIO_PACKET_SIZE}"
        )
        Log.i(
            TAG,
            "TC01 bioOk=$bioOk, bioError=$bioError, " +
                    "bioSize=${bioBytes.size}, expectedBio=${PacketParser.BIO_PACKET_SIZE}"
        )

        return audioOk && bioOk
    }

    private fun testRejectCrcMismatch(): Boolean {
        val bytes = FakeBleSource.makeBioPacket(sequence = 8)

        // Đảo 1 bit trong payload, không đụng Header/Footer.
        bytes[10] = (bytes[10].toInt() xor 0x01).toByte()

        val parsed = PacketParser.parse(bytes)
        val error = PacketParser.lastError

        Log.i(
            TAG,
            "TC02 parsed=$parsed, parserError=$error"
        )

        return parsed == null && error == PacketParser.ParseError.CRC_MISMATCH
    }

    private fun testAssembleAudioBioFrame(): Boolean {
        val assembler = PacketAssembler()

        /*
         * TC03 cố ý không tạo cặp Audio/Bio hợp lệ.
         * Các packet Audio vẫn đúng CRC và parser vẫn đọc được,
         * nhưng bị thiếu Bio cùng sequence nên không được tạo BioSignalFrame.
         * Khi số packet chờ vượt giới hạn, PacketAssembler phải dọn pending
         * và tăng incompleteFrames.
         */
        var createdFrame: BioSignalFrame? = null

        for (sequence in 30 until 50) {
            val audioPacket = PacketParser.parse(
                FakeBleSource.makeAudioPacket(sequence = sequence)
            ) as? ParsedBlePacket.Audio ?: return false

            val frame = assembler.push(audioPacket)
            if (frame != null) {
                createdFrame = frame
            }
        }

        val ok = createdFrame == null &&
                assembler.completedFrames == 0L &&
                assembler.audioPacketsReceived == 20L &&
                assembler.bioPacketsReceived == 0L &&
                assembler.incompleteFrames > 0 &&
                assembler.pendingAudioCount() <= 16 &&
                assembler.pendingBioCount() == 0

        Log.i(
            TAG,
            "TC03 intentionallyMissingBio=true, " +
                    "frameCreated=${createdFrame != null}, " +
                    "completed=${assembler.completedFrames}, " +
                    "incomplete=${assembler.incompleteFrames}, " +
                    "audioRx=${assembler.audioPacketsReceived}, " +
                    "bioRx=${assembler.bioPacketsReceived}, " +
                    "pendingAudio=${assembler.pendingAudioCount()}, " +
                    "pendingBio=${assembler.pendingBioCount()}"
        )

        return ok
    }

    private fun testRingBufferOverwrite(): Boolean {
        val ringBuffer = SignalRingBuffer(capacity = 5)
        ringBuffer.pushSamples(
            floatArrayOf(
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f
            )
        )

        val snapshot = ringBuffer.snapshot()
        val expected = floatArrayOf(4f, 5f, 6f, 7f, 8f)
        val ok = snapshot.contentEqualsWithTolerance(expected)

        Log.i(
            TAG,
            "TC04 snapshot=${snapshot.joinToString()}, " +
                    "expected=${expected.joinToString()}, size=${ringBuffer.size()}"
        )

        return ok && ringBuffer.size() == 5
    }

    private fun testRPeakDetectorBasic(): Boolean {
        /*
         * TC05:
         * Tạo một dãy ECG mô phỏng dài 900 mẫu.
         * Có 3 đỉnh rõ:
         * - Đỉnh tại sample 200: hợp lệ, phải được nhận là R-peak.
         * - Đỉnh tại sample 380: rõ nhưng cách đỉnh trước 180 ms < 250 ms, phải bị loại.
         * - Đỉnh tại sample 650: cách đỉnh hợp lệ trước 450 ms, phải được nhận.
         */
        val detector = RPeakDetector(sampleRateHz = 1000)
        detector.reset()

        val ecgSamples = MutableList(900) { index ->
            // Nền ECG giả lập dao động nhẹ để tín hiệu không phải đường thẳng tuyệt đối.
            0.2 * kotlin.math.sin(2.0 * Math.PI * index / 120.0)
        }

        fun addSharpPeak(center: Int, amplitude: Double) {
            ecgSamples[center - 3] = 0.3 * amplitude
            ecgSamples[center - 2] = 0.7 * amplitude
            ecgSamples[center - 1] = 1.4 * amplitude
            ecgSamples[center] = 3.0 * amplitude
            ecgSamples[center + 1] = 1.2 * amplitude
            ecgSamples[center + 2] = 0.6 * amplitude
            ecgSamples[center + 3] = 0.2 * amplitude
        }

        val firstValidPeak = 200L
        val tooClosePeak = 380L
        val secondValidPeak = 650L

        addSharpPeak(firstValidPeak.toInt(), amplitude = 1.2)
        addSharpPeak(tooClosePeak.toInt(), amplitude = 1.1)
        addSharpPeak(secondValidPeak.toInt(), amplitude = 1.25)

        val indexedSamples = ecgSamples.mapIndexed { index, value ->
            IndexedSample(
                index = index.toLong(),
                value = value
            )
        }

        val detectedPeaks = detector.detectNew(indexedSamples)
        val expectedPeaks = listOf(firstValidPeak, secondValidPeak)

        val detectedExpected = detectedPeaks == expectedPeaks
        val rejectedTooClose = tooClosePeak !in detectedPeaks

        Log.i(
            TAG,
            "TC05 inputLength=${ecgSamples.size}, " +
                    "candidatePeaks=[$firstValidPeak, $tooClosePeak, $secondValidPeak], " +
                    "refractoryMs=250, " +
                    "diffFirstToTooClose=${tooClosePeak - firstValidPeak}ms, " +
                    "diffFirstToSecond=${secondValidPeak - firstValidPeak}ms, " +
                    "detectedPeaks=$detectedPeaks, " +
                    "expected=$expectedPeaks, " +
                    "rejectedTooClose=$rejectedTooClose"
        )

        return detectedExpected && rejectedTooClose
    }

    private fun testPpgFootDetectorBasic(): Boolean {
        /*
         * TC06:
         * Tạo một dãy PPG mô phỏng dài 1100 mẫu.
         * Có 3 chân sóng ứng viên:
         * - Foot tại sample 250: hợp lệ, phải được nhận.
         * - Foot tại sample 480: cách foot trước 230 ms < 300 ms, phải bị loại.
         * - Foot tại sample 850: cách foot hợp lệ trước 600 ms, phải được nhận.
         */
        val detector = PpgFootDetector(sampleRateHz = 1000)
        detector.reset()

        val ppgSamples = MutableList(1100) { index ->
            // Nền PPG giả lập dao động nhẹ quanh mức 1000.
            1000.0 + 25.0 * kotlin.math.sin(2.0 * Math.PI * index / 180.0)
        }

        fun addFootWithRisingSlope(center: Int) {
            // Tạo vùng đáy cục bộ và sườn lên sau foot.
            ppgSamples[center - 4] = 940.0
            ppgSamples[center - 3] = 890.0
            ppgSamples[center - 2] = 820.0
            ppgSamples[center - 1] = 720.0
            ppgSamples[center] = 600.0
            ppgSamples[center + 1] = 700.0
            ppgSamples[center + 2] = 830.0
            ppgSamples[center + 3] = 950.0
            ppgSamples[center + 4] = 1080.0
        }

        val firstValidFoot = 250L
        val tooCloseFoot = 480L
        val secondValidFoot = 850L

        addFootWithRisingSlope(firstValidFoot.toInt())
        addFootWithRisingSlope(tooCloseFoot.toInt())
        addFootWithRisingSlope(secondValidFoot.toInt())

        val indexedSamples = ppgSamples.mapIndexed { index, value ->
            IndexedSample(
                index = index.toLong(),
                value = value
            )
        }

        val detectedFeet = detector.detectNew(indexedSamples)
        val expectedFeet = listOf(firstValidFoot, secondValidFoot)

        val detectedExpected = detectedFeet == expectedFeet
        val rejectedTooClose = tooCloseFoot !in detectedFeet

        Log.i(
            TAG,
            "TC06 inputLength=${ppgSamples.size}, " +
                    "candidateFeet=[$firstValidFoot, $tooCloseFoot, $secondValidFoot], " +
                    "refractoryMs=300, " +
                    "diffFirstToTooClose=${tooCloseFoot - firstValidFoot}ms, " +
                    "diffFirstToSecond=${secondValidFoot - firstValidFoot}ms, " +
                    "detectedFeet=$detectedFeet, " +
                    "expected=$expectedFeet, " +
                    "rejectedTooClose=$rejectedTooClose"
        )

        return detectedExpected && rejectedTooClose
    }

    private fun testAssemblerPendingCleanup(): Boolean {
        val assembler = PacketAssembler()

        for (sequence in 0 until 20) {
            val audioPacket = PacketParser.parse(
                FakeBleSource.makeAudioPacket(sequence = sequence)
            ) as? ParsedBlePacket.Audio ?: return false

            assembler.push(audioPacket)
        }

        val ok = assembler.pendingAudioCount() <= 16 &&
                assembler.pendingBioCount() == 0 &&
                assembler.incompleteFrames > 0

        Log.i(
            TAG,
            "TC07 pendingAudio=${assembler.pendingAudioCount()}, " +
                    "pendingBio=${assembler.pendingBioCount()}, " +
                    "incomplete=${assembler.incompleteFrames}"
        )

        return ok
    }

    private fun FloatArray.contentEqualsWithTolerance(
        expected: FloatArray,
        tolerance: Float = 0.0001f
    ): Boolean {
        if (size != expected.size) return false

        for (index in indices) {
            if (abs(this[index] - expected[index]) > tolerance) {
                return false
            }
        }

        return true
    }
}
