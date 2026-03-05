package com.oo_dev17.qrnotes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private lateinit var eraseButton: Button
    private lateinit var colorButton: Button
    private lateinit var geometryButton: Button

    // store original state per-button so helper can be used for any button
    private val originalBackgrounds = mutableMapOf<Button, Drawable?>()
    private val originalTints = mutableMapOf<Button, ColorStateList?>()
    private val originalTextColors = mutableMapOf<Button, Int?>()

    private var geometryMode = GeometryMode.LINE
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
        geometryButton = btn
        saveOriginalStateIfNeeded(btn)
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
                    if (geometryMode == GeometryMode.RECTANGLE) {
                        startX = x
                        startY = y
                        currentPath = Path()
                        val stroke = Stroke(currentPath!!, currentPaint)
                        strokes.add(stroke)
                        actions.add(Action.Draw(stroke))
                        undoneActions.clear()
                    } else if (geometryMode == GeometryMode.CIRCLE) {
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
                    val erasedStrokes = mutableListOf<Stroke>()
                    val iterator = strokes.iterator()
                    while (iterator.hasNext()) {
                        val stroke = iterator.next()
                        if (isStrokeHit(stroke, x, y)) {
                            erasedStrokes.add(stroke)
                            iterator.remove()
                        }
                    }
                    if (erasedStrokes.isNotEmpty()) {
                        actions.add(Action.Erase(erasedStrokes))
                        undoneActions.clear()
                    }
                } else if (currentMode == ToolMode.SELECT) {
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentMode == ToolMode.DRAW) {
                    if (geometryMode== GeometryMode.RECTANGLE) {
                        currentPath?.apply {
                            reset()
                            val rect = RectF(startX, startY, x, y)
                            rect.sort()
                            addRect(rect, Path.Direction.CW)
                        }
                    } else if (geometryMode == GeometryMode.CIRCLE) {
                        // draw circle centered at startX,startY with radius to current point
                        currentPath?.apply {
                            reset()
                            val dx = x - startX
                            val dy = y - startY
                            val radius = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            addCircle(startX, startY, radius, Path.Direction.CW)
                        }
                    } else {
                        currentPath?.lineTo(x, y)
                    }
                } else if (currentMode == ToolMode.ERASE) {
                    val erasedStrokes = mutableListOf<Stroke>()
                    val iterator = strokes.iterator()
                    while (iterator.hasNext()) {
                        val stroke = iterator.next()
                        if (isStrokeHit(stroke, x, y)) {
                            erasedStrokes.add(stroke)
                            iterator.remove()
                        }
                    }
                    if (erasedStrokes.isNotEmpty()) {
                        actions.add(Action.Erase(erasedStrokes))
                        undoneActions.clear()
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

    // Public helper functions for button highlighting
    private fun saveOriginalStateIfNeeded(btn: Button) {
        if (originalBackgrounds.containsKey(btn)) return
        originalBackgrounds[btn] = btn.background?.constantState?.newDrawable()?.mutate()
        originalTints[btn] = btn.backgroundTintList
        originalTextColors[btn] = btn.currentTextColor
    }

    fun highlightButton(btn: Button, color: Int) {
        saveOriginalStateIfNeeded(btn)
        // Try to tint a fresh copy of the original drawable to preserve shape
        val orig = originalBackgrounds[btn]
        val newBg = orig?.constantState?.newDrawable()?.mutate()
        if (newBg != null) {
            val wrapped = DrawableCompat.wrap(newBg)
            DrawableCompat.setTintList(wrapped, ColorStateList.valueOf(color))
            btn.background = wrapped
        } else {
            // fallback to simple color drawable
            btn.background = ColorDrawable(color)
        }
        ViewCompat.setBackgroundTintMode(btn, PorterDuff.Mode.SRC_IN)
        btn.setTextColor(if (isColorDark(color)) Color.WHITE else Color.BLACK)
        btn.invalidate()
    }

    fun clearHighlight(btn: Button) {
        // restore original background and tint and text color
        val orig = originalBackgrounds[btn]
        if (orig != null) {
            btn.background = orig
        }
        ViewCompat.setBackgroundTintList(btn, originalTints[btn])
        originalTextColors[btn]?.let { btn.setTextColor(it) }
        btn.invalidate()
    }

    private fun isColorDark(color: Int): Boolean {
        // Perceived luminance
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        return luminance < 128
    }

    fun geometryMode() {
        // cycle geometry mode and update icon + show a short message
        val message = when (geometryMode) {
            GeometryMode.LINE -> {
                geometryMode = GeometryMode.RECTANGLE
                setButtonIcon(geometryButton, R.drawable.rectangle)
                "Rectangle"
            }
            GeometryMode.RECTANGLE -> {
                geometryMode = GeometryMode.CIRCLE
                setButtonIcon(geometryButton, R.drawable.open_circle)
                "Circle"
            }
            GeometryMode.CIRCLE -> {
                geometryMode = GeometryMode.LINE
                setButtonIcon(geometryButton, R.drawable.line)
                "Line"
            }
        }
        // show a small toast as a bubble/tea message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // helper to set icon and tint for any button
    private fun setButtonIcon(btn: Button, drawableResId: Int, tintColor: Int? = null) {
        val d = AppCompatResources.getDrawable(context, drawableResId)?.mutate() ?: return
        val wrapped = DrawableCompat.wrap(d)
        tintColor?.let { DrawableCompat.setTint(wrapped, it) }

        if (btn is MaterialButton) {
            btn.icon = wrapped
        } else {
            btn.setCompoundDrawablesWithIntrinsicBounds(null, wrapped, null, null)
            btn.compoundDrawablePadding = 0
        }
    }

    // try to tint an existing icon on a button; fallback to text color
    private fun tintExistingButtonIcon(btn: Button, color: Int) {
        if (btn is MaterialButton) {
            btn.icon?.let {
                val wrapped = DrawableCompat.wrap(it.mutate())
                DrawableCompat.setTint(wrapped, color)
                btn.icon = wrapped
            } ?: run {
                // no icon, fallback to text
                btn.setTextColor(color)
            }
        } else {
            val drawables = btn.compoundDrawables
            val top = drawables[1]
            if (top != null) {
                val wrapped = DrawableCompat.wrap(top.mutate())
                DrawableCompat.setTint(wrapped, color)
                btn.setCompoundDrawablesWithIntrinsicBounds(null, wrapped, null, null)
            } else {
                btn.setTextColor(color)
            }
        }
    }

    fun changeColor() {
        var index = listOfAvailableColors.indexOf(currentColor)
        index++
        if (index >= listOfAvailableColors.size) {
            index = 0
        }
        currentColor = listOfAvailableColors[index]
        // prefer tinting an icon if present, otherwise fall back to text color
        tintExistingButtonIcon(colorButton, currentColor)
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
                strokes.removeAll(action.erasedStrokes.toSet())
            }
        }

        invalidate()
    }

    fun erase() {
        setMode(if (currentMode == ToolMode.ERASE) ToolMode.DRAW else if (currentMode == ToolMode.DRAW) ToolMode.SELECT else ToolMode.ERASE)
        eraseButton.text = currentMode.toString()
        if (currentMode == ToolMode.ERASE) {
            highlightButton(eraseButton, Color.WHITE)
        } else if (currentMode == ToolMode.SELECT) {
            highlightButton(eraseButton, Color.GREEN)
        } else {
            clearHighlight(eraseButton)
        }
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
        DRAW, ERASE, SELECT
    }

    enum class GeometryMode {
        RECTANGLE, CIRCLE, LINE
    }

    data class Stroke(
        val path: Path, val paint: Paint
    )

    sealed class Action {
        data class Draw(val stroke: Stroke) : Action()
        data class Erase(val erasedStrokes: List<Stroke>) : Action()
    }
}