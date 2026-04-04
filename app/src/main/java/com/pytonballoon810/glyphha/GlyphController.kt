package com.pytonballoon810.glyphha

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
        try {
            matrixManager.closeAppMatrix()
        } catch (_: Exception) {
        }
        matrixManager.unInit()
        initialized = false
    }

    fun render(mapping: SensorMapping, state: SensorState) {
        if (!initialized) return

        when (mapping.mode) {
            DisplayMode.RAW_NUMBER -> renderRawNumber(state)
            DisplayMode.PROGRESS -> {
                val value = state.value ?: return
                val denominator = mapping.maxValue.coerceAtLeast(1.0)
                renderProgressRatio((value / denominator).coerceIn(0.0, 1.0))
            }
        }
    }

    fun clearAppDisplay() {
        try {
            matrixManager.closeAppMatrix()
        } catch (_: Exception) {
        }
    }

    private fun renderRawNumber(state: SensorState) {
        val text = state.value?.let { String.format("%.1f", it) } ?: state.rawState
        val obj = GlyphMatrixObject.Builder()
            .setText(text.take(8))
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(obj)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }
    }

    fun renderProgressRatio(ratio: Double) {
        val side = matrixSize
        val clamped = ratio.coerceIn(0.0, 1.0)

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

        // Fixed bar geometry: exactly 3 pixels tall, vertically centered.
        val horizontalPadding = if (side >= 20) 2 else 1
        val barLeft = horizontalPadding
        val barRight = side - horizontalPadding - 1
        val barTop = ((side - 3) / 2).coerceAtLeast(1)
        val barBottom = (barTop + 2).coerceAtMost(side - 1)
        val barWidth = (barRight - barLeft + 1).coerceAtLeast(1)

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

    fun renderCompletionBlink(showIcon: Boolean) {
        if (!showIcon) {
            val offFrame = IntArray(matrixSize * matrixSize) { 0 }
            try {
                matrixManager.setAppMatrixFrame(offFrame)
            } catch (_: GlyphException) {
            }
            return
        }

        val printerIcon = buildPrinterBitmap(matrixSize)
        val printerObject = GlyphMatrixObject.Builder()
            .setImageSource(printerIcon)
            .setBrightness(255)
            .build()

        val frame = GlyphMatrixFrame.Builder()
            .addTop(printerObject)
            .build(context)

        try {
            matrixManager.setAppMatrixFrame(frame)
        } catch (_: GlyphException) {
        }
    }

    private fun buildPrinterBitmap(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val unit = (size / 8f).coerceAtLeast(1f)
        val bodyLeft = (size * 0.15f).toInt()
        val bodyTop = (size * 0.32f).toInt()
        val bodyRight = (size * 0.85f).toInt()
        val bodyBottom = (size * 0.7f).toInt()
        canvas.drawRect(bodyLeft.toFloat(), bodyTop.toFloat(), bodyRight.toFloat(), bodyBottom.toFloat(), paint)

        val paperLeft = (size * 0.28f).toInt()
        val paperTop = (size * 0.06f).toInt()
        val paperRight = (size * 0.72f).toInt()
        val paperBottom = (size * 0.34f).toInt()
        canvas.drawRect(paperLeft.toFloat(), paperTop.toFloat(), paperRight.toFloat(), paperBottom.toFloat(), paint)

        val trayLeft = (size * 0.24f).toInt()
        val trayTop = (size * 0.68f).toInt()
        val trayRight = (size * 0.76f).toInt()
        val trayBottom = (size * 0.9f).toInt()
        canvas.drawRect(trayLeft.toFloat(), trayTop.toFloat(), trayRight.toFloat(), trayBottom.toFloat(), paint)

        // Abstract "3D print" nozzle indicator.
        canvas.drawRect((size / 2f - unit).toFloat(), (size * 0.42f), (size / 2f + unit).toFloat(), (size * 0.52f), paint)

        return bitmap
    }
}
