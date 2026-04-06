package it.pytonballoon810.glyphha

enum class UseCaseType {
    TRACK_3D_PRINTER_PROGRESS,
    TRACK_GENERIC_SENSOR
}

enum class GenericDisplayMode {
    PROGRESS,
    NUMBER
}

data class SensorMapping(
    val useCase: UseCaseType = UseCaseType.TRACK_3D_PRINTER_PROGRESS,
    val progressEntityId: String,
    val maxValue: Double = 100.0,
    val remainingTimeEntityId: String? = null,
    val interruptedEntityId: String? = null,
    val genericDisplayMode: GenericDisplayMode = GenericDisplayMode.NUMBER,
    val turnOffValue: String? = null,
    val resetValue: String? = null,
    val genericErrorEntityId: String? = null,
    val genericErrorTriggerValue: String? = null
)

data class SensorState(
    val value: Double?,
    val rawState: String,
    val unit: String?
)
