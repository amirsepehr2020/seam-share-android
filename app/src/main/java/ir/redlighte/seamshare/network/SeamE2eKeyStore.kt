package ir.redlighte.seamshare.network

import android.content.Context
import android.util.Base64

class SeamE2eKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("seam_e2e_identity", Context.MODE_PRIVATE)

    fun loadOrCreate(): SeamE2eCrypto.KeyPair {
        val privateKey = prefs.getString("private_key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val publicKey = prefs.getString("public_key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (privateKey?.size == 32 && publicKey?.size == 32) return SeamE2eCrypto.KeyPair(privateKey, publicKey)
        val generated = SeamE2eCrypto.generateKeyPair()
        prefs.edit()
            .putString("private_key", Base64.encodeToString(generated.privateKey, Base64.NO_WRAP))
            .putString("public_key", Base64.encodeToString(generated.publicKey, Base64.NO_WRAP))
            .apply()
        return generated
    }
}
