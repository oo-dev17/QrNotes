package com.oo_dev17.qrnotes

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DrawingActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        val drawingView = findViewById<DrawingView>(R.id.drawingView)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnColor = findViewById<Button>(R.id.btnColor)
        val btnRectangle = findViewById<Button>(R.id.btnRectangle)
        val btnUndo = findViewById<Button>(R.id.btnUndo)
        val btnRedo = findViewById<Button>(R.id.btnRedo)
        val btnErase = findViewById<Button>(R.id.btnErase)

        drawingView.attachEraseButton(btnErase)
        drawingView.attachColorButton(btnColor)
        drawingView.attachRectangleButton(btnRectangle)

        btnClear.setOnClickListener {
            drawingView.clearCanvas()
        }

        btnColor.setOnClickListener {
            drawingView.changeColor()
        }

        btnRectangle.setOnClickListener {
            drawingView.rectangleMode()
        }
        btnUndo.setOnClickListener {
            drawingView.undo()
        }
        btnRedo.setOnClickListener {
            drawingView.redo()
        }
        btnErase.setOnClickListener{
            drawingView.erase()
        }
    }

}