package com.openkustom.lwp

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class LuaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LuaEngine()

    inner class LuaEngine : Engine() {
        private val globals = JsePlatform.standardGlobals()
        private val handler = Handler(Looper.getMainLooper())
        private val startTime = System.currentTimeMillis()
        private var isVisible = false
        
        private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val textPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.MONOSPACE }

        private val tick = Runnable {
            if (isVisible) {
                drawFrame()
                handler.postDelayed({ drawFrame() }, 16) // ~60fps
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            isVisible = v
            if (v) handler.post(tick) else handler.removeCallbacksAndMessages(null)
        }

        private fun jsonToLuaTable(json: Any): LuaValue {
            return when (json) {
                is JSONArray -> {
                    val table = LuaTable()
                    for (i in 0 until json.length()) {
                        table.set(i + 1, jsonToLuaTable(json.get(i)))
                    }
                    table
                }
                is JSONObject -> {
                    val table = LuaTable()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        table.set(key, jsonToLuaTable(json.get(key)))
                    }
                    table
                }
                is Int -> LuaValue.valueOf(json)
                is Double -> LuaValue.valueOf(json)
                is Boolean -> LuaValue.valueOf(json)
                else -> LuaValue.valueOf(json.toString())
            }
        }

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.parseColor("#0A0B10"))
                
                // 1. Inject System Globals
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                globals.set("time", LuaValue.valueOf(elapsed))
                globals.set("si_width", LuaValue.valueOf(canvas.width.toDouble()))
                globals.set("si_height", LuaValue.valueOf(canvas.height.toDouble()))

                // 2. Inject UI Data (Layers)
                val uiFile = File("/sdcard/OpenKustom/ui.json")
                if (uiFile.exists()) {
                    globals.set("ui_data", jsonToLuaTable(JSONArray(uiFile.readText())))
                } else {
                    globals.set("ui_data", LuaTable())
                }

                // 3. Inject Global Variables (Theme)
                val gvFile = File("/sdcard/OpenKustom/globals.json")
                if (gvFile.exists()) {
                    globals.set("gv_data", jsonToLuaTable(JSONObject(gvFile.readText())))
                } else {
                    globals.set("gv_data", LuaTable())
                }

                // 4. Run Lua Logic
                val script = File("/sdcard/OpenKustom/logic.lua")
                if (script.exists()) {
                    val result = globals.load(script.readText()).call()
                    if (result.istable()) {
                        val renderList = result.checktable()
                        for (i in 1..renderList.length()) {
                            renderLayer(canvas, renderList.get(i))
                        }
                    }
                }
            } catch (e: Exception) {
                textPaint.color = Color.RED
                textPaint.textSize = 40f
                canvas.drawText("ERR: ${e.message}", 50f, 200f, textPaint)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun renderLayer(canvas: Canvas, l: LuaValue) {
            val type = l.get("type").optjstring("none")
            val colorStr = l.get("color").optjstring("#FFFFFF")
            val colorInt = try { Color.parseColor(colorStr) } catch (e: Exception) { Color.WHITE }
            
            val x = l.get("x").tofloat()
            val y = l.get("y").tofloat()

            when (type) {
                "rect" -> {
                    fillPaint.color = colorInt
                    canvas.drawRect(x, y, x + l.get("w").tofloat(), y + l.get("h").tofloat(), fillPaint)
                }
                "circle" -> {
                    fillPaint.color = colorInt
                    canvas.drawCircle(x, y, l.get("radius").tofloat(), fillPaint)
                }
                "text" -> {
                    textPaint.color = colorInt
                    textPaint.textSize = l.get("size").tofloat()
                    canvas.drawText(l.get("content").optjstring(""), x, y, textPaint)
                }
            }
        }
    }
}
