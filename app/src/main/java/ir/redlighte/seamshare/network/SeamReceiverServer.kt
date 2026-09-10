package ir.redlighte.seamshare.network

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class IncomingRequest(val id:String,val name:String,val size:Long,val relativePath:String)

class SeamReceiverServer(private val context:Context, private val identity:SeamIdentity, private val port:Int=38949){
    private val executor=Executors.newCachedThreadPool()
    private val prefs:SharedPreferences=context.getSharedPreferences("seam_share_settings",Context.MODE_PRIVATE)
    private val pending=ConcurrentHashMap<String,CompletableFuture<Boolean>>()
    @Volatile private var running=false
    var onIncomingRequest:((IncomingRequest)->Unit)?=null
    fun start(){ if(running)return; running=true; executor.execute{serve()}; executor.execute{discover()} }
    fun stop(){running=false; executor.shutdownNow()}
    fun approve(id:String,approved:Boolean){pending.remove(id)?.complete(approved)}
    fun destinationLabel():String = prefs.getString("receive_tree_uri",null)?.let{"Custom folder"} ?: "Downloads/SEAM Share"
    fun setReceiveTreeUri(uri:Uri){context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.edit().putString("receive_tree_uri",uri.toString()).apply()}
    private fun createOutput(name:String,relativePath:String):java.io.OutputStream?{
        val safeParts=relativePath.replace('\\','/').split('/').filter{it.isNotBlank()&&it!="."&&it!=".."}.map{it.filter{c->c.isLetterOrDigit()||c=='.'||c=='_'||c=='-'||c==' '}.ifBlank{"received-file"}}
        val safeName=safeParts.lastOrNull() ?: name.filter{it.isLetterOrDigit()||it=='.'||it=='_'||it=='-'||it==' '}.ifBlank{"received-file"}
        val tree=prefs.getString("receive_tree_uri",null)
        if(tree!=null){var dir=DocumentFile.fromTreeUri(context,Uri.parse(tree)) ?: return null;for(part in safeParts.dropLast(1)){dir=dir.findFile(part)?.takeIf{it.isDirectory} ?: dir.createDirectory(part) ?: return null};return dir.createFile("application/octet-stream",safeName)?.uri?.let{context.contentResolver.openOutputStream(it)}}
        val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,safeName);put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SEAM Share")}
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: return null
        return context.contentResolver.openOutputStream(uri)
    }
    private fun serve(){runCatching{ServerSocket(port).use{server->while(running){val socket=server.accept();executor.execute{handle(socket)}}}}}
    private fun handle(socket:Socket){socket.use{s->runCatching{
        val input=BufferedInputStream(s.getInputStream());val headerBytes=ByteArrayOutputStream();var state=0
        while(state<4){val b=input.read();if(b<0)return@runCatching;headerBytes.write(b);state=if(state==0&&b==13)1 else if(state==1&&b==10)2 else if(state==2&&b==13)3 else if(state==3&&b==10)4 else 0;if(headerBytes.size()>65536)return@runCatching}
        val lines=headerBytes.toString(Charsets.UTF_8.name()).trim().split("\r\n");val request=lines.firstOrNull().orEmpty();val headers=lines.drop(1).mapNotNull{it.split(":",limit=2).takeIf{p->p.size==2}?.let{p->p[0].trim().lowercase() to p[1].trim()}}.toMap()
        if(request.startsWith("POST /pair")){respond(s,200,"{}");return@runCatching};if(headers["x-seam-token"]!=identity.token){respond(s,401,"");return@runCatching}
        if(request.startsWith("POST /request")){val body=readBody(input,headers["content-length"]?.toLongOrNull()?:0L);val o=org.json.JSONObject(body);val req=IncomingRequest(o.getString("id"),o.getString("name"),o.getLong("size"),o.getString("relativePath"));val future=CompletableFuture<Boolean>();pending[req.id]=future;onIncomingRequest?.invoke(req);val ok=runCatching{future.get(120,java.util.concurrent.TimeUnit.SECONDS)}.getOrDefault(false);pending.remove(req.id);respond(s,if(ok)200 else 403,if(ok)"OK" else "DECLINED");return@runCatching}
        if(!request.startsWith("POST /receive")){respond(s,404,"");return@runCatching}
        val length=headers["content-length"]?.toLongOrNull() ?: 0L;val rawName=headers["x-file-name"]?.let{runCatching{java.net.URLDecoder.decode(it,"UTF-8")}.getOrDefault(it)} ?: "received-file";val rel=headers["x-relative-path"]?.let{runCatching{java.net.URLDecoder.decode(it,"UTF-8")}.getOrDefault(it)} ?: rawName
        createOutput(rawName,rel)?.use{out->var remaining=length;val buffer=ByteArray(256*1024);while(remaining>0){val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(n<=0)break;out.write(buffer,0,n);remaining-=n}}
        respond(s,201,"OK")
    }}}
    private fun readBody(input:BufferedInputStream,length:Long):String{val out=ByteArrayOutputStream();var remaining=length;val b=ByteArray(8192);while(remaining>0){val n=input.read(b,0,minOf(b.size.toLong(),remaining).toInt());if(n<=0)break;out.write(b,0,n);remaining-=n};return out.toString(Charsets.UTF_8.name())}
    private fun respond(socket:Socket,code:Int,body:String){val bytes=body.toByteArray();BufferedOutputStream(socket.getOutputStream()).use{it.write("HTTP/1.1 $code OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());it.write(bytes);it.flush()}}
    private fun discover(){runCatching{DatagramSocket(38947).use{socket->socket.broadcast=true;val buffer=ByteArray(1024);while(running){val packet=DatagramPacket(buffer,buffer.size);socket.receive(packet);val text=String(packet.data,0,packet.length);val p=text.split('|');if(p.size>=3&&p[0]=="SEAM_SHARE_DISCOVER_V2"){val reply="SEAM_SHARE_DISCOVER_V2|${identity.deviceName}|$port".toByteArray();socket.send(DatagramPacket(reply,reply.size,packet.address,packet.port))}}}}}
}
