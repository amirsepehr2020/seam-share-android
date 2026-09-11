package ir.redlighte.seamshare.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeamE2eCryptoTest {
    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    @Test
    fun crossPlatformVectorMatches() {
        val senderPrivate = ByteArray(32) { (it + 1).toByte() }
        val receiverPrivate = ByteArray(32) { (it + 101).toByte() }
        val sender = SeamE2eCrypto.generateKeyPairForTest(senderPrivate)
        val receiver = SeamE2eCrypto.generateKeyPairForTest(receiverPrivate)

        assertArrayEquals(hex("07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c"), sender.publicKey)
        assertArrayEquals(hex("5714769d116bf76436ae74bc793d2c30ad1903c59ac5273805c7e2698b410c36"), receiver.publicKey)

        val key = SeamE2eCrypto.sharedKey(senderPrivate, receiver.publicKey)
        assertArrayEquals(hex("18d1c47d0296028a6dae78c8f723af54b80f1b9d6a4b7362f6ec34dfe3f979ce"), key)

        val nonce = hex("102030400000000000000002")
        val aad = hex("00000001000000000000000200000024766563746f722d7472616e736665722d3031")
        val ciphertext = hex("bfb9465ab07e48e9acbc450e8e471a8720bddf5193e5b68f9ac09cc0a373f3d943d4ac634d71cf7d6daeac546177e81ebda939f5")
        val plaintext = "SEAM Share cross-platform E2E vector".toByteArray()

        assertArrayEquals(ciphertext, SeamE2eCrypto.encrypt(key, nonce, plaintext, aad))
        assertArrayEquals(plaintext, SeamE2eCrypto.decrypt(key, nonce, ciphertext, aad))
    }

    @Test
    fun tamperingIsRejected() {
        val key = ByteArray(32) { 7 }
        val nonce = ByteArray(12) { 8 }
        val aad = "transfer-id".toByteArray()
        val ciphertext = SeamE2eCrypto.encrypt(key, nonce, "hello".toByteArray(), aad).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val rejected = runCatching { SeamE2eCrypto.decrypt(key, nonce, ciphertext, aad) }.isFailure
        assertTrue(rejected)
    }

    @Test
    fun nonceUsesBigEndianChunkIndex() {
        assertArrayEquals(hex("102030400000000000000002"), SeamE2eCrypto.chunkNonce(hex("10203040"), 2))
    }
}

private fun SeamE2eCrypto.generateKeyPairForTest(privateKey: ByteArray): SeamE2eCrypto.KeyPair {
    val cls = org.bouncycastle.crypto.params.X25519PrivateKeyParameters(privateKey, 0)
    return SeamE2eCrypto.KeyPair(privateKey.copyOf(), cls.generatePublicKey().encoded)
}
