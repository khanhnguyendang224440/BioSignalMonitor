//định nghĩa 1 packet dữ liệu sau khi parse xong


package com.example.biosignalmonitor.protocol

data class BioPacket(
    val sequence: Int,
    val timestamp: Long,
    val count: Int,
    val ecg: ShortArray,
    val ppg: ShortArray,
    val pcg: ShortArray
)
