package ir.redlighte.seamshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.redlighte.seamshare.network.SeamDiscovery

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SeamShareApp() } }
}

@Composable
private fun SeamShareApp() {
    var devices by remember { mutableStateOf<List<SeamDiscovery.Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val discovery = remember { SeamDiscovery() }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C5CFF), secondary = Color(0xFF00D9FF))) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("SEAM Share", color = Color.White, fontSize = 22.sp)
                Text("Move freely. Stay connected.", color = Color(0xFF7E8393), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11141E))) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Share without the friction.", color = Color.White, fontSize = 24.sp)
                        Text("Find nearby SEAM Share devices on your Wi‑Fi.", color = Color(0xFF858A9B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Button(onClick = { scanning=true; discovery.scan { devices=it; scanning=false } }, modifier = Modifier.padding(top = 20.dp)) { Text(if(scanning) "Scanning…" else "Scan nearby devices") }
                    }
                }
                Text("NEARBY", color = Color(0xFF666C7D), fontSize = 10.sp)
                if (devices.isEmpty()) Text("No devices found yet", color = Color(0xFF858A9B), fontSize = 13.sp)
                devices.forEach { device -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151824)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(device.name, color=Color.White); Text("${device.ip}:${device.port}", color=Color(0xFF7CE9FF), fontSize=12.sp) } } }
            }
        }
    }
}
