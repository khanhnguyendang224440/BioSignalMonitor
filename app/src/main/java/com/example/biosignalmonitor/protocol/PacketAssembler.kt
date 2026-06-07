package com.example.biosignalmonitor.protocol

class PacketAssembler {

    fun pushChunk(chunk: ByteArray): List<BioPacket> {
        val packet = PacketParser.parse(chunk)

        return if (packet != null) {
            listOf(packet)
        } else {
            emptyList()
        }
    }
}