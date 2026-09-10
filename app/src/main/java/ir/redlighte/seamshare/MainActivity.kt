package ir.redlighte.seamshare

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ir.redlighte.seamshare.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamShareApp() }
    }
}

data class QueueItem(val uri: Uri, val name: String, val size: Long, val relativePath: String)

private fun localIp(context: Context): String = runCatching {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ip = wm.connectionInfo.ipAddress
    listOf(ip and 255, (ip shr 8) and 255, (ip shr 16) and 255, (ip shr 24) and 255).joinToString(".")
}.getOrDefault("127.0.0.1")

private fun collectTree(root: DocumentFile, prefix: String = ""): List<QueueItem> {
    val result = mutableListOf<QueueItem>()
    root.listFiles().forEach { file ->
        val name = file.name ?: "shared-file"
        val relative = if (prefix.isBlank()) name else "$prefix/$name"
        if (file.isDirectory) result += collectTree(file, relative)
        else result += QueueItem(file.uri, name, file.length(), relative)
    }
    return result
}

@Composable
private fun SeamShareApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val identity = remember { SeamIdentityStore(context).load() }
    val store = remember { SeamPairingStore(context) }
    val receiver = remember { SeamReceiverServer(context, identity) }
    val discovery = remember { SeamDiscovery() }

    var pairing by remember { mutableStateOf<SeamPairing?>(null) }
    var devices by remember { mutableStateOf<List<SeamDiscovery.Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    var progress by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var incoming by remember { mutableStateOf<IncomingRequest?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val items = uris.map { uri ->
            val name = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else "shared-file"
            } ?: "shared-file"
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            QueueItem(uri, name, size, name)
        }
        if (items.isNotEmpty()) {
            queue = queue + items
            message = "${items.size} file(s) added to queue"
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                DocumentFile.fromTreeUri(context, uri)?.let(::collectTree).orEmpty()
            }.onSuccess { items ->
                queue = queue + items
                message = "${items.size} file(s) from folder added"
            }.onFailure { message = "Could not read folder" }
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            message = "QR scan cancelled"
        } else {
            runCatching { SeamPairing.fromJson(contents) }
                .onSuccess { value ->
                    scope.launch(Dispatchers.IO) {
                        val resultPair = SeamPairClient.pair(value, identity, localIp(context), 38949)
                        launch(Dispatchers.Main) {
                            if (resultPair.isSuccess) {
                                store.save(value)
                                pairing = value
                                message = "Paired with ${value.deviceName}"
                            } else {
                                message = "Pairing failed: ${resultPair.exceptionOrNull()?.message ?: "connection error"}"
                            }
                        }
                    }
                }
                .onFailure { message = "Invalid SEAM Share pairing code" }
        }
    }

    DisposableEffect(Unit) {
        receiver.onIncomingRequest = { request ->
            scope.launch(Dispatchers.Main) { incoming = request }
        }
        receiver.start()
        onDispose {
            receiver.stop()
            receiver.onIncomingRequest = null
        }
    }

    LaunchedEffect(Unit) { pairing = store.load() }

    fun sendQueue() {
        val target = pairing
        if (target == null) {
            message = "Pair a Windows PC first"
            return
        }
        if (queue.isEmpty()) {
            message = "Add files to the queue first"
            return
        }
        sending = true
        scope.launch(Dispatchers.IO) {
            for (item in queue) {
                val result = SeamTransferController(context).send(
                    item.uri, target.ip, target.port, target.token, item.relativePath
                ) { p ->
                    scope.launch(Dispatchers.Main) { progress = p.percent }
                }
                if (result.isFailure) {
                    launch(Dispatchers.Main) {
                        message = "Transfer failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    }
                    break
                }
                launch(Dispatchers.Main) { message = "Sent ${item.name}" }
            }
            launch(Dispatchers.Main) {
                sending = false
                queue = emptyList()
                progress = 100
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C5CFF), secondary = Color(0xFF00D9FF))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SEAM Share", color = Color.White, fontSize = 22.sp)
                Text("Move freely. Stay connected.", color = Color(0xFF7E8393), fontSize = 12.sp)

                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (pairing == null) "Connect your PC" else pairing!!.deviceName, color = Color.White, fontSize = 24.sp)
                        Text(
                            if (pairing == null) "Scan the QR code shown by SEAM Share on Windows." else "Paired over your local network.",
                            color = Color(0xFF858A9B), fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp)
                        )
                        Button(
                            onClick = { qrLauncher.launch(ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan the SEAM Share QR code on your PC"); setBeepEnabled(false); setOrientationLocked(false) }) },
                            modifier = Modifier.padding(top = 16.dp)
                        ) { Text("Scan pairing QR") }
                    }
                }

                if (pairing != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151824)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("CONNECTED", color = Color(0xFF7CE9FF), fontSize = 10.sp)
                            Text("${pairing!!.ip}:${pairing!!.port}", color = Color.White, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !sending, onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Add files") }
                                Button(enabled = !sending, onClick = { folderPicker.launch(null) }) { Text("Add folder") }
                                Button(enabled = !sending && queue.isNotEmpty(), onClick = { sendQueue() }) { Text(if (sending) "Sending $progress%" else "Send queue") }
                                Button(enabled = !sending, onClick = { store.clear(); pairing = null; queue = emptyList(); message = "Pairing removed" }) { Text("Forget") }
                            }
                        }
                    }
                }

                if (queue.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("QUEUE · ${queue.size}", color = Color(0xFF7CE9FF), fontSize = 10.sp)
                            queue.take(6).forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, color = Color.White, fontSize = 13.sp)
                                        Text("${item.relativePath} · ${item.size / 1024} KB", color = Color(0xFF858A9B), fontSize = 11.sp)
                                    }
                                    Button(onClick = { queue = queue.filterIndexed { i, _ -> i != index } }) { Text("×") }
                                }
                            }
                        }
                    }
                }

                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Find nearby devices", color = Color.White, fontSize = 20.sp)
                        Text("Use the same Wi‑Fi network for fast local transfers.", color = Color(0xFF858A9B), fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
                        Button(onClick = { scanning = true; discovery.scan { devices = it; scanning = false } }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(if (scanning) "Scanning…" else "Scan nearby devices")
                        }
                    }
                }

                Text("NEARBY", color = Color(0xFF666C7D), fontSize = 10.sp)
                if (devices.isEmpty()) Text("No devices found yet", color = Color(0xFF858A9B), fontSize = 13.sp)
                devices.forEach { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151824)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            Text(device.name, color = Color.White)
                            Text("${device.ip}:${device.port}", color = Color(0xFF7CE9FF), fontSize = 12.sp)
                        }
                    }
                }
                if (message.isNotBlank()) Text(message, color = Color(0xFFB8B9C8), fontSize = 12.sp)
            }

            incoming?.let { request ->
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Incoming transfer") },
                    text = { Column { Text(request.name, color = Color.White); Text("${request.size / 1024} KB · ${request.relativePath}", color = Color(0xFF858A9B)) } },
                    confirmButton = { Button(onClick = { receiver.approve(request.id, true); incoming = null }) { Text("Accept") } },
                    dismissButton = { Button(onClick = { receiver.approve(request.id, false); incoming = null }) { Text("Decline") } }
                )
            }
        }
    }
}
