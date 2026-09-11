package ir.redlighte.seamshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import ir.redlighte.seamshare.network.SeamPairingStore
import ir.redlighte.seamshare.network.SeamTransferController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Android Sharesheet target: send a shared file/text to the already-paired Windows device. */
class SeamShareShareActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pairing = SeamPairingStore(this).load()
        if (pairing == null) {
            Toast.makeText(this, "Open SEAM Share and pair a Windows PC first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val transfer = SeamTransferController(this)
        when (intent?.action) {
            Intent.ACTION_SEND -> handleSend(intent, transfer, pairing)
            Intent.ACTION_SEND_MULTIPLE -> handleMultiple(intent, transfer, pairing)
            else -> finish()
        }
    }

    private fun handleSend(
        intent: Intent,
        transfer: SeamTransferController,
        pairing: ir.redlighte.seamshare.network.SeamPairing
    ) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val stream = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        if (stream != null) {
            sendUri(stream, transfer, pairing)
        } else if (!text.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                val result = transfer.sendText(text, pairing.ip, pairing.port, pairing.token, "text")
                showResult(result.isSuccess, result.exceptionOrNull()?.message)
            }
        } else {
            showResult(false, "Nothing to share")
        }
    }

    private fun handleMultiple(
        intent: Intent,
        transfer: SeamTransferController,
        pairing: ir.redlighte.seamshare.network.SeamPairing
    ) {
        val streams = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty()
        if (streams.isEmpty()) {
            showResult(false, "Nothing to share")
            return
        }
        scope.launch(Dispatchers.IO) {
            var failed: String? = null
            streams.forEach { uri ->
                val result = transfer.send(uri, pairing.ip, pairing.port, pairing.token) { }
                if (result.isFailure && failed == null) failed = result.exceptionOrNull()?.message ?: "Transfer failed"
            }
            showResult(failed == null, failed)
        }
    }

    private fun sendUri(
        uri: android.net.Uri,
        transfer: SeamTransferController,
        pairing: ir.redlighte.seamshare.network.SeamPairing
    ) {
        scope.launch(Dispatchers.IO) {
            val result = transfer.send(uri, pairing.ip, pairing.port, pairing.token) { }
            showResult(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    private fun showResult(success: Boolean, error: String?) {
        runOnUiThread {
            Toast.makeText(
                this,
                if (success) "Sent with SEAM Share ✓" else "SEAM Share failed: ${error ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onDestroy() {
        scope.coroutineContext.cancel()
        super.onDestroy()
    }
}
