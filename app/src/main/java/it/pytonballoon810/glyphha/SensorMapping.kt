package it.pytonballoon810.glyphha

enum class UseCaseType {
    TRACK_3D_PRINTER_PROGRESS
}

data class SensorMapping(
    val useCase: UseCaseType = UseCaseType.TRACK_3D_PRINTER_PROGRESS,
    val progressEntityId: String,
    val maxValue: Double = 100.0,
    val remainingTimeEntityId: String? = null,
    val interruptedEntityId: String? = null
)

data class SensorState(
    val value: Double?,
    val rawState: String,
    val unit: String?
)
