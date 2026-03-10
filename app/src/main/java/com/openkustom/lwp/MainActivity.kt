package com.openkustom.lwp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
        
        val scroller = ScrollView(this).apply { 
            setBackgroundColor(Color.parseColor("#0A0B10")) 
            isFillViewport = true
        }
        
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        scroller.addView(container)

        val btnAdd = Button(this).apply {
            text = "+ ADD NEW TEXT LAYER"
            setOnClickListener {
                val newObj = JSONObject().apply {
                    put("type", "text")
                    put("content", "New Layer")
                    put("x", 100)
                    put("y", 600)
                    put("size", 60)
                    put("color", "#00E5FF")
                }
                layers.put(newObj)
                saveAndRefresh()
            }
        }
        
        container.addView(btnAdd)
        loadJson()
        refreshList()
        setContentView(scroller)
    }

    private fun loadJson() {
        try {
            if (jsonFile.exists()) {
                layers = JSONArray(jsonFile.readText())
            } else {
                jsonFile.parentFile?.mkdirs()
                jsonFile.writeText("[]")
            }
        } catch (e: Exception) {
            layers = JSONArray()
        }
    }

    private fun saveAndRefresh() {
        jsonFile.writeText(layers.toString(2))
        refreshList()
    }

    private fun refreshList() {
        // Keep the "Add" button at index 0, clear the rest
        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 30, 30, 30)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 20, 0, 20)
                layoutParams = params
                setBackgroundColor(Color.parseColor("#1A1C2E"))
            }

            val title = TextView(this).apply { 
                text = "Layer $i: ${layer.optString("type")}"
                setTextColor(Color.WHITE)
                textSize = 18f
            }
            
            val xLabel = TextView(this).apply { 
                text = "X Position: ${layer.getInt("x")}"
                setTextColor(Color.CYAN)
            }
            
            val xSlider = SeekBar(this).apply {
                max = 1080
                progress = layer.getInt("x")
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                        if (b) {
                            layer.put("x", p)
                            xLabel.text = "X Position: $p"
                            // Save silently while sliding for real-time feel
                            jsonFile.writeText(layers.toString())
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }

            card.addView(title)
            card.addView(xLabel)
            card.addView(xSlider)
            container.addView(card)
        }
    }
}
// Build Trigger: Tue Mar 10 16:26:15 CDT 2026
