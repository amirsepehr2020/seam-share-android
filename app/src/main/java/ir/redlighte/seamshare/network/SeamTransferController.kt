package ir.redlighte.seamshare.network

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class SeamTransferController(private val context:Context){
    data class Progress(val sent:Long,val total:Long,val percent:Int)
    private fun sha256(uri:Uri):String{
        val digest=MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use{raw->
            requireNotNull(raw){"Unable to open selected file"}
            BufferedInputStream(raw).use{input->
                val buffer=ByteArray(256*1024)
                while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
            }
        }
        return digest.digest().joinToString(""){String.format("%02x",it)}
    }
    fun send(uri:Uri,targetIp:String,targetPort:Int,token:String,relativePath:String?=null,onProgress:(Progress)->Unit):Result<Unit> = runCatching{
        val resolver=context.contentResolver
        val total=resolver.openAssetFileDescriptor(uri,"r")?.use{it.length} ?: -1L
        val name=resolver.query(uri,arrayOf("_display_name"),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else "shared-file"} ?: "shared-file"
        require(total>=0){"Unable to determine file size"}
        val rel=relativePath ?: name
        val checksum=sha256(uri)
        val id="android-${System.nanoTime()}"
        val requestBody=JSONObject().apply{put("id",id);put("name",name);put("size",total);put("relativePath",rel);put("checksum_sha256",checksum)}.toString()
        val request=(URL("http://$targetIp:$targetPort/request").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=5000;readTimeout=125000;setRequestProperty("Content-Type","application/json");setRequestProperty("X-Seam-Token",token)}
        request.outputStream.use{it.write(requestBody.toByteArray())}
        require(request.responseCode in 200..299){"Transfer declined or timed out (${request.responseCode})"}
        request.disconnect()
        val safeName=URLEncoder.encode(name.replace('\r','_').replace('\n','_'),"UTF-8")
        val safeRelative=URLEncoder.encode(rel.replace('\r','_').replace('\n','_'),"UTF-8").replace("%2F","/")
        val connection=(URL("http://$targetIp:$targetPort/receive").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=5000;readTimeout=60000;setRequestProperty("Content-Type","application/octet-stream");setRequestProperty("X-Seam-Token",token);setRequestProperty("X-File-Name",safeName);setRequestProperty("X-Relative-Path",safeRelative);setRequestProperty("X-Checksum-SHA256",checksum);setFixedLengthStreamingMode(total)}
        resolver.openInputStream(uri).use{raw->requireNotNull(raw){"Unable to open selected file"};BufferedInputStream(raw).use{input->BufferedOutputStream(connection.outputStream).use{output->val buffer=ByteArray(256*1024);var sent=0L;var n:Int;while(input.read(buffer).also{n=it}!=-1){output.write(buffer,0,n);sent+=n;val percent=((sent*100)/total).toInt().coerceAtMost(100);onProgress(Progress(sent,total,percent))}}}}
        val code=connection.responseCode
        require(code==201){if(code==422)"Checksum verification failed on Windows" else "Transfer failed: $code"}
        connection.disconnect()
    }
    fun sendText(text:String,targetIp:String,targetPort:Int,token:String,kind:String="text"):Result<Unit> = runCatching{
        require(text.isNotBlank()){"Text is empty"};val bytes=text.toByteArray(Charsets.UTF_8)
        val connection=(URL("http://$targetIp:$targetPort/text").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=5000;readTimeout=10000;setRequestProperty("Content-Type","text/plain; charset=utf-8");setRequestProperty("Content-Length",bytes.size.toString());setRequestProperty("X-Seam-Token",token);setRequestProperty("X-Seam-Text-Kind",kind);setRequestProperty("X-Seam-Text-Id","android-text-${System.nanoTime()}")}
        connection.outputStream.use{it.write(bytes)};require(connection.responseCode in 200..299){"Text send failed: ${connection.responseCode}"};connection.disconnect()
    }
}
