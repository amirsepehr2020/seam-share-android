package ir.redlighte.seamshare.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class SeamDiscovery {
    data class Device(val name: String, val ip: String, val port: Int)
    private val executor = Executors.newSingleThreadExecutor()
    fun scan(onResult: (List<Device>) -> Unit) {
        executor.execute {
            val found = linkedMapOf<String, Device>()
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val payload = "SEAM_SHARE_DISCOVER_V2|Android|38948".toByteArray()
                    socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 38947))
                    socket.soTimeout = 160
                    val deadline = System.currentTimeMillis() + 1800
                    val buffer = ByteArray(1024)
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val text = String(packet.data, 0, packet.length)
                            val parts = text.split('|')
                            if (parts.size == 3 && parts[0] == "SEAM_SHARE_DISCOVER_V2") {
                                parts[2].toIntOrNull()?.let { port -> found[packet.address.hostAddress ?: ""] = Device(parts[1], packet.address.hostAddress ?: "", port) }
                            }
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
            onResult(found.values.toList())
        }
    }
}
