package ir.redlighte.seamshare.network

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class IncomingRequest(val id:String,val name:String,val size:Long,val relativePath:String,val checksumSha256:String,val e2eVersion:Int?=null,val senderEphemeralPublicKey:String?=null,val noncePrefix:String?=null)
data class IncomingText(val id:String,val text:String,val kind:String)

class SeamReceiverServer(private val context:Context, private val identity:SeamIdentity, private val port:Int=38949){
    private data class PendingE2e(val key:ByteArray,val noncePrefix:ByteArray)
    private val executor=Executors.newCachedThreadPool()
    private val prefs:SharedPreferences=context.getSharedPreferences("seam_share_settings",Context.MODE_PRIVATE)
    private val pending=ConcurrentHashMap<String,CompletableFuture<Boolean>>()
    private val pendingE2e=ConcurrentHashMap<String,PendingE2e>()
    @Volatile private var running=false
    var onIncomingRequest:((IncomingRequest)->Unit)?=null
    var onIncomingText:((IncomingText)->Unit)?=null
    var onTransferVerified:((String,String)->Unit)?=null
    var onTransferVerificationFailed:((String,String,String)->Unit)?=null
    fun start(){if(running)return;running=true;executor.execute{serve()};executor.execute{discover()}}
    fun stop(){running=false;executor.shutdownNow();pendingE2e.clear()}
    fun approve(id:String,approved:Boolean){pending.remove(id)?.complete(approved);if(!approved)pendingE2e.remove(id)}
    fun destinationLabel():String=prefs.getString("receive_tree_uri",null)?.let{"Custom folder"} ?: "Downloads/SEAM Share"
    fun setReceiveTreeUri(uri:Uri){context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.edit().putString("receive_tree_uri",uri.toString()).apply()}
    private fun createOutput(name:String,relativePath:String):Pair<java.io.OutputStream,Uri?>?{
        val safeParts=relativePath.replace('\\','/').split('/').filter{it.isNotBlank()&&it!="."&&it!=".."}.map{it.filter{c->c.isLetterOrDigit()||c=='.'||c=='_'||c=='-'||c==' '}.ifBlank{"received-file"}}
        val safeName=safeParts.lastOrNull() ?: name.filter{it.isLetterOrDigit()||it=='.'||it=='_'||it=='-'||it==' '}.ifBlank{"received-file"}
        val tree=prefs.getString("receive_tree_uri",null)
        if(tree!=null){var dir=DocumentFile.fromTreeUri(context,Uri.parse(tree)) ?: return null;for(part in safeParts.dropLast(1)){dir=dir.findFile(part)?.takeIf{it.isDirectory} ?: dir.createDirectory(part) ?: return null};val uri=dir.createFile("application/octet-stream",safeName)?.uri ?: return null;return context.contentResolver.openOutputStream(uri)?.let{it to uri}}
        val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,safeName);put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SEAM Share")}
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: return null
        return context.contentResolver.openOutputStream(uri)?.let{it to uri}
    }
    private fun deleteOutput(uri:Uri?){if(uri!=null)runCatching{context.contentResolver.delete(uri,null,null)}}
    private fun serve(){while(running){try{ServerSocket(port).use{server->while(running){val socket=server.accept();executor.execute{handle(socket)}}}}catch(_:Exception){if(running)try{Thread.sleep(1000)}catch(_:InterruptedException){Thread.currentThread().interrupt()}}}}
    private fun handle(socket:Socket){socket.use{s->runCatching{
        val input=BufferedInputStream(s.getInputStream());val headerBytes=ByteArrayOutputStream();var state=0
        while(state<4){val b=input.read();if(b<0)return@runCatching;headerBytes.write(b);state=if(state==0&&b==13)1 else if(state==1&&b==10)2 else if(state==2&&b==13)3 else if(state==3&&b==10)4 else 0;if(headerBytes.size()>65536)return@runCatching}
        val lines=headerBytes.toString(Charsets.UTF_8.name()).trim().split("\r\n");val request=lines.firstOrNull().orEmpty();val headers=lines.drop(1).mapNotNull{it.split(":",limit=2).takeIf{p->p.size==2}?.let{p->p[0].trim().lowercase() to p[1].trim()}}.toMap()
        if(request.startsWith("POST /pair")){respond(s,200,"{}");return@runCatching};if(headers["x-seam-token"]!=identity.token){respond(s,401,"");return@runCatching}
        if(request.startsWith("POST /request")){
            val body=readBody(input,headers["content-length"]?.toLongOrNull()?:0L);val o=JSONObject(body)
            val req=IncomingRequest(o.getString("id"),o.getString("name"),o.getLong("size"),o.getString("relativePath"),o.getString("checksum_sha256"),o.optInt("e2e_version",0).takeIf{it>0},o.optString("sender_ephemeral_public_key","").takeIf{it.isNotBlank()},o.optString("nonce_prefix","").takeIf{it.isNotBlank()})
            require(req.checksumSha256.matches(Regex("[0-9a-fA-F]{64}")))
            var e2eResponse="{}"
            if(req.e2eVersion==1&&req.senderEphemeralPublicKey!=null&&req.noncePrefix!=null){val peer=hexDecode(req.senderEphemeralPublicKey,32);val prefix=hexDecode(req.noncePrefix,4);val kp=SeamE2eCrypto.generateKeyPair();val key=SeamE2eCrypto.sharedKey(kp.privateKey,peer);pendingE2e[req.id]=PendingE2e(key,prefix);e2eResponse=JSONObject().apply{put("e2e_version",1);put("receiver_ephemeral_public_key",hexEncode(kp.publicKey))}.toString()}
            val future=CompletableFuture<Boolean>();pending[req.id]=future;onIncomingRequest?.invoke(req);val ok=runCatching{future.get(120,java.util.concurrent.TimeUnit.SECONDS)}.getOrDefault(false);pending.remove(req.id);if(!ok)pendingE2e.remove(req.id);respond(s,if(ok)200 else 403,if(ok)e2eResponse else "DECLINED");return@runCatching
        }
        if(request.startsWith("POST /text")){val length=headers["content-length"]?.toLongOrNull()?:0L;val text=readBody(input,length);val kind=headers["x-seam-text-kind"] ?: "text";val id=headers["x-seam-text-id"] ?: "android-text-${System.nanoTime()}";onIncomingText?.invoke(IncomingText(id,text,kind));respond(s,201,"OK");return@runCatching}
        if(!request.startsWith("POST /receive")){respond(s,404,"");return@runCatching}
        val encrypted=request.startsWith("POST /receive-e2e")
        val length=headers["content-length"]?.toLongOrNull() ?: 0L;val expected=headers["x-checksum-sha256"]?.lowercase() ?: "";if(!expected.matches(Regex("[0-9a-f]{64}"))){respond(s,400,"invalid checksum");return@runCatching}
        val rawName=headers["x-file-name"]?.let{runCatching{java.net.URLDecoder.decode(it,"UTF-8")}.getOrDefault(it)} ?: "received-file";val rel=headers["x-relative-path"]?.let{runCatching{java.net.URLDecoder.decode(it,"UTF-8")}.getOrDefault(it)} ?: rawName
        val output=createOutput(rawName,rel) ?: run{respond(s,500,"unable to create output");return@runCatching};val digest=MessageDigest.getInstance("SHA-256");var received=0L
        output.first.use{out->
            if(encrypted){
                val transferId=headers["x-seam-transfer-id"] ?: run{deleteOutput(output.second);respond(s,400,"missing transfer id");return@runCatching}
                val crypto=pendingE2e.remove(transferId) ?: run{deleteOutput(output.second);respond(s,403,"missing e2e session");return@runCatching}
                val plainSize=headers["x-plaintext-size"]?.toLongOrNull() ?: -1L;require(plainSize>=0);require(SeamE2eProtocol.ciphertextSize(plainSize)==length)
                var plainRemaining=plainSize;var index=0L
                while(plainRemaining>0||index==0L){val plainLen=if(plainRemaining==0L)0 else minOf(256L*1024L,plainRemaining).toInt();val frameLen=plainLen+16;val cipher=readBytes(input,frameLen.toLong());val plain=SeamE2eCrypto.decrypt(crypto.key,SeamE2eProtocol.nonce(crypto.noncePrefix,index),cipher,SeamE2eProtocol.aad(transferId,index,plainLen));require(plain.size==plainLen);out.write(plain);digest.update(plain);received+=plain.size.toLong();plainRemaining-=plainLen.toLong();index++}
                require(received==plainSize)
            }else{
                var remaining=length;val buffer=ByteArray(256*1024);while(remaining>0){val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(n<=0)break;out.write(buffer,0,n);digest.update(buffer,0,n);received+=n.toLong();remaining-=n};if(received!=length){deleteOutput(output.second);respond(s,422,"size mismatch");return@runCatching}
            }
        }
        val actual=digest.digest().joinToString(""){String.format("%02x",it)}
        if(actual!=expected){deleteOutput(output.second);onTransferVerificationFailed?.invoke(rawName,expected,actual);respond(s,422,"checksum mismatch");return@runCatching}
        onTransferVerified?.invoke(rawName,actual);respond(s,201,"VERIFIED")
    }}}
    private fun readBody(input:BufferedInputStream,length:Long):String=String(readBytes(input,length),Charsets.UTF_8)
    private fun readBytes(input:BufferedInputStream,length:Long):ByteArray{require(length>=0&&length<=Int.MAX_VALUE);val out=ByteArrayOutputStream(length.toInt());var remaining=length;val b=ByteArray(8192);while(remaining>0){val n=input.read(b,0,minOf(b.size.toLong(),remaining).toInt());if(n<=0)throw IllegalStateException("connection closed");out.write(b,0,n);remaining-=n};return out.toByteArray()}
    private fun respond(socket:Socket,code:Int,body:String){val reason=when(code){200->"OK";201->"Created";400->"Bad Request";401->"Unauthorized";403->"Forbidden";422->"Unprocessable Entity";else->"Error"};val bytes=body.toByteArray();BufferedOutputStream(socket.getOutputStream()).use{it.write("HTTP/1.1 $code $reason\r\nContent-Length: ${bytes.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray());it.write(bytes);it.flush()}}
    private fun discover(){while(running){try{DatagramSocket(38947).use{socket->socket.broadcast=true;val buffer=ByteArray(1024);while(running){val packet=DatagramPacket(buffer,buffer.size);socket.receive(packet);val text=String(packet.data,0,packet.length);val p=text.split('|');if(p.size>=3&&p[0]=="SEAM_SHARE_DISCOVER_V2"){val reply="SEAM_SHARE_DISCOVER_V2|${identity.deviceName}|$port".toByteArray();socket.send(DatagramPacket(reply,reply.size,packet.address,packet.port))}}}}catch(_:Exception){if(running)try{Thread.sleep(1000)}catch(_:InterruptedException){Thread.currentThread().interrupt()}}}}
    private fun hexDecode(value:String,size:Int):ByteArray{require(value.length==size*2);return ByteArray(size){value.substring(it*2,it*2+2).toInt(16).toByte()}}
    private fun hexEncode(value:ByteArray):String=value.joinToString(""){String.format("%02x",it)}
}
