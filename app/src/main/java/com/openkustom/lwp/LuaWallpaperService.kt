package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.WindowInsets
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class LuaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LuaEngine()

    inner class LuaEngine : Engine() {
        private val globals = JsePlatform.standardGlobals()
        private val handler = Handler(Looper.getMainLooper())
        private val startTime = System.currentTimeMillis()
        private var isVisible = false
        
        // Drawing Tools
        private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val textPaint = Paint().apply { isAntiAlias = true }

        // THE ANIMATION LOOP (60 FPS ~ 16ms)
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
            if (visible) {
                handler.post(tick)
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            globals.set("top_inset", LuaValue.valueOf(systemBars.top.toDouble()))
        }

        override fun onTouchEvent(event: MotionEvent) {
            globals.set("touch_x", LuaValue.valueOf(event.x.toDouble()))
            globals.set("touch_y", LuaValue.valueOf(event.y.toDouble()))
            when (event.action) {
                MotionEvent.ACTION_DOWN -> globals.set("is_touching", LuaValue.valueOf(true))
                MotionEvent.ACTION_UP -> globals.set("is_touching", LuaValue.valueOf(false))
            }
        }

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                // 1. CLEAR AND UPDATE TIME
                canvas.drawColor(Color.parseColor("#0A0A0A"))
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                globals.set("time", LuaValue.valueOf(elapsed))
                
                // 2. REFRESH ENVIRONMENT
                globals.set("width", LuaValue.valueOf(canvas.width.toDouble()))
                globals.set("height", LuaValue.valueOf(canvas.height.toDouble()))
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                globals.set("battery", LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))

                // 3. DRAWING API (Simplified for brevity, keep your existing rect/text/circle here)
                val draw = LuaValue.tableOf()
                draw.set("circle", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        fillPaint.color = Color.parseColor(args.checkjstring(4))
                        canvas.drawCircle(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(),
                            args.checkdouble(3).toFloat(), fillPaint)
                        return LuaValue.NONE
                    }
                })
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

                // 4. EXECUTE LUA
                val file = File("/sdcard/OpenKustom/logic.lua")
                if (file.exists()) {
                    globals.load(file.readText()).call()
                }

            } catch (e: Exception) {
                textPaint.color = Color.YELLOW
                canvas.drawText("ERR: ${e.message}", 50f, 400f, textPaint)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(tick)
        }
    }
}
