package ir.redlighte.seamshare.network

import android.content.Context
import java.util.UUID

data class SeamIdentity(val deviceId:String,val deviceName:String,val token:String)

class SeamIdentityStore(context:Context){
    private val prefs=context.getSharedPreferences("seam_identity",Context.MODE_PRIVATE)
    fun load():SeamIdentity{
        val id=prefs.getString("device_id",null) ?: "seam-${UUID.randomUUID()}".also{prefs.edit().putString("device_id",it).apply()}
        val token=prefs.getString("token",null) ?: UUID.randomUUID().toString().also{prefs.edit().putString("token",it).apply()}
        val name=prefs.getString("device_name","Android") ?: "Android"
        return SeamIdentity(id,name,token)
    }
}