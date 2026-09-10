package ir.redlighte.seamshare.network

import java.nio.ByteBuffer

/** Cross-platform framing for SEAM-Share-E2E-v1 file chunks. */
object SeamE2eProtocol {
    const val VERSION = 1
    const val PLAINTEXT_CHUNK = 256 * 1024
    const val TAG_SIZE = 16
    const val NONCE_PREFIX_SIZE = 4
    const val NONCE_SIZE = 12

    data class Frame(val index: Long, val ciphertext: ByteArray)

    fun ciphertextSize(plaintextSize: Long): Long {
        require(plaintextSize >= 0)
        if (plaintextSize == 0L) return TAG_SIZE.toLong()
        val chunks = (plaintextSize + PLAINTEXT_CHUNK - 1) / PLAINTEXT_CHUNK
        return plaintextSize + chunks * TAG_SIZE
    }

    fun aad(transferId: String, index: Long, plaintextLength: Int): ByteArray =
        ByteBuffer.allocate(4 + 8 + 4 + transferId.toByteArray(Charsets.UTF_8).size)
            .putInt(VERSION)
            .putLong(index)
            .putInt(plaintextLength)
            .put(transferId.toByteArray(Charsets.UTF_8))
            .array()
}
