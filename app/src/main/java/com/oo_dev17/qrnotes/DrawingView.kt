package com.oo_dev17.qrnotes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private lateinit var eraseButton: Button
    private lateinit var colorButton: Button
    private lateinit var rectangleButton: Button
    private var defaultBackgroundColor: Int = Color.BLACK
    private var rectangleMode = false
    private val strokes = mutableListOf<Stroke>()
    private val undoneStrokes = mutableListOf<Stroke>()
    private var currentMode = ToolMode.DRAW
    private var currentPath: Path? = null
    private var currentPaint: Paint = createPaint(Color.WHITE, 8f)
    private val listOfAvailableColors =
        mutableListOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK)
    private var currentColor = Color.WHITE
    private val actions = mutableListOf<Action>()
    private val undoneActions = mutableListOf<Action>()
    val erased = mutableListOf<Stroke>()
    private var startX = 0f
    private var startY = 0f

    private fun createPaint(color: Int, strokeWidth: Float): Paint {
        return Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    fun attachEraseButton(btn: Button) {
        eraseButton = btn
    }

    fun attachColorButton(btn: Button) {
        colorButton = btn
    }

    fun attachRectangleButton(btn: Button) {
        rectangleButton = btn
        val background = rectangleButton.background
        if (background is ColorDrawable) {
            defaultBackgroundColor = background.color
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        strokes.forEach {
            canvas.drawPath(it.path, it.paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val x = event.x
        val y = event.y

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                if (currentMode == ToolMode.DRAW) {
                    if (rectangleMode) {
                        startX = x
                        startY = y
                        currentPath = Path()
                        val stroke = Stroke(currentPath!!, currentPaint)
                        strokes.add(stroke)
                        actions.add(Action.Draw(stroke))
                        undoneActions.clear()
                    } else {
                        currentPath = Path().apply {
                            moveTo(x, y)
                        }
                        val stroke = Stroke(currentPath!!, currentPaint)
                        strokes.add(stroke)
                        actions.add(Action.Draw(stroke))
                        undoneActions.clear()
                    }
                } else if (currentMode == ToolMode.ERASE) {

                    val iterator = strokes.iterator()
                    while (iterator.hasNext()) {
                        val stroke = iterator.next()
                        if (isStrokeHit(stroke, x, y)) {
                            iterator.remove()
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentMode == ToolMode.DRAW) {
                    if (rectangleMode) {
                        currentPath?.apply {
                            reset()
                            val rect = RectF(startX, startY, x, y)
                            rect.sort()
                            addRect(rect, Path.Direction.CW)
                        }
                    } else {
                        currentPath?.lineTo(x, y)
                    }
                } else if (currentMode == ToolMode.ERASE) {

                    val iterator = strokes.iterator()
                    while (iterator.hasNext()) {
                        val stroke = iterator.next()
                        if (isStrokeHit(stroke, x, y)) {
                            val iterator = strokes.iterator()
                            while (iterator.hasNext()) {
                                val stroke = iterator.next()
                                if (isStrokeHit(stroke, x, y)) {
                                    erased.add(stroke)
                                    iterator.remove()
                                }
                            }

                            if (erased.isNotEmpty()) {
                                actions.add(Action.Erase(erased))
                                undoneActions.clear()
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                currentPath = null
            }
        }
        invalidate()
        return true
    }

    fun rectangleMode() {
        rectangleMode = !rectangleMode
        rectangleButton.setBackgroundColor(if (rectangleMode) Color.WHITE else defaultBackgroundColor)
    }

    fun changeColor() {
        var index = listOfAvailableColors.indexOf(currentColor)
        index++
        if (index >= listOfAvailableColors.size) {
            index = 0
        }
        currentColor = listOfAvailableColors[index]
        colorButton.setTextColor(currentColor)
        currentPaint = createPaint(currentColor, currentPaint.strokeWidth)
    }

    fun setStrokeWidth(width: Float) {
        currentPaint = createPaint(currentPaint.color, width)
    }

    fun undo() {
        if (actions.isEmpty()) return

        val lastAction = actions.removeAt(actions.size - 1)
        undoneActions.add(lastAction)

        when (lastAction) {
            is Action.Draw -> {
                strokes.remove(lastAction.stroke)
            }

            is Action.Erase -> {
                strokes.addAll(lastAction.erasedStrokes)
            }
        }

        invalidate()
    }

    fun redo() {
        if (undoneActions.isEmpty()) return

        val action = undoneActions.removeAt(undoneActions.size - 1)
        actions.add(action)

        when (action) {
            is Action.Draw -> {
                strokes.add(action.stroke)
            }

            is Action.Erase -> {
                strokes.removeAll(action.erasedStrokes)
            }
        }

        invalidate()
    }

    fun erase() {
        setMode(if (currentMode == ToolMode.ERASE) ToolMode.DRAW else ToolMode.ERASE)
        eraseButton?.text = currentMode.toString()
    }

    private fun isStrokeHit(stroke: Stroke, x: Float, y: Float): Boolean {
        val bounds = RectF()
        stroke.path.computeBounds(bounds, true)

        return bounds.contains(x, y)
    }

    fun setMode(mode: ToolMode) {
        currentMode = mode
    }

    fun clearCanvas() {
        strokes.clear()
        undoneStrokes.clear()
        invalidate()
    }

    enum class ToolMode {
        DRAW, ERASE
    }

    data class Stroke(
        val path: Path, val paint: Paint
    )

    sealed class Action {
        data class Draw(val stroke: Stroke) : Action()
        data class Erase(val erasedStrokes: List<Stroke>) : Action()
    }
}
