package pl.cardioscp.rehab.bluetooth.protocol

class CommandFactory(
    private val sequences: SequenceGenerator = SequenceGenerator(),
) {
    fun ack(ofSequence: Int): ProtocolFrame =
        ProtocolFrame(FrameType.ACK, ofSequence)

    fun init(
        unixTimestampSeconds: Long,
        samplingHz: Int,
        pulseAverageSeconds: Int,
        clearBuffer: Boolean,
        extraLeads: ByteArray = ByteArray(0),
    ): ProtocolFrame = ProtocolFrame(
        FrameType.INIT,
        sequences.next(),
        PayloadCodec.init(
            unixTimestampSeconds,
            samplingHz,
            pulseAverageSeconds,
            clearBuffer,
            extraLeads,
        ),
    )

    fun ecgOffline(lookbackSeconds: Int, totalSeconds: Int, userIdUtf8: String): ProtocolFrame =
        ProtocolFrame(
            FrameType.ECG_OFFLINE,
            sequences.next(),
            PayloadCodec.ecgOffline(lookbackSeconds, totalSeconds, userIdUtf8),
        )

    fun getScpInfo(): ProtocolFrame =
        ProtocolFrame(FrameType.GET_SCP_INFO, sequences.next())

    fun getScp(): ProtocolFrame =
        ProtocolFrame(FrameType.GET_SCP, sequences.next())

    fun scpDone(): ProtocolFrame =
        ProtocolFrame(FrameType.SCP_DONE, sequences.next())

    fun getPulse(intervalTenthsOfSecond: Int): ProtocolFrame =
        ProtocolFrame(
            FrameType.GET_PULSE,
            sequences.next(),
            PayloadCodec.getPulse(intervalTenthsOfSecond),
        )

    fun end(): ProtocolFrame =
        ProtocolFrame(FrameType.END, sequences.next())

    fun get(infoId: Int): ProtocolFrame =
        ProtocolFrame(FrameType.GET, sequences.next(), PayloadCodec.get(infoId))

    fun ecgOfflineStop(): ProtocolFrame =
        ProtocolFrame(FrameType.ECG_OFFLINE_STOP, sequences.next())

    fun ecgOnline(): ProtocolFrame =
        ProtocolFrame(FrameType.ECG_ONLINE, sequences.next())

    fun ecgOnlineStop(): ProtocolFrame =
        ProtocolFrame(FrameType.ECG_ONLINE_STOP, sequences.next())
}
