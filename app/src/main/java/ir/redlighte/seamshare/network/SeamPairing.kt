package ir.redlighte.seamshare.network

import org.json.JSONObject

data class SeamPairing(val deviceId:String,val deviceName:String,val ip:String,val port:Int,val token:String) {
    fun toJson(): String = JSONObject().apply { put("version",1);put("deviceId",deviceId);put("deviceName",deviceName);put("address",ip);put("port",port);put("token",token) }.toString()
    companion object {
        fun fromJson(value:String): SeamPairing {
            val o=JSONObject(value)
            require(o.optInt("version")==1)
            val deviceId=o.optString("deviceId").ifBlank { o.optString("device_id") }
            val deviceName=o.optString("deviceName").ifBlank { o.optString("device_name") }
            val address=o.optString("address").ifBlank { o.optString("ip") }
            val port=o.optInt("port",0)
            val token=o.optString("token")
            require(deviceId.isNotBlank() && deviceName.isNotBlank() && address.isNotBlank() && port>0 && token.isNotBlank())
            return SeamPairing(deviceId,deviceName,address,port,token)
        }
    }
}
