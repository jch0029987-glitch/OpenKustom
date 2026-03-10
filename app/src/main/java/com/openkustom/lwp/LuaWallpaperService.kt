package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowInsets
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

class LuaWallpaperService : WallpaperService() {
    // New Android 16 API allows identifying specific instances (Home vs Lock)
    override fun onCreateEngine(): Engine = LuaEngine()

    inner class LuaEngine : Engine() {
        private val globals = JsePlatform.standardGlobals()
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunnable = Runnable { drawFrame() }
        private var isVisible = false
        
        // Edge-to-Edge Insets
        private var topInset = 0
        private var bottomInset = 0

        init {
            setupLuaGlobals()
        }

        private fun setupLuaGlobals() {
            val androidLib = org.luaj.vm2.LuaTable()
            
            // Example: $bi(level)$ equivalent
            androidLib.set("battery", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                    return LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                }
            })

            globals.set("android", androidLib)
            // Initializing touch state
            globals.set("is_touching", LuaValue.valueOf(false))
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = systemBars.top
            bottomInset = systemBars.bottom
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisible = visible
            if (visible) drawFrame() else handler.removeCallbacks(drawRunnable)
        }

        override fun onTouchEvent(event: MotionEvent) {
            globals.set("touch_x", LuaValue.valueOf(event.x.toDouble()))
            globals.set("touch_y", LuaValue.valueOf(event.y.toDouble()))
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> globals.set("is_touching", LuaValue.valueOf(true))
                MotionEvent.ACTION_UP -> globals.set("is_touching", LuaValue.valueOf(false))
            }
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            
            try {
                // RUN LUA SCRIPT (Mocking a script that returns a color)
                val luaScript = "if is_touching then return '#FF5722' else return '#121212' end"
                val hexColor = globals.load(luaScript).call().tojstring()
                
                // DRAWING
                canvas.drawColor(Color.parseColor(hexColor))
                
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 64f
                    isAntiAlias = true
                }

                // Respecting Android 16 Insets
                canvas.drawText("OpenKustom", 100f, topInset + 100f, paint)
                
                if (globals.get("is_touching").toboolean()) {
                    val tx = globals.get("touch_x").tofloat()
                    val ty = globals.get("touch_y").tofloat()
                    canvas.drawCircle(tx, ty, 80f, paint)
                }

            } catch (e: Exception) {
                val errorPaint = Paint().apply { color = Color.RED; textSize = 40f }
                canvas.drawText("Lua Error: ${e.message}", 50f, 200f, errorPaint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
        }
    }
}
