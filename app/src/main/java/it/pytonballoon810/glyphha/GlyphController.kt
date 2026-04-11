package it.pytonballoon810.glyphha

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

class GlyphController(private val context: Context) {
    private val matrixManager = GlyphMatrixManager.getInstance(context.applicationContext)
    private val renderStatePrefs by lazy {
        context.applicationContext.getSharedPreferences(RENDER_STATE_PREFS, Context.MODE_PRIVATE)
    }
    private var initialized = false
    private val matrixSize: Int
        get() = Common.getDeviceMatrixLength().coerceAtLeast(1)

    fun start() {
        if (initialized) return
        matrixManager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                val target = if (Common.is25111p()) Glyph.DEVICE_25111p else Glyph.DEVICE_23112
                matrixManager.register(target)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                initialized = false
            }
        })
        initialized = true
    }

    fun stop() {
        if (!initialized) return

        try {
            matrixManager.closeAppMatrix()
        } catch (_: Exception) {
        }

        try {
            matrixManager.unInit()
        } catch (_: Exception) {
        }

        initialized = false
    }

    fun render(mapping: SensorMapping, state: SensorState) {
        if (!initialized) return
        val value = state.value ?: return
        val denominator = mapping.maxValue.coerceAtLeast(1.0)
        renderProgressRatio((value / denominator).coerceIn(0.0, 1.0))
    }

    fun clearAppDisplay() {
        try {
            matrixManager.closeAppMatrix()
        } catch (_: Exception) {
        }
        saveCurrentRenderData("OFF")
    }

    private fun renderRawNumber(state: SensorState) {
        val text = state.value?.let { String.format("%.1f", it) } ?: state.rawState
        renderRawText(text)
    }

    fun renderInterruptedX() {
        val interruptedBitmap = buildCompletionIconBitmap(
            size = matrixSize,
            iconType = CompletionIconType.CROSS,
            customIconData = null
        )
        val iconObject = GlyphMatrixObject.Builder()
            .setImageSource(interruptedBitmap)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(iconObject)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }

        saveCurrentRenderData("INTERRUPTED")
    }

    fun renderRawText(text: String) {
        val currentText = text.take(8)
        val obj = GlyphMatrixObject.Builder()
            .setText(currentText)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(obj)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }

        saveCurrentRenderData(currentText)
    }

    fun renderProgressRatio(
        ratio: Double,
        subText: String? = null,
        scrollOffsetPx: Int = 0
    ): Boolean {
        val side = matrixSize
        val clamped = ratio.coerceIn(0.0, 1.0)
        val normalizedSubText = subText?.trim().orEmpty().take(48)
        val hasSubText = normalizedSubText.isNotBlank()

        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val trackPaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val outlinePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        // Fixed bar geometry: exactly 3 pixels tall.
        val horizontalPadding = if (side >= 20) 2 else 1
        val barLeft = horizontalPadding
        val barRight = side - horizontalPadding - 1
        val barTop = if (hasSubText) {
            (side / 2 - 3).coerceAtLeast(1)
        } else {
            ((side - 3) / 2).coerceAtLeast(1)
        }
        val barBottom = (barTop + 2).coerceAtMost(side - 1)

        // Outline is always visible regardless of progress.
        canvas.drawRect(
            barLeft.toFloat(),
            barTop.toFloat(),
            (barRight + 1).toFloat(),
            (barBottom + 1).toFloat(),
            outlinePaint
        )

        val innerTop = (barTop + 1).coerceAtMost(barBottom)
        val innerBottom = (barBottom - 1).coerceAtLeast(barTop)
        val innerLeft = (barLeft + 1).coerceAtMost(barRight)
        val innerRight = (barRight - 1).coerceAtLeast(barLeft)
        val innerWidth = (innerRight - innerLeft + 1).coerceAtLeast(1)
        val fillWidth = (innerWidth * clamped).toInt().coerceIn(0, innerWidth)

        // Reset inner bar to dark each frame, then fill only as far as progress should go.
        canvas.drawRect(
            innerLeft.toFloat(),
            innerTop.toFloat(),
            (innerRight + 1).toFloat(),
            (innerBottom + 1).toFloat(),
            trackPaint
        )

        if (fillWidth > 0) {
            canvas.drawRect(
                innerLeft.toFloat(),
                innerTop.toFloat(),
                (innerLeft + fillWidth).toFloat(),
                (innerBottom + 1).toFloat(),
                fillPaint
            )
        }

        // Arrow points to the next pixel position that will light up.
        val nextPixelX = (innerLeft + fillWidth).coerceIn(innerLeft, innerRight)
        drawDownArrow(canvas, nextPixelX, barTop - 1, side, fillPaint)

        val hasOverflow = if (hasSubText) {
            drawSubTextTicker(canvas, normalizedSubText, side, barBottom + 2, scrollOffsetPx, fillPaint)
        } else {
            false
        }

        val progressObject = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(progressObject)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }

        val summary = if (hasSubText) {
            "${(clamped * 100).toInt()}% | ${normalizedSubText.take(24)}"
        } else {
            "${(clamped * 100).toInt()}%"
        }
        saveCurrentRenderData(summary)

        return hasOverflow
    }

    private fun drawSubTextTicker(
        canvas: Canvas,
        rawText: String,
        side: Int,
        startY: Int,
        scrollOffsetPx: Int,
        paint: Paint
    ): Boolean {
        val text = rawText.uppercase()
        val glyphHeight = 5
        if (startY + glyphHeight > side) return false

        val contentWidth = getPixelTextWidth(text)
        val drawPadding = 1
        val availableWidth = (side - (drawPadding * 2)).coerceAtLeast(1)
        val overflowPx = (contentWidth - availableWidth).coerceAtLeast(0)
        val overflow = overflowPx > MIN_SCROLL_OVERFLOW_PX

        val baseX = if (overflow) {
            val cycle = contentWidth + side + 4
            side - (scrollOffsetPx % cycle)
        } else {
            drawPadding
        }

        drawPixelText(canvas, text, baseX, startY, paint)
        return overflow
    }

    private fun getPixelTextWidth(text: String): Int {
        if (text.isEmpty()) return 0
        var width = 0
        text.forEachIndexed { index, ch ->
            width += charPattern(ch)[0].length
            if (index != text.lastIndex) width += 1
        }
        return width
    }

    private fun drawPixelText(canvas: Canvas, text: String, startX: Int, startY: Int, paint: Paint) {
        var xCursor = startX
        text.forEachIndexed { index, ch ->
            val pattern = charPattern(ch)
            pattern.forEachIndexed { row, rowPattern ->
                rowPattern.forEachIndexed { col, pixel ->
                    if (pixel == '1') {
                        canvas.drawPoint((xCursor + col).toFloat(), (startY + row).toFloat(), paint)
                    }
                }
            }
            xCursor += pattern[0].length
            if (index != text.lastIndex) xCursor += 1
        }
    }

    private fun charPattern(ch: Char): Array<String> {
        return when (ch) {
            '0' -> arrayOf("111", "101", "101", "101", "111")
            '1' -> arrayOf("010", "110", "010", "010", "111")
            '2' -> arrayOf("111", "001", "111", "100", "111")
            '3' -> arrayOf("111", "001", "111", "001", "111")
            '4' -> arrayOf("101", "101", "111", "001", "001")
            '5' -> arrayOf("111", "100", "111", "001", "111")
            '6' -> arrayOf("111", "100", "111", "101", "111")
            '7' -> arrayOf("111", "001", "010", "010", "010")
            '8' -> arrayOf("111", "101", "111", "101", "111")
            '9' -> arrayOf("111", "101", "111", "001", "111")
            'A' -> arrayOf("111", "101", "111", "101", "101")
            'B' -> arrayOf("110", "101", "110", "101", "110")
            'C' -> arrayOf("111", "100", "100", "100", "111")
            'D' -> arrayOf("110", "101", "101", "101", "110")
            'E' -> arrayOf("111", "100", "110", "100", "111")
            'F' -> arrayOf("111", "100", "110", "100", "100")
            'G' -> arrayOf("111", "100", "101", "101", "111")
            'H' -> arrayOf("101", "101", "111", "101", "101")
            'I' -> arrayOf("111", "010", "010", "010", "111")
            'J' -> arrayOf("111", "001", "001", "101", "111")
            'K' -> arrayOf("101", "101", "110", "101", "101")
            'L' -> arrayOf("100", "100", "100", "100", "111")
            'M' -> arrayOf("101", "111", "111", "101", "101")
            'N' -> arrayOf("101", "111", "111", "111", "101")
            'O' -> arrayOf("111", "101", "101", "101", "111")
            'P' -> arrayOf("111", "101", "111", "100", "100")
            'Q' -> arrayOf("111", "101", "101", "111", "001")
            'R' -> arrayOf("111", "101", "111", "110", "101")
            'S' -> arrayOf("111", "100", "111", "001", "111")
            'T' -> arrayOf("111", "010", "010", "010", "010")
            'U' -> arrayOf("101", "101", "101", "101", "111")
            'V' -> arrayOf("101", "101", "101", "101", "010")
            'W' -> arrayOf("101", "101", "111", "111", "101")
            'X' -> arrayOf("101", "101", "010", "101", "101")
            'Y' -> arrayOf("101", "101", "010", "010", "010")
            'Z' -> arrayOf("111", "001", "010", "100", "111")
            ':' -> arrayOf("0", "1", "0", "1", "0")
            '.' -> arrayOf("0", "0", "0", "0", "1")
            '-' -> arrayOf("000", "000", "111", "000", "000")
            '/' -> arrayOf("001", "001", "010", "100", "100")
            ' ' -> arrayOf("0", "0", "0", "0", "0")
            else -> arrayOf("0", "0", "0", "0", "0")
        }
    }

    private fun drawDownArrow(canvas: Canvas, centerX: Int, tipY: Int, side: Int, paint: Paint) {
        if (tipY < 0) return

        // Exact 5x4 pattern (o=off, x=on):
        // ooxoo
        // xoxox
        // oxoxo
        // ooxoo
        val pattern = arrayOf(
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(true, false, true, false, true),
            booleanArrayOf(false, true, false, true, false),
            booleanArrayOf(false, false, true, false, false)
        )

        val width = 5
        val leftX = centerX - (width / 2)
        val topY = tipY - (pattern.size - 1)

        for (row in pattern.indices) {
            val y = topY + row
            if (y !in 0 until side) continue
            for (col in 0 until width) {
                if (!pattern[row][col]) continue
                val x = leftX + col
                if (x in 0 until side) {
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }
    }

    fun renderCompletionBlink(
        showIcon: Boolean,
        iconType: CompletionIconType,
        customIconData: CustomIconData?
    ) {
        if (!showIcon) {
            val offFrame = IntArray(matrixSize * matrixSize) { 0 }
            try {
                matrixManager.setAppMatrixFrame(offFrame)
            } catch (_: GlyphException) {
            }
            saveCurrentRenderData("OFF")
            return
        }

        val completionIcon = buildCompletionIconBitmap(matrixSize, iconType, customIconData)
        val iconObject = GlyphMatrixObject.Builder()
            .setImageSource(completionIcon)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(iconObject)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }

        saveCurrentRenderData("ICON: ${iconType.name}")
    }

    fun getCurrentRenderData(): String {
        return renderStatePrefs.getString(KEY_CURRENT_RENDER_DATA, "OFF") ?: "OFF"
    }

    private fun saveCurrentRenderData(data: String) {
        renderStatePrefs.edit().putString(KEY_CURRENT_RENDER_DATA, data).apply()
    }

    private fun buildCompletionIconBitmap(
        size: Int,
        iconType: CompletionIconType,
        customIconData: CustomIconData?
    ): Bitmap {
        if (iconType != CompletionIconType.CUSTOM) {
            val fromAssets = loadIconBitmapFromAssets(iconType, size)
            if (fromAssets != null) return fromAssets
        }

        return when (iconType) {
            CompletionIconType.PRINTER -> buildPrinterBitmap(size)
            CompletionIconType.CHECK -> buildCheckBitmap(size)
            CompletionIconType.CROSS -> buildCrossBitmap(size)
            CompletionIconType.TROPHY -> buildTrophyBitmap(size)
            CompletionIconType.CUSTOM -> buildCustomBitmap(size, customIconData)
        }
    }

    private fun loadIconBitmapFromAssets(iconType: CompletionIconType, size: Int): Bitmap? {
        val iconName = when (iconType) {
            CompletionIconType.PRINTER -> "printer"
            CompletionIconType.CHECK -> "check"
            CompletionIconType.CROSS -> "cross"
            CompletionIconType.TROPHY -> "trophy"
            CompletionIconType.CUSTOM -> return null
        }

        val bucket = if (size >= 20) "25" else "13"
        val path = "icons/$bucket/$iconName.txt"

        val lines = try {
            context.assets.open(path).bufferedReader().use { reader ->
                reader.readLines().map { it.trimEnd() }.filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            return null
        }

        if (lines.isEmpty()) return null
        return buildPatternBitmap(size, lines)
    }

    private fun buildPrinterBitmap(size: Int): Bitmap {
        val pattern = listOf(
            ".............",
            "..XXXXXXXXX..",
            "..X.......X..",
            "..X..XXX..X..",
            "..X...X...X..",
            "..X...X...X..",
            "..X...X...X..",
            "..X..XXX..X..",
            "..X..X.X..X..",
            "..X.XXXXX.X..",
            "..X.......X..",
            "..XXXXXXXXX..",
            "............."
        )
        return buildPatternBitmap(size, pattern)
    }

    private fun buildCheckBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        var x = (size * 0.2f).toInt()
        var y = (size * 0.55f).toInt()
        while (x < (size * 0.42f).toInt() && y < (size * 0.78f).toInt()) {
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + 2).toFloat(), (y + 2).toFloat(), paint)
            x += 1
            y += 1
        }

        var x2 = (size * 0.38f).toInt()
        var y2 = (size * 0.74f).toInt()
        while (x2 < (size * 0.82f).toInt() && y2 > (size * 0.26f).toInt()) {
            canvas.drawRect(x2.toFloat(), y2.toFloat(), (x2 + 2).toFloat(), (y2 + 2).toFloat(), paint)
            x2 += 1
            y2 -= 1
        }

        return bitmap
    }

    private fun buildTrophyBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        canvas.drawRect((size * 0.25f), (size * 0.18f), (size * 0.75f), (size * 0.42f), paint)
        canvas.drawRect((size * 0.18f), (size * 0.2f), (size * 0.25f), (size * 0.34f), paint)
        canvas.drawRect((size * 0.75f), (size * 0.2f), (size * 0.82f), (size * 0.34f), paint)
        canvas.drawRect((size * 0.45f), (size * 0.42f), (size * 0.55f), (size * 0.62f), paint)
        canvas.drawRect((size * 0.34f), (size * 0.62f), (size * 0.66f), (size * 0.72f), paint)

        return bitmap
    }

    private fun buildCrossBitmap(size: Int): Bitmap {
        val pattern = listOf(
            "XX.........XX",
            ".XX.......XX.",
            "..XX.....XX..",
            "...XX...XX...",
            "....XX.XX....",
            ".....XXX.....",
            "......X......",
            ".....XXX.....",
            "....XX.XX....",
            "...XX...XX...",
            "..XX.....XX..",
            ".XX.......XX.",
            "XX.........XX"
        )
        return buildPatternBitmap(size, pattern)
    }

    private fun buildCustomBitmap(size: Int, customIconData: CustomIconData?): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val custom = customIconData ?: return buildPrinterBitmap(size)
        if (custom.size <= 0) return buildPrinterBitmap(size)

        val sourceSize = custom.size
        custom.activePixels.forEach { index ->
            val x = index % sourceSize
            val y = index / sourceSize
            if (x !in 0 until sourceSize || y !in 0 until sourceSize) return@forEach

            val dstLeft = (x * size) / sourceSize
            val dstTop = (y * size) / sourceSize
            val dstRight = ((x + 1) * size) / sourceSize
            val dstBottom = ((y + 1) * size) / sourceSize

            canvas.drawRect(dstLeft.toFloat(), dstTop.toFloat(), dstRight.toFloat(), dstBottom.toFloat(), paint)
        }

        return bitmap
    }

    private fun buildInterruptedBitmap(size: Int): Bitmap {
        val pattern = listOf(
            "XX.........XX",
            ".XX.......XX.",
            "..XX.....XX..",
            "...XX...XX...",
            "....XX.XX....",
            ".....XXX.....",
            "......X......",
            ".....XXX.....",
            "....XX.XX....",
            "...XX...XX...",
            "..XX.....XX..",
            ".XX.......XX.",
            "XX.........XX"
        )
        return buildPatternBitmap(size, pattern)
    }

    private fun buildPatternBitmap(size: Int, pattern: List<String>): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val sourceHeight = pattern.size.coerceAtLeast(1)
        val sourceWidth = (pattern.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)

        pattern.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch != 'X') return@forEachIndexed

                val dstLeft = (x * size) / sourceWidth
                val dstTop = (y * size) / sourceHeight
                val dstRight = ((x + 1) * size) / sourceWidth
                val dstBottom = ((y + 1) * size) / sourceHeight

                canvas.drawRect(dstLeft.toFloat(), dstTop.toFloat(), dstRight.toFloat(), dstBottom.toFloat(), paint)
            }
        }

        return bitmap
    }

    companion object {
        private const val RENDER_STATE_PREFS = "glyph_render_state"
        private const val KEY_CURRENT_RENDER_DATA = "current_render_data"
        private const val MIN_SCROLL_OVERFLOW_PX = 1
        private val SUPPORTED_MATRIX_SIZES = setOf(13, 25)

        fun isSupportedMatrixSize(matrixSize: Int): Boolean {
            return matrixSize in SUPPORTED_MATRIX_SIZES
        }
    }
}
