package pl.cardioscp.rehab.bluetooth.protocol

/** Monotonic ushort sequence for outbound app frames. */
class SequenceGenerator(start: Int = 1) {
    private var next = start and 0xFFFF

    @Synchronized
    fun next(): Int {
        val value = next
        next = (next + 1) and 0xFFFF
        return value
    }
}
