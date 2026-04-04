package it.pytonballoon810.glyphha

enum class CompletionIconType {
    PRINTER,
    CHECK,
    TROPHY,
    CUSTOM
}

data class CustomIconData(
    val size: Int,
    val activePixels: Set<Int>
)
