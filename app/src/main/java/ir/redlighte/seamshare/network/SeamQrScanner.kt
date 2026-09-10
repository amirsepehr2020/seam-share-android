package ir.redlighte.seamshare.network

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class SeamQrScanner(private val launcher: ActivityResultLauncher<ScanOptions>) {
    fun start() {
        launcher.launch(ScanOptions().apply {
            setPrompt("Scan the SEAM Share QR code on your PC")
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }
    companion object {
        fun options(): ScanOptions = ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setOrientationLocked(false) }
    }
}
