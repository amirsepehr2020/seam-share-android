package ir.redlighte.seamshare.network

/**
 * Transport is deliberately below TransferManager and above E2E framing.
 * Every transport carries the same encrypted transfer payload.
 */
enum class SeamTransportType {
    DIRECT_LAN,
    NEARBY_HOTSPOT,
    BLUETOOTH,
    RELAY
}

data class SeamTransportTarget(
    val host: String? = null,
    val port: Int? = null,
    val relayUrl: String? = null
)

data class SeamTransportCandidate(
    val type: SeamTransportType,
    val target: SeamTransportTarget,
    val available: Boolean,
    val score: Int,
    val reason: String? = null
)

interface SeamTransport {
    val type: SeamTransportType
    fun isAvailable(target: SeamTransportTarget): Boolean
}

object SeamTransportSelector {
    fun select(candidates: List<SeamTransportCandidate>): SeamTransportCandidate? =
        candidates.filter { it.available }.maxByOrNull { it.score }

    fun localCandidates(host: String, port: Int): List<SeamTransportCandidate> = listOf(
        SeamTransportCandidate(
            SeamTransportType.DIRECT_LAN,
            SeamTransportTarget(host, port),
            available = true,
            score = 100
        ),
        SeamTransportCandidate(
            SeamTransportType.NEARBY_HOTSPOT,
            SeamTransportTarget(host, port),
            available = true,
            score = 80,
            reason = "Uses the same local transfer protocol over a hotspot/private network"
        )
    )
}
