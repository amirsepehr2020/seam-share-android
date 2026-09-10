package ir.redlighte.seamshare.network

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SeamTransferController(private val context:Context){
    data class Progress(val sent:Long,val total:Long,val percent:Int)
    fun send(uri:Uri,targetIp:String,targetPort:Int,token:String,onProgress:(Progress)->Unit):Result<Unit> = runCatching{
        val resolver=context.contentResolver
        val total=resolver.openAssetFileDescriptor(uri,"r")?.use{it.length} ?: -1L
        val name=resolver.query(uri,arrayOf("_display_name"),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else "shared-file"} ?: "shared-file"
        val safeName=name.replace('\r','_').replace('\n','_')
        val connection=(URL("http://$targetIp:$targetPort/receive").openConnection() as HttpURLConnection).apply{
            requestMethod="POST";doOutput=true;connectTimeout=5000;readTimeout=60000
            setRequestProperty("Content-Type","application/octet-stream");setRequestProperty("X-Seam-Token",token);setRequestProperty("X-File-Name",safeName)
            if(total>=0)setFixedLengthStreamingMode(total)
        }
        resolver.openInputStream(uri).use{input->requireNotNull(input){"Unable to open selected file"};BufferedOutputStream(connection.outputStream).use{output->
            val buffer=ByteArray(256*1024);var sent=0L;var n:Int
            while(input.read(buffer).also{n=it}!=-1){output.write(buffer,0,n);sent+=n;val percent=if(total>0)((sent*100)/total).toInt().coerceAtMost(100) else 0;onProgress(Progress(sent,total,percent))}
        }}
        require(connection.responseCode in 200..299){"Transfer failed: ${connection.responseCode}"};connection.disconnect()
    }
}