package ir.redlighte.seamshare.network

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object SeamPairClient {
    fun pair(target:SeamPairing, identity:SeamIdentity, localIp:String, localPort:Int):Result<Unit> = runCatching {
        val body=JSONObject().apply{put("device_id",identity.deviceId);put("device_name",identity.deviceName);put("ip",localIp);put("port",localPort);put("token",identity.token)}.toString().toByteArray()
        val connection=(URL("http://${target.ip}:${target.port}/pair").openConnection() as HttpURLConnection).apply{
            requestMethod="POST";doOutput=true;connectTimeout=5000;readTimeout=5000
            setRequestProperty("Content-Type","application/json");setRequestProperty("Content-Length",body.size.toString());setRequestProperty("X-Seam-Token",target.token)
        }
        BufferedOutputStream(connection.outputStream).use{it.write(body);it.flush()}
        require(connection.responseCode in 200..299){"Pairing failed: ${connection.responseCode}"}
        connection.disconnect()
    }
}