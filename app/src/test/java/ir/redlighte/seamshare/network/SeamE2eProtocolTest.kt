package ir.redlighte.seamshare.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SeamE2eProtocolTest {
    @Test
    fun ciphertextSizeHandlesZeroAndChunkBoundaries() {
        assertEquals(16L, SeamE2eProtocol.ciphertextSize(0))
        assertEquals(17L, SeamE2eProtocol.ciphertextSize(1))
        assertEquals(256L * 1024L + 16L, SeamE2eProtocol.ciphertextSize(256L * 1024L))
        assertEquals(256L * 1024L + 33L, SeamE2eProtocol.ciphertextSize(256L * 1024L + 1L))
    }

    @Test
    fun nonceIsUniquePerChunk() {
        val prefix = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        assertEquals("102030400000000000000000", prefixNonce(prefix, 0))
        assertEquals("102030400000000000000001", prefixNonce(prefix, 1))
        assertEquals("102030400000000000000002", prefixNonce(prefix, 2))
    }

    private fun prefixNonce(prefix: ByteArray, index: Long): String =
        SeamE2eProtocol.nonce(prefix, index).joinToString("") { "%02x".format(it) }
}
