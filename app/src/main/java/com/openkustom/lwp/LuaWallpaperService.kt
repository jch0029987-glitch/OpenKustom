package com.openkustom.lwp

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import org.json.JSONArray
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
        private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; typeface = Typeface.MONOSPACE }

        private val tick = Runnable {
            if (isVisible) {
                drawFrame()
                handler.postDelayed({ drawFrame() }, 16)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisible = visible
            if (visible) handler.post(tick) else handler.removeCallbacksAndMessages(null)
        }

        // Converts JSON to LuaTable so the Lua script can easily manipulate it
        private fun jsonToLuaTable(jsonArray: JSONArray): LuaTable {
            val table = LuaTable()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val row = LuaTable()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val value = obj.get(key)) {
                        is Int -> row.set(key, LuaValue.valueOf(value))
                        is Double -> row.set(key, LuaValue.valueOf(value))
                        is String -> row.set(key, LuaValue.valueOf(value))
                        is Boolean -> row.set(key, LuaValue.valueOf(if (value) 1 else 0))
                    }
                }
                table.set(i + 1, row)
            }
            return table
        }

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.parseColor("#0A0B10")) // Deep Background
                
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                globals.set("time", LuaValue.valueOf(elapsed))
                globals.set("width", LuaValue.valueOf(canvas.width.toDouble()))
                globals.set("height", LuaValue.valueOf(canvas.height.toDouble()))
                
                // Load JSON Data from storage
                val jsonFile = File("/sdcard/OpenKustom/ui.json")
                if (jsonFile.exists()) {
                    val jsonArray = JSONArray(jsonFile.readText())
                    globals.set("ui_data", jsonToLuaTable(jsonArray))
                } else {
                    globals.set("ui_data", LuaTable())
                }

                // Execute logic.lua to get the final render table
                val script = File("/sdcard/OpenKustom/logic.lua")
                if (script.exists()) {
                    val result = globals.load(script.readText()).call()
                    if (result.istable()) {
                        val renderTable = result.checktable()
                        
                        for (i in 1..renderTable.length()) {
                            val layer = renderTable.get(i)
                            val type = layer.get("type").optjstring("none")
                            
                            // Safe Color Parsing
                            val colorStr = layer.get("color").optjstring("#FFFFFF")
                            val colorInt = try { Color.parseColor(colorStr) } catch(e: Exception) { Color.WHITE }
                            
                            val x = layer.get("x").tofloat()
                            val y = layer.get("y").tofloat()

                            when (type) {
                                "rect" -> {
                                    fillPaint.color = colorInt
                                    val w = layer.get("w").tofloat()
                                    val h = layer.get("h").tofloat()
                                    canvas.drawRect(x, y, x + w, y + h, fillPaint)
                                }
                                "circle" -> {
                                    fillPaint.color = colorInt
                                    val r = layer.get("radius").tofloat()
                                    canvas.drawCircle(x, y, r, fillPaint)
                                }
                                "text" -> {
                                    textPaint.color = colorInt
                                    textPaint.textSize = layer.get("size").tofloat()
                                    canvas.drawText(layer.get("content").optjstring(""), x, y, textPaint)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Visual error reporting on the wallpaper
                canvas.drawText("LUA ERR: ${e.message}", 50f, 300f, textPaint.apply { color = Color.RED; textSize = 40f })
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
