package ir.redlighte.seamshare.network

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class SeamTransferController(private val context: Context) {
    data class Progress(val sent: Long, val total: Long, val percent: Int)
    private val random = SecureRandom()

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Unable to open selected file" }
            BufferedInputStream(raw).use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    private fun hex(value: ByteArray): String = value.joinToString("") { String.format("%02x", it) }
    private fun decodeHex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun send(uri: Uri, targetIp: String, targetPort: Int, token: String, relativePath: String? = null, onProgress: (Progress) -> Unit): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        val total = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val name = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else "shared-file" } ?: "shared-file"
        require(total >= 0) { "Unable to determine file size" }
        val rel = relativePath ?: name
        val checksum = sha256(uri)
        val id = "android-${System.nanoTime()}"
        val sender = SeamE2eCrypto.generateKeyPair()
        val prefix = ByteArray(4).also(random::nextBytes)
        val requestBody = JSONObject().apply {
            put("id", id); put("name", name); put("size", total); put("relativePath", rel); put("checksum_sha256", checksum)
            put("e2e_version", 1); put("sender_ephemeral_public_key", hex(sender.publicKey)); put("nonce_prefix", hex(prefix))
        }.toString()
        val request = (URL("http://$targetIp:$targetPort/request").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 125000
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("X-Seam-Token", token)
        }
        request.outputStream.use { it.write(requestBody.toByteArray()) }
        val code = request.responseCode
        val responseText = runCatching { request.inputStream.bufferedReader().readText() }.getOrDefault("")
        require(code in 200..299) { "Transfer declined or timed out ($code)" }
        request.disconnect()
        val response = JSONObject(responseText)
        require(response.optInt("e2e_version") == 1) { "Receiver does not support E2E transfer" }
        val receiverPub = decodeHex(response.getString("receiver_ephemeral_public_key"))
        require(receiverPub.size == 32) { "Invalid receiver E2E key" }
        val key = SeamE2eCrypto.sharedKey(sender.privateKey, receiverPub)
        val safeName = URLEncoder.encode(name.replace('\r', '_').replace('\n', '_'), "UTF-8")
        val safeRelative = URLEncoder.encode(rel.replace('\r', '_').replace('\n', '_'), "UTF-8").replace("%2F", "/")
        val encryptedTotal = SeamE2eProtocol.ciphertextSize(total)
        val connection = (URL("http://$targetIp:$targetPort/receive-e2e").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 60000
            setRequestProperty("Content-Type", "application/octet-stream"); setRequestProperty("X-Seam-Token", token)
            setRequestProperty("X-Seam-Transfer-Id", id); setRequestProperty("X-File-Name", safeName); setRequestProperty("X-Relative-Path", safeRelative)
            setRequestProperty("X-Checksum-SHA256", checksum); setRequestProperty("X-Plaintext-Size", total.toString()); setFixedLengthStreamingMode(encryptedTotal)
        }
        resolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Unable to open selected file" }
            BufferedInputStream(raw).use { input ->
                BufferedOutputStream(connection.outputStream).use { output ->
                    val plain = ByteArray(SeamE2eProtocol.PLAINTEXT_CHUNK); var index = 0L; var sent = 0L
                    if (total == 0L) {
                        val cipher = SeamE2eCrypto.encrypt(key, SeamE2eProtocol.nonce(prefix, 0L), ByteArray(0), SeamE2eProtocol.aad(id, 0L, 0))
                        output.write(cipher)
                    } else {
                        while (true) {
                            val n = input.read(plain); if (n < 0) break
                            val cipher = SeamE2eCrypto.encrypt(key, SeamE2eProtocol.nonce(prefix, index), plain.copyOf(n), SeamE2eProtocol.aad(id, index, n))
                            output.write(cipher); sent += n
                            onProgress(Progress(sent, total, ((sent * 100) / total.coerceAtLeast(1)).toInt().coerceAtMost(100))); index++
                        }
                    }
                }
            }
        }
        val result = connection.responseCode
        require(result == 201) { if (result == 422) "Checksum or E2E verification failed on Windows" else "Transfer failed: $result" }
        connection.disconnect()
    }

    fun sendText(text: String, targetIp: String, targetPort: Int, token: String, kind: String = "text"): Result<Unit> = runCatching {
        require(text.isNotBlank()) { "Text is empty" }; val bytes = text.toByteArray(Charsets.UTF_8)
        val connection = (URL("http://$targetIp:$targetPort/text").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 10000
            setRequestProperty("Content-Type", "text/plain; charset=utf-8"); setRequestProperty("Content-Length", bytes.size.toString()); setRequestProperty("X-Seam-Token", token)
            setRequestProperty("X-Seam-Text-Kind", kind); setRequestProperty("X-Seam-Text-Id", "android-text-${System.nanoTime()}")
        }
        connection.outputStream.use { it.write(bytes) }; require(connection.responseCode in 200..299) { "Text send failed: ${connection.responseCode}" }; connection.disconnect()
    }
}
