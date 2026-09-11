package ir.redlighte.seamshare.network

/**
 * Chooses the best currently reachable local transport.
 * Bluetooth and relay remain reserved for their native backends; until then
 * they are not advertised as available candidates.
 */
object SeamTransportSelector {
    fun localCandidates(host: String, port: Int): List<TransportCandidate> = listOf(
        TransportCandidate(
            type = TransportType.DIRECT_LAN,
            host = host,
            port = port,
            available = host.isNotBlank() && port > 0,
            priority = 10
        ),
        TransportCandidate(
            type = TransportType.NEARBY_HOTSPOT,
            host = host,
            port = port,
            available = host.isNotBlank() && port > 0,
            priority = 20
        )
    )

    fun select(candidates: List<TransportCandidate>): TransportCandidate? =
        SeamTransportRegistry.rank(candidates).firstOrNull()
}
