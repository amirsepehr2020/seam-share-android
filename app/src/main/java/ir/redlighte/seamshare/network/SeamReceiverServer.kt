package ir.redlighte.seamshare.network

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SeamReceiverServer(private val context:Context, private val identity:SeamIdentity, private val port:Int=38949){
    private val executor=Executors.newCachedThreadPool()
    private val prefs:SharedPreferences=context.getSharedPreferences("seam_share_settings",Context.MODE_PRIVATE)
    @Volatile private var running=false
    fun start(){ if(running)return; running=true; executor.execute{serve()}; executor.execute{discover()} }
    fun stop(){running=false; executor.shutdownNow()}
    fun destinationLabel():String = prefs.getString("receive_tree_uri",null)?.let{"Custom folder"} ?: "Downloads/SEAM Share"
    fun setReceiveTreeUri(uri:Uri){context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.edit().putString("receive_tree_uri",uri.toString()).apply()}
    private fun createOutput(name:String):java.io.OutputStream?{
        val tree=prefs.getString("receive_tree_uri",null)
        if(tree!=null){val dir=DocumentFile.fromTreeUri(context,Uri.parse(tree));val safe=name.filter{it.isLetterOrDigit()||it=='.'||it=='_'||it=='-'||it==' '}.ifBlank{"received-file"};return dir?.createFile("application/octet-stream",safe)?.uri?.let{context.contentResolver.openOutputStream(it)}}
        val values=android.content.ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SEAM Share")}
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: return null
        return context.contentResolver.openOutputStream(uri)
    }
    private fun serve(){runCatching{ServerSocket(port).use{server->while(running){val socket=server.accept();executor.execute{handle(socket)}}}}}
    private fun handle(socket:Socket){socket.use{s->runCatching{
        val input=BufferedInputStream(s.getInputStream());val headerBytes=ByteArrayOutputStream();var state=0
        while(state<4){val b=input.read();if(b<0)return@runCatching;headerBytes.write(b);state=if(state==0&&b==13)1 else if(state==1&&b==10)2 else if(state==2&&b==13)3 else if(state==3&&b==10)4 else 0;if(headerBytes.size()>65536)return@runCatching}
        val lines=headerBytes.toString(Charsets.UTF_8.name()).trim().split("\r\n");val request=lines.firstOrNull().orEmpty();val headers=lines.drop(1).mapNotNull{it.split(":",limit=2).takeIf{p->p.size==2}?.let{p->p[0].trim().lowercase() to p[1].trim()}}.toMap()
        if(request.startsWith("POST /pair")){respond(s,200,"{}");return@runCatching};if(!request.startsWith("POST /receive")){respond(s,404,"");return@runCatching};if(headers["x-seam-token"]!=identity.token){respond(s,401,"");return@runCatching}
        val length=headers["content-length"]?.toLongOrNull() ?: 0L;val rawName=headers["x-file-name"]?.let{runCatching{java.net.URLDecoder.decode(it,"UTF-8")}.getOrDefault(it)} ?: "received-file";val name=rawName.filter{it.isLetterOrDigit()||it=='.'||it=='_'||it=='-'||it==' '}.ifBlank{"received-file"}
        createOutput(name)?.use{out->val buffer=ByteArray(256*1024);var remaining=length;while(remaining>0){val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(n<=0)break;out.write(buffer,0,n);remaining-=n}}
        respond(s,201,"OK")
    }}}
    private fun respond(socket:Socket,code:Int,body:String){val bytes=body.toByteArray();BufferedOutputStream(socket.getOutputStream()).use{it.write("HTTP/1.1 $code OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());it.write(bytes);it.flush()}}
    private fun discover(){runCatching{DatagramSocket(38947).use{socket->socket.broadcast=true;val buffer=ByteArray(1024);while(running){val packet=DatagramPacket(buffer,buffer.size);socket.receive(packet);val text=String(packet.data,0,packet.length);val p=text.split('|');if(p.size>=3&&p[0]=="SEAM_SHARE_DISCOVER_V2"){val reply="SEAM_SHARE_DISCOVER_V2|${identity.deviceName}|$port".toByteArray();socket.send(DatagramPacket(reply,reply.size,packet.address,packet.port))}}}}}
}
