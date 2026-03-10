package com.openkustom.lwp

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private val uiFile = File("/sdcard/OpenKustom/ui.json")
    private val globalsFile = File("/sdcard/OpenKustom/globals.json")
    private var layers = JSONArray()
    private var globalsObj = JSONObject()
    
    private lateinit var layersContainer: LinearLayout
    private lateinit var preview: PreviewView

    // THE LIVE PREVIEW COMPONENT
    inner class PreviewView(context: Context) : View(context) {
        private val fillPaint = Paint().apply { isAntiAlias = true }
        private val textPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }
        private val handler = Handler(Looper.getMainLooper())
        private val ticker = object : Runnable {
            override fun run() {
                invalidate() // Redraw
                handler.postDelayed(this, 16)
            }
        }

        init { handler.post(ticker) }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#0A0B10"))
            val scale = width.toFloat() / 1080f // Scale the 1080p design to fit the preview box
            canvas.scale(scale, scale)

            for (i in 0 until layers.length()) {
                val l = layers.getJSONObject(i)
                val type = l.optString("type")
                val colorStr = l.optString("color", "#FFFFFF")
                
                // Resolve Global if it exists
                val finalColor = if (globalsObj.has(colorStr)) globalsObj.getString(colorStr) else colorStr
                val colorInt = try { Color.parseColor(finalColor) } catch (e: Exception) { Color.WHITE }
                
                val x = l.optDouble("x", 0.0).toFloat()
                val y = l.optDouble("y", 0.0).toFloat()

                when (type) {
                    "rect" -> {
                        fillPaint.color = colorInt
                        canvas.drawRect(x, y, x + l.optInt("w", 100), y + l.getOptInt("h", 100), fillPaint)
                    }
                    "circle" -> {
                        fillPaint.color = colorInt
                        canvas.drawCircle(x, y, l.optInt("radius", 50).toFloat(), fillPaint)
                    }
                    "text" -> {
                        textPaint.color = colorInt
                        textPaint.textSize = l.optInt("size", 40).toFloat()
                        canvas.drawText(l.optString("content", ""), x, y, textPaint)
                    }
                }
            }
        }
        private fun JSONObject.getOptInt(key: String, def: Int) = if (has(key)) getInt(key) else def
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
        }

        // 1. THE PREVIEW BOX (Top 30% of screen)
        preview = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 0.35f)
        }
        root.addView(preview)

        // 2. THE EDITOR TABS
        val scroller = ScrollView(this).apply { 
            layoutParams = LinearLayout.LayoutParams(-1, 0, 0.65f)
            isFillViewport = true 
        }
        layersContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 200) 
        }
        scroller.addView(layersContainer)
        root.addView(scroller)

        loadData()
        refreshUI()
        setContentView(root)
    }

    private fun loadData() {
        try {
            if (uiFile.exists()) layers = JSONArray(uiFile.readText())
            if (globalsFile.exists()) globalsObj = JSONObject(globalsFile.readText())
        } catch (e: Exception) {}
    }

    private fun refreshUI() {
        layersContainer.removeAllViews()
        
        // Add Header
        val btnAdd = Button(this).apply { 
            text = "+ NEW TEXT LAYER"
            setOnClickListener { 
                layers.put(JSONObject().apply { 
                    put("type", "text"); put("content", "Preview Test")
                    put("x", 100); put("y", 200); put("size", 60); put("color", "#00E5FF")
                })
                save()
                refreshUI()
            }
        }
        layersContainer.addView(btnAdd)

        // List Layers
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20)
                setBackgroundColor(Color.parseColor("#1A1C2E"))
            }
            
            // X Slider
            card.addView(TextView(this).apply { text = "X Position"; setTextColor(Color.GRAY) })
            card.addView(SeekBar(this).apply {
                max = 1080; progress = layer.optInt("x")
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                        if (b) { layer.put("x", p); save() }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
            
            layersContainer.addView(card)
            layersContainer.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 20) })
        }
    }

    private fun save() {
        uiFile.writeText(layers.toString())
    }
}
