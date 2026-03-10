package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.WindowInsets
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
        private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE }

        private val tick = object : Runnable {
            override fun run() {
                if (isVisible) {
                    drawFrame()
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisible = visible
            if (visible) handler.post(tick) else handler.removeCallbacks(tick)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            globals.set("top_inset", LuaValue.valueOf(systemBars.top.toDouble()))
        }

        override fun onTouchEvent(event: MotionEvent) {
            globals.set("touch_x", LuaValue.valueOf(event.x.toDouble()))
            globals.set("touch_y", LuaValue.valueOf(event.y.toDouble()))
            if (event.action == MotionEvent.ACTION_DOWN) globals.set("is_touching", LuaValue.TRUE)
            if (event.action == MotionEvent.ACTION_UP) globals.set("is_touching", LuaValue.FALSE)
        }

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.parseColor("#0A0B10"))
                
                // Update dynamic globals
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                globals.set("time", LuaValue.valueOf(elapsed))
                globals.set("width", LuaValue.valueOf(canvas.width.toDouble()))
                globals.set("height", LuaValue.valueOf(canvas.height.toDouble()))
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                globals.set("battery", LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))

                val file = File("/sdcard/OpenKustom/logic.lua")
                if (file.exists()) {
                    val layersTable = globals.load(file.readText()).call()
                    
                    if (layersTable.istable()) {
                        val table = layersTable.checktable()
                        for (i in 1..table.length()) {
                            val layer = table.get(i)
                            val type = layer.get("type").optjstring("none")
                            val hex = layer.get("color").optjstring("#FFFFFF")
                            val x = layer.get("x").tofloat()
                            val y = layer.get("y").tofloat()

                            when (type) {
                                "circle" -> {
                                    fillPaint.color = Color.parseColor(hex)
                                    canvas.drawCircle(x, y, layer.get("radius").tofloat(), fillPaint)
                                }
                                "rect" -> {
                                    fillPaint.color = Color.parseColor(hex)
                                    canvas.drawRect(x, y, x + layer.get("w").tofloat(), y + layer.get("h").tofloat(), fillPaint)
                                }
                                "text" -> {
                                    textPaint.color = Color.parseColor(hex)
                                    textPaint.textSize = layer.get("size").tofloat()
                                    canvas.drawText(layer.get("content").optjstring(""), x, y, textPaint)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                textPaint.color = Color.YELLOW
                canvas.drawText("LUA ERROR: ${e.message}", 50f, 400f, textPaint)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
