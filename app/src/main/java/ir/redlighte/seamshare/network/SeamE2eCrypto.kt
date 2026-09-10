package ir.redlighte.seamshare.network

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import java.security.SecureRandom

/** Cross-platform E2E primitives: X25519 key agreement + HKDF-SHA256 + ChaCha20-Poly1305. */
object SeamE2eCrypto {
    private val random = SecureRandom()
    const val TAG_LENGTH_BYTES = 16
    const val NONCE_PREFIX_LENGTH = 4
    private val protocolContext = "SEAM-Share-E2E-v1".toByteArray()

    data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val privateKey = ByteArray(32).also(random::nextBytes)
        val priv = X25519PrivateKeyParameters(privateKey, 0)
        return KeyPair(privateKey.copyOf(), priv.generatePublicKey().encoded)
    }

    fun sharedKey(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32 && peerPublicKey.size == 32)
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
        require(shared.any { it.toInt() != 0 }) { "Invalid peer public key" }
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, null, protocolContext))
        return ByteArray(32).also { hkdf.generateBytes(it, 0, it.size) }
    }

    /** 96-bit nonce: 32-bit random transfer prefix + 64-bit big-endian chunk index. */
    fun chunkNonce(prefix: ByteArray, index: Long): ByteArray {
        require(prefix.size == NONCE_PREFIX_LENGTH && index >= 0)
        return ByteArray(12).also { nonce ->
            prefix.copyInto(nonce, 0)
            for (i in 0 until 8) nonce[4 + i] = (index ushr (56 - i * 8)).toByte()
        }
    }

    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        return ByteArray(cipher.getOutputSize(plaintext.size)).also { out ->
            var n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
            n += cipher.doFinal(out, n)
            if (n != out.size) return out.copyOf(n)
        }
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        return ByteArray(cipher.getOutputSize(ciphertext.size)).also { out ->
            var n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
            n += cipher.doFinal(out, n)
            if (n != out.size) return out.copyOf(n)
        }
    }
}
