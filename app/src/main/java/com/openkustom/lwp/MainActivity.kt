package com.openkustom.lwp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private val jsonFile = File("/sdcard/OpenKustom/ui.json")
    private var layers = JSONArray()
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0B10")) }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        root.addView(container)

        val btnAdd = Button(this).apply {
            text = "+ ADD NEW TEXT LAYER"
            setOnClickListener {
                val newObj = JSONObject().apply {
                    put("type", "text"); put("content", "New Layer")
                    put("x", 100); put("y", 600); put("size", 60); put("color", "#00E5FF")
                }
                layers.put(newObj)
                saveAndRefresh()
            }
        }
        
        container.addView(btnAdd)
        loadJson()
        refreshList()
        setContentView(root)
    }

    private fun loadJson() {
        if (jsonFile.exists()) layers = JSONArray(jsonFile.readText())
        else jsonFile.writeText("[]")
    }

    private fun saveAndRefresh() {
        jsonFile.writeText(layers.toString(2))
        refreshList()
    }

    private fun refreshList() {
        // Keep the "Add" button, clear rest
        while (container.childCount > 1) container.removeViewAt(1)

        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                setBackgroundColor(Color.parseColor("#1A1C2E"))
            }

            val title = TextView(this).apply { 
                text = "Layer $i: ${layer.optString("type")}"
                setTextColor(Color.WHITE)
            }
            
            val xLabel = TextView(this).apply { text = "X: ${layer.getInt("x")}"; setTextColor(Color.GRAY) }
            val xSlider = SeekBar(this).apply {
                max = 1080
                progress = layer.getInt("x")
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                        if (b) {
                            layer.put("x", p)
                            xLabel.text = "X: $p"
                            jsonFile.writeText(layers.toString()) // Fast save
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            card.addView(title); card.addView(xLabel); card.addView(xSlider)
            container.addView(Space(this, 30))
            container.addView(card)
        }
    }

    private fun Space(context: Activity, h: Int) = View(context).apply { 
        layoutParams = LinearLayout.LayoutParams(1, h) 
    }
}
