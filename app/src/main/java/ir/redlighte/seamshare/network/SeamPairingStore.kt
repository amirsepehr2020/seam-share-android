package ir.redlighte.seamshare.network

import android.content.Context

class SeamPairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("seam_pairing", Context.MODE_PRIVATE)
    fun save(pairing: SeamPairing) = prefs.edit().putString("device_id", pairing.deviceId).putString("device_name", pairing.deviceName).putString("ip", pairing.ip).putInt("port", pairing.port).putString("token", pairing.token).apply()
    fun load(): SeamPairing? = prefs.getString("device_id", null)?.let { SeamPairing(it, prefs.getString("device_name", "This PC") ?: "This PC", prefs.getString("ip", "") ?: "", prefs.getInt("port", 38948), prefs.getString("token", "") ?: "") }
    fun clear() = prefs.edit().clear().apply()
}
