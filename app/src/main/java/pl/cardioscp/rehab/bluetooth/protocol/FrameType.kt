package pl.cardioscp.rehab.bluetooth.protocol

enum class FrameType(val code: Int) {
    ACK(0x01),
    COMMAND_ERROR(0x02),
    DEVICE_ERROR(0x03),
    INIT(0x04),
    ECG_OFFLINE(0x05),
    ECG_OFFLINE_DONE(0x06),
    GET_SCP_INFO(0x07),
    SCP_INFO(0x08),
    GET_SCP(0x09),
    SCP_FRAGMENT(0x0A),
    SCP_DONE(0x0B),
    GET_PULSE(0x0C),
    PULSE_VALUE(0x0D),
    ECG_ONLINE(0x0E),
    ECG_ONLINE_INFO(0x0F),
    ECG_ONLINE_DATA(0x10),
    ECG_ONLINE_STOP(0x11),
    END(0x12),
    GET(0x13),
    GET_ANS(0x14),
    ECG_OFFLINE_STOP(0x15),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Int): FrameType? = byCode[code and 0xFF]
    }
}
