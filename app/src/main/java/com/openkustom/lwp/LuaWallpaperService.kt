package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
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
        private var topInset = 0
        private var bottomInset = 0

        init {
            // Initialize basic touch globals
            globals.set("touch_x", LuaValue.valueOf(0.0))
            globals.set("touch_y", LuaValue.valueOf(0.0))
            globals.set("is_touching", LuaValue.valueOf(false))
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = systemBars.top
            bottomInset = systemBars.bottom
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
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                // Path to your script in Termux/Storage
                val scriptFile = File("/sdcard/OpenKustom/logic.lua")
                
                // Fallback script if file is missing
                val luaCode = if (scriptFile.exists()) {
                    scriptFile.readText()
                } else {
                    "return '#121212'"
                }

                // Inject current battery level into Lua
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                globals.set("battery", LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))

                // Execute Lua and get the background color
                val result = globals.load(luaCode).call()
                val hexColor = result.tojstring()
                
                canvas.drawColor(Color.parseColor(hexColor))

                // Optional: Draw a circle where you touch
                if (globals.get("is_touching").toboolean()) {
                    val paint = Paint().apply { color = Color.WHITE }
                    canvas.drawCircle(
                        globals.get("touch_x").tofloat(), 
                        globals.get("touch_y").tofloat(), 
                        80f, 
                        paint
                    )
                }

            } catch (e: Exception) {
                canvas.drawColor(Color.DKGRAY) // Indicate error
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) drawFrame()
        }
    }
}
