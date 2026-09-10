package ir.redlighte.seamshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ir.redlighte.seamshare.network.SeamDiscovery
import ir.redlighte.seamshare.network.SeamPairing
import ir.redlighte.seamshare.network.SeamPairingStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SeamShareApp() } }
}

@Composable
private fun SeamShareApp() {
    var devices by remember { mutableStateOf<List<SeamDiscovery.Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf<SeamPairing?>(null) }
    var message by remember { mutableStateOf("") }
    val discovery = remember { SeamDiscovery() }
    val store = remember { SeamPairingStore(androidx.compose.ui.platform.LocalContext.current) }
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents.isNullOrBlank()) { message = "QR scan cancelled" }
        else runCatching { SeamPairing.fromJson(result.contents) }.onSuccess { value -> store.save(value); pairing = value; message = "Paired with ${value.deviceName}" }.onFailure { message = "Invalid SEAM Share pairing code" }
    }
    LaunchedEffect(Unit) { pairing = store.load() }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C5CFF), secondary = Color(0xFF00D9FF))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("SEAM Share", color = Color.White, fontSize = 22.sp)
                Text("Move freely. Stay connected.", color = Color(0xFF7E8393), fontSize = 12.sp)
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (pairing == null) "Connect your PC" else pairing!!.deviceName, color = Color.White, fontSize = 24.sp)
                        Text(if (pairing == null) "Scan the QR code shown by SEAM Share on Windows." else "Paired over your local network.", color = Color(0xFF858A9B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { qrLauncher.launch(ScanOptions().apply { setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan the SEAM Share QR code on your PC"); setBeepEnabled(false); setOrientationLocked(false) }) }, modifier = Modifier.padding(top = 20.dp)) { Text("Scan pairing QR") }
                    }
                }
                if (pairing != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151824)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("CONNECTED", color = Color(0xFF7CE9FF), fontSize = 10.sp); Text("${pairing!!.ip}:${pairing!!.port}", color = Color.White, fontSize = 15.sp); Text("Your pairing is saved on this device.", color = Color(0xFF858A9B), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)); TextButton(onClick = { store.clear(); pairing = null; message = "Pairing removed" }) { Text("Forget device") } } }
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Find nearby devices", color = Color.White, fontSize = 20.sp)
                        Text("Use the same Wi‑Fi network for fast local transfers.", color = Color(0xFF858A9B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { scanning=true; discovery.scan { devices=it; scanning=false } }, modifier = Modifier.padding(top = 18.dp)) { Text(if(scanning) "Scanning…" else "Scan nearby devices") }
                    }
                }
                Text("NEARBY", color = Color(0xFF666C7D), fontSize = 10.sp)
                if (devices.isEmpty()) Text("No devices found yet", color = Color(0xFF858A9B), fontSize = 13.sp)
                devices.forEach { device -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151824)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(device.name, color=Color.White); Text("${device.ip}:${device.port}", color=Color(0xFF7CE9FF), fontSize=12.sp) } } }
                if (message.isNotBlank()) Text(message, color = Color(0xFFB8B9C8), fontSize = 12.sp)
            }
        }
    }
}
