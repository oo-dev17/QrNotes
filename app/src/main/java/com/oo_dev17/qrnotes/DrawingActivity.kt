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
        val btnRed = findViewById<Button>(R.id.btnRed)
        val btnBlue = findViewById<Button>(R.id.btnBlue)
        val btnUndo = findViewById<Button>(R.id.btnUndo)
        val btnRedo = findViewById<Button>(R.id.btnRedo)
        val btnErase = findViewById<Button>(R.id.btnErase)

        drawingView.attachButton(btnErase)

        btnClear.setOnClickListener {
            drawingView.clearCanvas()
        }

        btnRed.setOnClickListener {
            drawingView.setColor(Color.RED)
        }

        btnBlue.setOnClickListener {
            drawingView.setColor(Color.BLUE)
        }
        btnUndo.setOnClickListener {
            drawingView.undo()
        }
        btnRedo.setOnClickListener {
            drawingView.redo()
        }
        btnRedo.setOnClickListener {
            drawingView.erase()
        }
        btnErase.setOnClickListener{
            drawingView.erase()
        }
    }

}