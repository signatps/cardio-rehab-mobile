package pl.cardioscp.rehab.ecg

enum class Lead(val label: String) {
    I("I"),
    II("II"),
    III("III"),
    AVR("aVR"),
    AVL("aVL"),
    AVF("aVF"),
    V1("V1"),
    V2("V2"),
    V3("V3"),
    V4("V4"),
    V5("V5"),
    V6("V6");

    val measured: Boolean
        get() = this == I || this == II || this == V1 || this == V2 || this == V3 ||
            this == V4 || this == V5 || this == V6

    companion object {
        val measuredOrder = listOf(I, II, V1, V2, V3, V4, V5, V6)
        val displayOrder = listOf(I, II, III, AVR, AVL, AVF, V1, V2, V3, V4, V5, V6)

        fun fromLabel(label: String): Lead = entries.first { it.label == label }
    }
}
