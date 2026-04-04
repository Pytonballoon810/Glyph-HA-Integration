package it.pytonballoon810.glyphha

enum class DisplayMode {
    PROGRESS,
    RAW_NUMBER
}

data class SensorMapping(
    val entityId: String,
    val mode: DisplayMode,
    val maxValue: Double = 100.0,
    val secondaryTextEntityId: String? = null
)

data class SensorState(
    val value: Double?,
    val rawState: String,
    val unit: String?
)
