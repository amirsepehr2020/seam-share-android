package ir.redlighte.seamshare.network

import android.content.Context

class SeamPairingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("seam_share_pairing", Context.MODE_PRIVATE)
    private companion object { const val KEY_PAIRING = "pairing" }

    fun save(pairing: SeamPairing) {
        prefs.edit().putString(KEY_PAIRING, pairing.toJson()).apply()
    }

    fun load(): SeamPairing? = runCatching {
        prefs.getString(KEY_PAIRING, null)?.let(SeamPairing::fromJson)
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(KEY_PAIRING).apply()
    }
}
