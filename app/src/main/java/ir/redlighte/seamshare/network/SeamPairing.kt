package ir.redlighte.seamshare.network

import org.json.JSONObject

data class SeamPairing(val deviceId:String,val deviceName:String,val ip:String,val port:Int,val token:String) {
    fun toJson(): String = JSONObject().apply { put("version",1);put("deviceId",deviceId);put("deviceName",deviceName);put("address",ip);put("port",port);put("token",token) }.toString()
    companion object { fun fromJson(value:String): SeamPairing { val o=JSONObject(value); require(o.optInt("version")==1); return SeamPairing(o.getString("deviceId"),o.getString("deviceName"),o.getString("address"),o.getInt("port"),o.getString("token")) } }
}
