package com.openkustom.lwp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
        
        // Android 11+ Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        val scroller = ScrollView(this).apply { 
            setBackgroundColor(Color.parseColor("#0A0B10")) 
            isFillViewport = true
        }
        
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        scroller.addView(container)

        // Toolbar
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(createToolButton("+ TEXT", "#00E5FF") { addLayer("text") })
        toolbar.addView(createToolButton("+ RECT", "#FF4081") { addLayer("rect") })
        toolbar.addView(createToolButton("+ CIRC", "#7C4DFF") { addLayer("circle") })
        container.addView(toolbar)

        loadJson()
        refreshList()
        setContentView(scroller)
    }

    private fun createToolButton(txt: String, clr: String, action: () -> Unit) = Button(this).apply {
        text = txt; setBackgroundColor(Color.parseColor(clr)); setTextColor(Color.BLACK)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        setOnClickListener { action() }
    }

    private fun addLayer(type: String) {
        val obj = JSONObject().apply {
            put("type", type); put("x", 200); put("y", 400)
            put("w", 200); put("h", 200); put("radius", 100); put("size", 60)
            put("color", "#FFFFFF"); put("content", "New $type")
        }
        layers.put(obj); saveAndRefresh()
    }

    private fun loadJson() {
        try {
            if (jsonFile.exists()) layers = JSONArray(jsonFile.readText())
            else { jsonFile.parentFile?.mkdirs(); jsonFile.writeText("[]") }
        } catch (e: Exception) { layers = JSONArray() }
    }

    private fun saveAndRefresh() {
        jsonFile.writeText(layers.toString(2))
        refreshList()
    }

    private fun refreshList() {
        if (container.childCount > 1) container.removeViews(1, container.childCount - 1)
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val type = layer.getString("type")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 30, 30, 30)
                val params = LinearLayout.LayoutParams(-1, -2)
                params.setMargins(0, 20, 0, 20)
                layoutParams = params
                setBackgroundColor(Color.parseColor("#1A1C2E"))
            }

            val header = LinearLayout(this).apply { 
                addView(TextView(context).apply { 
                    text = "LAYER $i: ${type.uppercase()}"; setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                addView(Button(context).apply { 
                    text = "X"; setBackgroundColor(Color.RED)
                    setOnClickListener { layers.remove(i); saveAndRefresh() }
                })
            }
            card.addView(header)

            if (type == "text") {
                card.addView(createEditField(layer, "content", "Text content"))
                card.addView(createSlider(layer, "size", "Size", 300))
            } else if (type == "rect") {
                card.addView(createSlider(layer, "w", "Width", 1080))
                card.addView(createSlider(layer, "h", "Height", 2400))
            } else if (type == "circle") {
                card.addView(createSlider(layer, "radius", "Radius", 500))
            }

            card.addView(createSlider(layer, "x", "X Position", 1080))
            card.addView(createSlider(layer, "y", "Y Position", 2400))
            card.addView(createEditField(layer, "color", "Color (Hex)"))
            container.addView(card)
        }
    }

    private fun createSlider(obj: JSONObject, key: String, label: String, maxVal: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply { text = label; setTextColor(Color.GRAY) })
        addView(SeekBar(context).apply {
            max = maxVal; progress = obj.optInt(key, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    if (b) { obj.put(key, p); jsonFile.writeText(layers.toString()) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    private fun createEditField(obj: JSONObject, key: String, hintText: String) = EditText(this).apply {
        hint = hintText; setTextColor(Color.WHITE); setText(obj.optString(key))
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { obj.put(key, s.toString()); jsonFile.writeText(layers.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}
