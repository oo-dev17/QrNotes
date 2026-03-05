package com.oo_dev17.qrnotes

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DrawingActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        val drawingView = findViewById<DrawingView>(R.id.drawingView)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnColor = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnColor)
        val btnGeometry = findViewById<Button>(R.id.btnGeometry)
        val btnUndo = findViewById<Button>(R.id.btnUndo)
        val btnRedo = findViewById<Button>(R.id.btnRedo)
        val btnToolMode = findViewById<Button>(R.id.btnToolMode)

        drawingView.attachEraseButton(btnToolMode)
        drawingView.attachColorButton(btnColor)
        drawingView.attachRectangleButton(btnGeometry)

        btnClear.setOnClickListener {
            drawingView.clearCanvas()
        }

        btnColor.setOnClickListener {
            drawingView.changeColor()
        }

        btnGeometry.setOnClickListener {
            drawingView.geometryMode()
        }
        btnUndo.setOnClickListener {
            drawingView.undo()
        }
        btnRedo.setOnClickListener {
            drawingView.redo()
        }
        btnToolMode.setOnClickListener{
            drawingView.switchToolMode()
        }
    }

}