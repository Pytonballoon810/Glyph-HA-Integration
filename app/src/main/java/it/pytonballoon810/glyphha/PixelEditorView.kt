package it.pytonballoon810.glyphha

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor

class PixelEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var matrixSize: Int = 13
    private var cellSize: Float = 1f
    private var activePixels: MutableSet<Int> = mutableSetOf()

    private val onPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        isAntiAlias = false
    }
    private val offPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.rgb(35, 45, 55)
        isAntiAlias = false
    }
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.rgb(70, 85, 100)
        isAntiAlias = false
    }

    fun setMatrixSize(size: Int) {
        matrixSize = size.coerceAtLeast(5)
        activePixels = activePixels.filter { it < matrixSize * matrixSize }.toMutableSet()
        requestLayout()
        invalidate()
    }

    fun setActivePixels(data: Set<Int>) {
        activePixels = data.filter { it in 0 until matrixSize * matrixSize }.toMutableSet()
        invalidate()
    }

    fun clearPixels() {
        activePixels.clear()
        invalidate()
    }

    fun getCustomIconData(): CustomIconData {
        return CustomIconData(matrixSize, activePixels.toSet())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = 700
        val width = resolveSize(desired, widthMeasureSpec)
        setMeasuredDimension(width, width)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellSize = width.toFloat() / matrixSize

        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                val index = y * matrixSize + x
                val left = x * cellSize
                val top = y * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                canvas.drawRect(left, top, right, bottom, if (index in activePixels) onPaint else offPaint)
                canvas.drawRect(left, top, right, bottom, gridPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val x = floor(event.x / cellSize).toInt()
            val y = floor(event.y / cellSize).toInt()
            if (x in 0 until matrixSize && y in 0 until matrixSize) {
                val index = y * matrixSize + x
                if (index in activePixels) {
                    activePixels.remove(index)
                } else {
                    activePixels.add(index)
                }
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
