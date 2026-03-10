package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.WindowInsets
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class LuaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LuaEngine()

    inner class LuaEngine : Engine() {
        private val globals = JsePlatform.standardGlobals()
        private var topInset = 0
        private var bottomInset = 0
        
        // Reusable Paint objects for performance
        private val fillPaint = Paint().apply { isAntiAlias = true }
        private val textPaint = Paint().apply { isAntiAlias = true }

        init {
            setupLuaGlobals()
        }

        private fun setupLuaGlobals() {
            // Coordinate Globals
            globals.set("touch_x", LuaValue.valueOf(0.0))
            globals.set("touch_y", LuaValue.valueOf(0.0))
            globals.set("is_touching", LuaValue.valueOf(false))
            globals.set("width", LuaValue.valueOf(0.0))
            globals.set("height", LuaValue.valueOf(0.0))
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = systemBars.top
            bottomInset = systemBars.bottom
            globals.set("top_inset", LuaValue.valueOf(topInset.toDouble()))
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
            
            // Update Canvas Dimensions for Lua
            globals.set("width", LuaValue.valueOf(canvas.width.toDouble()))
            globals.set("height", LuaValue.valueOf(canvas.height.toDouble()))
            
            // Inject Battery
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            globals.set("battery", LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))

            try {
                // EXPOSE DRAWING TOOLS TO LUA
                val draw = LuaValue.tableOf()
                
                // draw.rect(x, y, w, h, color_hex)
                draw.set("rect", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        fillPaint.color = Color.parseColor(args.checkjstring(5))
                        canvas.drawRect(
                            args.checkdouble(1).toFloat(),
                            args.checkdouble(2).toFloat(),
                            (args.checkdouble(1) + args.checkdouble(3)).toFloat(),
                            (args.checkdouble(2) + args.checkdouble(4)).toFloat(),
                            fillPaint
                        )
                        return LuaValue.NONE
                    }
                })

                // draw.text(string, x, y, size, color_hex)
                draw.set("text", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        textPaint.apply {
                            textSize = args.optdouble(4, 40.0).toFloat()
                            color = Color.parseColor(args.optjstring(5, "#FFFFFF"))
                        }
                        canvas.drawText(args.checkjstring(1), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), textPaint)
                        return LuaValue.NONE
                    }
                })

                globals.set("draw", draw)

                // LOAD SCRIPT
                val scriptFile = File("/sdcard/OpenKustom/logic.lua")
                val luaCode = if (scriptFile.exists()) scriptFile.readText() else "return '#121212'"
                
                // Execute script and use return value as background color
                val bgColor = globals.load(luaCode).call().tojstring()
                canvas.drawColor(Color.parseColor(bgColor))

            } catch (e: Exception) {
                canvas.drawColor(Color.rgb(50, 0, 0)) // Error Red
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) { if (visible) drawFrame() }
    }
}
