package ir.redlighte.seamshare.network

import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SeamTransferClient {
    fun sendFile(file: File, host: String, port: Int, token: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean {
        val connection = (URL("http://$host:$port/receive").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 60000
            setRequestProperty("Content-Length", file.length().toString())
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-SEAM-Token", token)
            setRequestProperty("X-File-Name", file.name)
        }
        return try {
            BufferedOutputStream(connection.outputStream).use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024); var sent = 0L; var read: Int
                    while (input.read(buffer).also { read = it } != -1) { output.write(buffer, 0, read); sent += read; onProgress(sent, file.length()) }
                }
            }
            connection.responseCode in 200..299
        } finally { connection.disconnect() }
    }
}
