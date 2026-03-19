package com.openkustom.lwp

import android.app.Activity
import android.content.*
import android.graphics.*
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

    // --- PREVIEW (Pinned at top) ---
    inner class PreviewView(context: Context) : View(context) {
        private val pFill = Paint().apply { isAntiAlias = true }
        private val pText = Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        private val h = Handler(Looper.getMainLooper())
        private val tick = Runnable { invalidate(); h.postDelayed({ invalidate() }, 16) }
        init { h.post(tick) }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val scale = width.toFloat() / 1080f
            canvas.scale(scale, scale)

            for (i in 0 until layers.length()) {
                val l = layers.optJSONObject(i) ?: continue
                val rawColor = l.optString("color", "#FFFFFF")
                val finalColor = if (globals.has(rawColor)) globals.getString(rawColor) else rawColor
                val colorInt = try { Color.parseColor(finalColor) } catch (e: Exception) { Color.WHITE }

                val x = l.optDouble("x", 0.0).toFloat()
                val y = l.optDouble("y", 0.0).toFloat()

                when (l.optString("type")) {
                    "rect" -> { pFill.color = colorInt; canvas.drawRect(x, y, x + l.optInt("w", 100), y + l.optInt("h", 100), pFill) }
                    "circle" -> { pFill.color = colorInt; canvas.drawCircle(x, y, l.optInt("radius", 50).toFloat(), pFill) }
                    "text" -> { pText.color = colorInt; pText.textSize = l.optInt("size", 60).toFloat(); canvas.drawText(l.optString("content", ""), x, y, pText) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPerms()

        // ROOT: RelativeLayout to allow FAB positioning
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#050505")) }

        // 1. CONTENT SCROLLER
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = PreviewView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 600) }
        mainLayout.addView(preview)

        val scroller = ScrollView(this).apply { isFillViewport = true }
        listContainer = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 300) // Padding for FAB
        }
        scroller.addView(listContainer)
        mainLayout.addView(scroller)
        root.addView(mainLayout)

        // 2. THE FAB (Floating Action Button)
        val fab = Button(this).apply {
            text = "+"
            textSize = 30f
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF")) // Cyber Cyan
            }
            val p = RelativeLayout.LayoutParams(180, 180).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                setMargins(0, 0, 60, 60)
            }
            layoutParams = p
            setOnClickListener { showAddComponentMenu() }
        }
        root.addView(fab)

        load(); refresh(); setContentView(root)
    }

    private fun showAddComponentMenu() {
        val popup = PopupMenu(this, findViewById(android.R.id.content), Gravity.BOTTOM)
        popup.menu.add("Add Text")
        popup.menu.add("Add Rectangle")
        popup.menu.add("Add Circle")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Add Text" -> addLayer("text")
                "Add Rectangle" -> addLayer("rect")
                "Add Circle" -> addLayer("circle")
            }
            refresh(); true
        }
        popup.show()
    }

    private fun createMaterialCard(title: String, type: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
            val p = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 40) }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121212"))
                cornerRadius = 35f
                setStroke(3, Color.parseColor("#252525"))
            }

            // Header Row
            val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            header.addView(TextView(context).apply { 
                text = title; setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.DEFAULT_BOLD 
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            header.addView(TextView(context).apply { 
                text = type.uppercase(); setTextColor(Color.parseColor("#00E5FF"))
                textSize = 10f; setPadding(15, 5, 15, 5)
                background = GradientDrawable().apply { cornerRadius = 10f; setStroke(2, Color.parseColor("#00E5FF")) }
            })
            addView(header)
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 30) }) // Spacer
        }
    }

    private fun refresh() {
        listContainer.removeAllViews()
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val card = createMaterialCard("Component $i", l.optString("type"))
            
            // Minimalist Inputs
            card.addView(createEdit(l, "content", "Content", uiFile))
            card.addView(createSlider(l, "x", "X Offset", 1080, uiFile))
            card.addView(createSlider(l, "y", "Y Offset", 2400, uiFile))
            
            val delBtn = Button(this).apply { 
                text = "Remove Component"; setTextColor(Color.parseColor("#FF5252"))
                background = null
                setOnClickListener { layers.remove(i); save(); refresh() }
            }
            card.addView(delBtn)
            listContainer.addView(card)
        }
    }

    // --- LOGIC HELPERS ---
    private fun load() {
        try { if (uiFile.exists()) layers = JSONArray(uiFile.readText()); if (gvFile.exists()) globals = JSONObject(gvFile.readText()) } catch (e: Exception) {}
    }
    private fun save() { uiFile.writeText(layers.toString()); gvFile.writeText(globals.toString()) }
    private fun addLayer(t: String) { layers.put(JSONObject().apply { put("type", t); put("x", 100); put("y", 500); put("w", 200); put("h", 200); put("size", 60); put("color", "#FFFFFF"); put("content", "New $t") }); save() }
    private fun createSlider(o: JSONObject, k: String, l: String, m: Int, f: File) = SeekBar(this).apply { max = m; progress = o.optInt(k); setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { if(b){ o.put(k, p); f.writeText(layers.toString()) } } override fun onStartTrackingTouch(s: SeekBar?){} override fun onStopTrackingTouch(s: SeekBar?){} }) }
    private fun createEdit(o: JSONObject, k: String, h: String, f: File) = EditText(this).apply { hint = h; setText(o.optString(k)); setTextColor(Color.WHITE); background = null; setHintTextColor(Color.GRAY); addTextChangedListener(object: TextWatcher { override fun afterTextChanged(s: Editable?) { o.put(k, s.toString()); f.writeText(layers.toString()) } override fun beforeTextChanged(s: CharSequence?,st:Int,c:Int,a:Int){} override fun onTextChanged(s: CharSequence?,st:Int,b:Int,c:Int){} }) }
    private fun checkPerms() { if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
}
