package com.openkustom.lwp

import android.app.Activity
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private val uiFile = File("/sdcard/OpenKustom/ui.json")
    private val gvFile = File("/sdcard/OpenKustom/globals.json")
    private var layers = JSONArray()
    private var globals = JSONObject()

    private lateinit var tabLayers: LinearLayout
    private lateinit var tabGlobals: LinearLayout
    private lateinit var flipper: ViewFlipper

    inner class PreviewView(context: Context) : View(context) {
        private val pFill = Paint().apply { isAntiAlias = true }
        private val pText = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }
        private val h = Handler(Looper.getMainLooper())
        private val tick = object : Runnable {
            override fun run() { invalidate(); h.postDelayed(this, 16) }
        }
        init { h.post(tick) }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#0A0B10"))
            val scale = width.toFloat() / 1080f
            canvas.scale(scale, scale)

            for (i in 0 until layers.length()) {
                val l = layers.optJSONObject(i) ?: continue
                val type = l.optString("type")
                val rawColor = l.optString("color", "#FFFFFF")
                val finalColor = if (globals.has(rawColor)) globals.getString(rawColor) else rawColor
                val colorInt = try { Color.parseColor(finalColor) } catch (e: Exception) { Color.WHITE }

                val x = l.optDouble("x", 0.0).toFloat()
                val y = l.optDouble("y", 0.0).toFloat()

                when (type) {
                    "rect" -> { pFill.color = colorInt; canvas.drawRect(x, y, x + l.optInt("w", 100), y + l.optInt("h", 100), pFill) }
                    "circle" -> { pFill.color = colorInt; canvas.drawCircle(x, y, l.optInt("radius", 50).toFloat(), pFill) }
                    "text" -> { pText.color = colorInt; pText.textSize = l.optInt("size", 60).toFloat(); canvas.drawText(l.optString("content", ""), x, y, pText) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Permission Check
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(i)
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        
        // 1. Preview
        root.addView(PreviewView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 500) })

        // 2. Tabs
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val b1 = Button(this).apply { text = "LAYERS"; setOnClickListener { flipper.displayedChild = 0 } }
        val b2 = Button(this).apply { text = "GLOBALS"; setOnClickListener { flipper.displayedChild = 1 } }
        nav.addView(b1, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(b2, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)

        // 3. Flipper + Containers
        flipper = ViewFlipper(this)
        
        // Create Layer Tab
        val scrollL = ScrollView(this).apply { isFillViewport = true }
        tabLayers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30,30,30,200) }
        scrollL.addView(tabLayers)
        
        // Create Globals Tab
        val scrollG = ScrollView(this).apply { isFillViewport = true }
        tabGlobals = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30,30,30,200) }
        scrollG.addView(tabGlobals)

        flipper.addView(scrollL)
        flipper.addView(scrollG)
        root.addView(flipper)

        load(); refresh(); setContentView(root)
    }

    private fun load() {
        try {
            if (!uiFile.parentFile.exists()) uiFile.parentFile.mkdirs()
            if (uiFile.exists()) layers = JSONArray(uiFile.readText())
            if (gvFile.exists()) globals = JSONObject(gvFile.readText())
        } catch (e: Exception) {}
    }

    private fun save() {
        uiFile.writeText(layers.toString())
        gvFile.writeText(globals.toString())
    }

    private fun refresh() {
        tabLayers.removeAllViews(); tabGlobals.removeAllViews()

        // Layer Controls
        val tools = LinearLayout(this)
        tools.addView(Button(this).apply { text = "+ TEXT"; setOnClickListener { addL("text"); refresh() } })
        tools.addView(Button(this).apply { text = "+ RECT"; setOnClickListener { addL("rect"); refresh() } })
        tabLayers.addView(tools)

        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val card = createCard("LAYER $i: ${l.optString("type")}")
            card.addView(createEdit(l, "content", "Content", uiFile))
            card.addView(createSlider(l, "x", "X", 1080, uiFile))
            card.addView(createSlider(l, "y", "Y", 2400, uiFile))
            card.addView(createEdit(l, "color", "Color/Global", uiFile))
            card.addView(Button(this).apply { text = "DEL"; setTextColor(Color.RED); setOnClickListener { layers.remove(i); save(); refresh() } })
            tabLayers.addView(card)
        }

        // Global Controls
        tabGlobals.addView(Button(this).apply { text = "+ NEW GLOBAL"; setOnClickListener { globals.put("g_${globals.length()}", "#FFFFFF"); save(); refresh() } })
        val keys = globals.keys()
        while(keys.hasNext()) {
            val k = keys.next()
            val card = createCard("GLOBAL: $k")
            card.addView(createEdit(globals, k, "Value", gvFile))
            tabGlobals.addView(card)
        }
    }

    private fun addL(t: String) { 
        layers.put(JSONObject().apply { 
            put("type", t); put("x", 100); put("y", 400); put("w", 200); put("h", 200); put("size", 60); put("color", "#FFFFFF"); put("content", "Hello") 
        }); save() 
    }

    private fun createCard(t: String) = LinearLayout(this).apply { 
        orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20); setBackgroundColor(Color.parseColor("#1A1C2E"))
        val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 10, 0, 10); layoutParams = p
        addView(TextView(context).apply { text = t; setTextColor(Color.YELLOW) })
    }

    private fun createSlider(o: JSONObject, k: String, l: String, m: Int, f: File) = SeekBar(this).apply { 
        max = m; progress = o.optInt(k)
        setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { 
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { if(b){ o.put(k, p); f.writeText(if(f==uiFile) layers.toString() else globals.toString()) } }
            override fun onStartTrackingTouch(s: SeekBar?){} override fun onStopTrackingTouch(s: SeekBar?){} 
        }) 
    }

    private fun createEdit(o: JSONObject, k: String, h: String, f: File) = EditText(this).apply { 
        hint = h; setText(o.optString(k)); setTextColor(Color.WHITE)
        addTextChangedListener(object: TextWatcher { 
            override fun afterTextChanged(s: Editable?) { o.put(k, s.toString()); f.writeText(if(f==uiFile) layers.toString() else globals.toString()) }
            override fun beforeTextChanged(s: CharSequence?,st:Int,c:Int,a:Int){} override fun onTextChanged(s: CharSequence?,st:Int,b:Int,c:Int){} 
        }) 
    }
}
