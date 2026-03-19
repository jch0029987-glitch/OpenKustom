package com.openkustom.lwp

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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

    private lateinit var listContainer: LinearLayout
    private lateinit var preview: PreviewView

    // --- HIGH PERFORMANCE PREVIEW ---
    inner class PreviewView(context: Context) : View(context) {
        private val pFill = Paint().apply { isAntiAlias = true }
        private val pText = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }
        private val h = Handler(Looper.getMainLooper())
        private val ticker = object : Runnable {
            override fun run() { invalidate(); h.postDelayed(this, 16) }
        }
        init { h.post(ticker) }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#050505"))
            val scale = width.toFloat() / 1080f
            canvas.scale(scale, scale)

            for (i in 0 until layers.length()) {
                val l = layers.optJSONObject(i) ?: continue
                val type = l.optString("type")
                val rawColor = l.optString("color", "#FFFFFF")
                val finalColor = if (globals.has(rawColor)) globals.getString(rawColor) else rawColor
                val colorInt = try { Color.parseColor(finalColor) } catch (e: Exception) { Color.WHITE }

                val x = l.optDouble("x", 540.0).toFloat()
                val y = l.optDouble("y", 1200.0).toFloat()

                when (type) {
                    "rect" -> { pFill.color = colorInt; canvas.drawRect(x, y, x + l.optInt("w", 200), y + l.optInt("h", 200), pFill) }
                    "circle" -> { pFill.color = colorInt; canvas.drawCircle(x, y, l.optInt("radius", 100).toFloat(), pFill) }
                    "text" -> { pText.color = colorInt; pText.textSize = l.optInt("size", 80).toFloat(); canvas.drawText(l.optString("content", "TEXT"), x, y, pText) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Main Layout
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = PreviewView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 700) }
        main.addView(preview)

        val scroller = ScrollView(this).apply { isFillViewport = true }
        listContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 400) 
        }
        scroller.addView(listContainer)
        main.addView(scroller)
        root.addView(main)

        // FIXED FAB
        val fab = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00E5FF")) }
            elevation = 20f
            val btn = ImageView(context).apply { 
                setImageResource(android.R.drawable.ic_input_add)
                setColorFilter(Color.BLACK)
                setPadding(40, 40, 40, 40)
            }
            addView(btn)
            val p = RelativeLayout.LayoutParams(180, 180).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                setMargins(0, 0, 60, 80)
            }
            layoutParams = p
            setOnClickListener { showAddMenu() }
        }
        root.addView(fab)

        load(); refresh(); setContentView(root)
    }

    private fun showAddMenu() {
        val dialog = Dialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(60, 60, 60, 100)
            background = GradientDrawable().apply { 
                setColor(Color.parseColor("#121212")); cornerRadius = 60f 
            }
        }

        val options = listOf("TEXT" to "text", "RECTANGLE" to "rect", "CIRCLE" to "circle", "GLOBAL" to "global")
        for (opt in options) {
            val b = Button(this).apply {
                text = opt.first; setTextColor(Color.WHITE); background = null
                setOnClickListener {
                    if (opt.second == "global") addGlobal() else addLayer(opt.second)
                    refresh(); dialog.dismiss()
                }
            }
            sheet.addView(b)
        }
        dialog.setContentView(sheet)
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        
        // Render Globals first
        val gTitle = TextView(this).apply { text = "GLOBALS"; setTextColor(Color.CYAN); setPadding(0,20,0,20) }
        listContainer.addView(gTitle)
        globals.keys().forEach { k ->
            val card = createCard("G: $k")
            card.addView(createEdit(globals, k, "Value", gvFile))
            listContainer.addView(card)
        }

        // Render Layers
        val lTitle = TextView(this).apply { text = "LAYERS"; setTextColor(Color.YELLOW); setPadding(0,40,0,20) }
        listContainer.addView(lTitle)
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val card = createCard("${l.optString("type").uppercase()} #$i")
            card.addView(createEdit(l, "content", "Content", uiFile))
            card.addView(createSlider(l, "x", "X", 1080, uiFile))
            card.addView(createSlider(l, "y", "Y", 2400, uiFile))
            card.addView(createEdit(l, "color", "Color/Global", uiFile))
            
            val del = Button(this).apply { text = "DELETE"; setTextColor(Color.RED); background = null; setOnClickListener { layers.remove(i); save(); refresh() } }
            card.addView(del)
            listContainer.addView(card)
        }
    }

    private fun createCard(t: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
        background = GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")); cornerRadius = 30f }
        val p = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 30) }
        layoutParams = p
        addView(TextView(context).apply { text = t; setTextColor(Color.GRAY); textSize = 12f })
    }

    private fun createSlider(o: JSONObject, k: String, l: String, m: Int, f: File) = SeekBar(this).apply {
        max = m; progress = o.optInt(k); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { if (b) { o.put(k, p); save() } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun createEdit(o: JSONObject, k: String, h: String, f: File) = EditText(this).apply {
        hint = h; setText(o.optString(k)); setTextColor(Color.WHITE); background = null
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { o.put(k, s.toString()); save() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun addLayer(t: String) { layers.put(JSONObject().apply { put("type", t); put("x", 540); put("y", 1200); put("color", "#FFFFFF"); put("content", "New $t") }); save() }
    private fun addGlobal() { globals.put("g_${globals.length()}", "#00E5FF"); save() }
    private fun load() { try { if (uiFile.exists()) layers = JSONArray(uiFile.readText()); if (gvFile.exists()) globals = JSONObject(gvFile.readText()) } catch (e: Exception) {} }
    private fun save() { uiFile.writeText(layers.toString()); gvFile.writeText(globals.toString()) }
    private fun checkPermissions() { if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
}
