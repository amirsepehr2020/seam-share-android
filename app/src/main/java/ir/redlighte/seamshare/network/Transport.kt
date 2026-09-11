package ir.redlighte.seamshare.network

/** Shared transport abstraction. Encryption and transfer queue stay above this layer. */
enum class TransportType { DIRECT_LAN, NEARBY_HOTSPOT, BLUETOOTH, RELAY }

data class TransportCandidate(
    val type: TransportType,
    val host: String? = null,
    val port: Int? = null,
    val available: Boolean = true,
    val priority: Int
)

interface SeamTransport {
    val type: TransportType
    fun isAvailable(): Boolean
}

object SeamTransportRegistry {
    fun rank(candidates: List<TransportCandidate>): List<TransportCandidate> =
        candidates.filter { it.available }.sortedBy { it.priority }
}
